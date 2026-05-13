# Informe de tolerancia a fallos — Kitties (2026-05-14)

## Contexto

El sistema tiene fallbacks puntuales pero carece de una estrategia de resiliencia sistemática.
Con la infraestructura de microservicios actual (11 servicios, gRPC, Kafka, S3), un único
servicio lento o caído puede propagarse en cascada. Este informe analiza cada gap, su impacto,
el coste de resolverlo y propone un árbol de prioridades.

---

## Inventario de gaps

---

### G1 — Sin timeout en REST clients

**Descripción**
Los 13 clientes MicroProfile REST Client del proyecto no tienen `connect-timeout` ni
`read-timeout` configurados. Usan los defaults del framework (indefinidos o muy altos).

**Servicios afectados**
`adoption-service` (CatClient, OrganizationClient), `cat-service` (StorageClient, AdoptionClient),
`user-service` (AuthInternalClient, AdoptionInternalClient, ChatInternalClient),
`notification-service` (UserServiceClient), `schedule-service` (4 clientes internos).

**Impacto**
Si un servicio downstream tarda 30 s en responder, el thread del event loop de Vert.x queda
bloqueado. En Hibernate Reactive, esto puede impedir que se liberen conexiones del pool.
Con tráfico moderado, basta que un único servicio vaya lento para agotar todos los workers
y hacer que **el servicio llamante deje de responder por completo** — efecto cascada.

**Argumentación**
Es el gap con mayor probabilidad de producir un outage total y el más barato de resolver.
No requiere ningún cambio de código: solo añadir dos propiedades por cliente en
`application.properties`.

**Coste estimado**: 30 min (configuración pura)

**Solución**
```properties
# Por cliente en application.properties de cada servicio
quarkus.rest-client.cat-service.connect-timeout=2000
quarkus.rest-client.cat-service.read-timeout=5000
```
Valores recomendados: connect 2 s, read 5 s para llamadas internas síncronas; 10 s para
operaciones pesadas (export, análisis).

---

### G2 — Sin dead-letter queue en Kafka

**Descripción**
Los tres consumidores Kafka (`user-registered`, `adoption-form-submitted`,
`adoption-form-analysed`) manejan fallos con try-catch + log. Si el procesamiento falla,
el mensaje se descarta silenciosamente — no hay reintento ni persistencia del mensaje fallido.

**Servicios afectados**
`notification-service`, `form-analysis-service`, `adoption-service`.

**Impacto**
- Un fallo al enviar el email de activación → el usuario nunca recibe su enlace y no puede
  activar la cuenta. Sin visibilidad.
- Un fallo al procesar el formulario de adopción → el formulario desaparece; la adopción
  queda bloqueada en estado `Reviewing` indefinidamente. Sin visibilidad.
- Un fallo al procesar el resultado del análisis → la decisión de aceptar/rechazar se pierde.

**Argumentación**
Son mensajes de negocio críticos con consecuencias directas para el usuario. La pérdida
silenciosa es inaceptable en producción. Una DLQ permite reintentar o alertar manualmente.

**Coste estimado**: 2-3 h (configuración Kafka + topic DLQ + alert básico)

**Solución**
```properties
# application.properties de cada consumidor
mp.messaging.incoming.user-registered.failure-strategy=dead-letter-queue
mp.messaging.incoming.user-registered.dead-letter-queue.topic=user-registered-dlq
```
Los mensajes fallidos van a `*-dlq` topics. Una alerta de Kafka (o un consumer de monitoreo)
notifica al equipo. No requiere cambio de código Java.

---

### G3 — Sin circuit breaker en llamadas a servicios externos

**Descripción**
No hay `@CircuitBreaker` ni corte automático cuando un servicio downstream está caído.
Cada llamada se ejecuta siempre, aunque el destino lleve minutos sin responder.

**Servicios afectados principalmente**
- `adoption-service → cat-service` (verifyCatActive en cada mutación de adopción)
- `adoption-service → organization-service` (findAlternatives)
- `schedule-service → todos los servicios` (jobs diarios)

**Impacto**
Sin circuit breaker, cuando `cat-service` está caído:
1. Cada intento de `updateStatus`, `scheduleInterview`, etc. espera el timeout (G1) antes de fallar.
2. Bajo carga, los timeouts acumulados saturan el pool de conexiones.
3. El circuit breaker evitaría este gasto rechazando inmediatamente cuando el servicio está caído.

**Argumentación**
Complementario a los timeouts (G1). Los timeouts limitan el daño por petición; el circuit
breaker limita el daño acumulado cuando el destino lleva tiempo caído.

**Coste estimado**: 4-6 h (anotaciones + tests de la lógica de estado)

**Solución**
```java
@CircuitBreaker(requestVolumeThreshold = 10, failureRatio = 0.5, delay = 30, delayUnit = ChronoUnit.SECONDS)
@Fallback(fallbackMethod = "catUnavailableFallback")
private Uni<Either<DomainError, Unit>> verifyCatActive(Long catId) { ... }
```
Requiere `quarkus-smallrye-fault-tolerance` (ya presente en gateway, añadir a adoption y cat).

---

### G4 — Sin `@Retry` en operaciones idempotentes

**Descripción**
Ninguna operación tiene reintento automático ante fallos transitorios de red (conexión
rechazada, timeout breve, servicio arrancando).

**Casos idempotentes candidatos**
- `schedule-service` disparando jobs (`triggerErasurePurge`, `triggerRetention`, etc.)
- `user-service` borrando tokens de auth al procesar la baja de un usuario
- `notification-service` enviando emails (con idempotency key)

**Impacto**
Un reinicio de `user-service` durante un job del scheduler hace que el job falle permanentemente
cuando podría haberse reintentado 3 s después con éxito.

**Argumentación**
Bajo coste, alto beneficio para los jobs scheduled que son por naturaleza tolerantes a retry.
No aplicar a operaciones no idempotentes (crear entidades) sin lógica de deduplicación.

**Coste estimado**: 2 h (anotaciones en los 4 clientes del scheduler + tests)

**Solución**
```java
@Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS,
       retryOn = {ConnectException.class, TimeoutException.class})
Uni<Response> triggerErasurePurge(@HeaderParam("X-Internal-Token") String token);
```

---

### G5 — Health checks sin validación de dependencias

**Descripción**
Todos los servicios exponen `/q/health` pero solo reportan el estado del servidor JVM.
No verifican si la base de datos, Kafka o MinIO están accesibles antes de declararse `UP`.

**Impacto**
- El load balancer / orchestrator cree que el servicio está listo para recibir tráfico.
- Las primeras peticiones fallan con error de conexión a BD antes de que el pool se establezca.
- En Kubernetes, un pod puede recibir tráfico mientras su BD aún no está disponible.

**Argumentación**
El readiness check es el contrato entre el servicio y el orchestrator. Sin validar dependencias,
es una promesa que no se puede cumplir.

**Coste estimado**: 3-4 h (una clase HealthCheck por tipo de dependencia, por servicio)

**Solución**
```java
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    @Inject
    io.agroal.api.AgroalDataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        try (var conn = dataSource.getConnection()) {
            return HealthCheckResponse.up("database");
        } catch (Exception e) {
            return HealthCheckResponse.down("database");
        }
    }
}
```

---

### G6 — Sin timeout en gRPC (auth ↔ user)

**Descripción**
El canal gRPC entre `auth-service` y `user-service` no tiene deadline configurado.
Las llamadas `ValidateCredentials` y `GetUserById` pueden bloquearse indefinidamente.

**Impacto**
Si `user-service` se cuelga durante un login, `auth-service` espera sin límite.
Cada request de login que llega acumula un thread gRPC bloqueado hasta agotar el pool.

**Argumentación**
Menor probabilidad de ocurrencia que G1 (el canal gRPC tiene keep-alive), pero el impacto
es directo en la ruta de autenticación — la más crítica del sistema.

**Coste estimado**: 1 h (configuración en application.properties)

**Solución**
```properties
quarkus.grpc.clients.user-service.deadline=5s
```

---

## Árbol de prioridades

```
FASE 1 — Configuración (sin código, máximo impacto/coste)
├── G1  Timeouts REST clients         [30 min]  ← HACER PRIMERO
└── G6  Timeout gRPC                  [1 h]

FASE 2 — Kafka (datos críticos de negocio)
└── G2  Dead-letter queue             [2-3 h]

FASE 3 — Resiliencia activa
├── G4  @Retry en scheduler           [2 h]
└── G3  @CircuitBreaker               [4-6 h]

FASE 4 — Observabilidad de salud
└── G5  Health checks con dependencias [3-4 h]
```

**Razonamiento del orden:**
- G1 y G6 son configuración pura — máximo ROI, riesgo cero de regresión.
- G2 protege datos de negocio irreversibles antes de añadir complejidad de código.
- G4 antes que G3: retry es más simple y complementa a G1 sin estado adicional.
- G3 añade estado (estados del circuito) que hay que testear bien.
- G5 último porque no afecta al comportamiento en producción, solo a la observabilidad.

---

## Tests recomendados por gap

La respuesta corta es **sí, con matices**: no todos los gaps justifican el mismo nivel de
cobertura de test. El criterio es: *¿puede el test detectar una regresión antes de que lo
haga producción?* Si la respuesta es sí y el coste es razonable, el test vale la pena.

### G1 — Timeouts REST clients

**Tipo de test**: integración con WireMock (ya presente en el proyecto vía gateway-service).
**Qué testear**: verificar que cuando el stub de WireMock introduce un delay > timeout
configurado, el cliente lanza un `TimeoutException` y no bloquea indefinidamente.

```java
@QuarkusTest
@WireMockTest
class CatClientTimeoutTest {
    @Test
    void catService_timeout_failsFast() {
        stubFor(get("/cats/1").willReturn(aResponse().withFixedDelay(6000))); // > read-timeout de 5s
        var result = adoptionService.createAdoptionRequest(request, adopterId)
                .await().atMost(Duration.ofSeconds(7));
        // debe fallar con timeout, no colgar
        assertThat(result.isLeft()).isTrue();
    }
}
```

**Coste adicional**: 2-3 h. **Recomendación**: SÍ — es el gap más crítico y WireMock ya está.

---

### G2 — Dead-letter queue Kafka

**Tipo de test**: integración con SmallRye in-memory Kafka (ya usado en notification-service).
**Qué testear**: forzar que el procesamiento del mensaje lance una excepción → verificar que
el mensaje aterriza en el topic DLQ y no se pierde.

```java
@QuarkusTest
class UserRegisteredDlqTest {
    @Inject
    @Channel("user-registered-dlq") Multi<String> dlqMessages;

    @Test
    void failedMessage_goesToDlq() {
        // mock del mailer para que lance
        doThrow(new RuntimeException("SMTP down")).when(mailer).send(any());
        emitter.send("""{"email":"x@y.com","name":"Test","token":"abc"}""");
        var dlqMsg = dlqMessages.collect().first().await().atMost(Duration.ofSeconds(5));
        assertNotNull(dlqMsg); // el mensaje llegó a la DLQ
    }
}
```

**Coste adicional**: 2 h. **Recomendación**: SÍ — los mensajes perdidos son bugs silenciosos graves.

---

### G3 — Circuit breaker

**Tipo de test**: integración. Simular N fallos consecutivos y verificar que el circuito abre
(estado OPEN) y que las llamadas siguientes fallan rápido (sin esperar timeout).

**Qué NO testear con unit tests**: el comportamiento de `@CircuitBreaker` es de la
anotación CDI — Mockito lo bypassa. Requiere `@QuarkusTest` para que el interceptor actúe.

**Coste adicional**: 3-4 h (más complejo de montar). **Recomendación**: SÍ, pero solo
después de que G1 y G2 estén en producción. El circuit breaker sin timeout no tiene mucho
sentido.

---

### G4 — Retry

**Tipo de test**: integración con WireMock. Configurar stub para que falle las 2 primeras
llamadas y tenga éxito en la 3ª. Verificar que WireMock recibe exactamente 3 llamadas.

```java
stubFor(post("/internal/erasure-purge")
    .inScenario("retry").whenScenarioStateIs(STARTED)
    .willReturn(aResponse().withStatus(503))
    .willSetStateTo("first-retry"));
// ...
verify(exactly(3), postRequestedFor(urlEqualTo("/internal/erasure-purge")));
```

**Coste adicional**: 1-2 h. **Recomendación**: SÍ — es el test más sencillo de los cuatro.

---

### G5 — Health checks

**Tipo de test**: `@QuarkusTest` — verificar que `/q/health/ready` devuelve DOWN cuando
la BD no es accesible (usando Testcontainers y parando el contenedor).

**Coste adicional**: 2-3 h. **Recomendación**: OPCIONAL para la primera iteración.
El valor real llega cuando se integra con el orchestrador (Kubernetes readiness probe).

---

### G6 — Timeout gRPC

**Tipo de test**: test de integración que introduce un delay en el stub gRPC > deadline.
Relativamente complejo de montar con Quarkus gRPC test infra.

**Coste adicional**: 2 h. **Recomendación**: DIFERIR — verificar manualmente en dev
antes de añadir el overhead de un test gRPC con delay.

---

### ¿Tests de carga / estrés?

Para los gaps G1 y G3 (timeout y circuit breaker), un test de carga con
[k6](https://k6.io/) o [Gatling](https://gatling.io/) tiene valor real:
- Verificar que bajo 50 usuarios concurrentes y `cat-service` caído, `adoption-service`
  no se satura (con circuit breaker abierto, las peticiones fallan en < 1 ms).
- El proyecto ya tiene base de e2e tests; un suite de carga se puede añadir como perfil Maven
  separado (`-Pload`) que no corre en CI normalmente.

**Recomendación**: añadir 2-3 escenarios k6 básicos cuando G1 y G3 estén implementados.
No bloqueante para empezar a desplegar los fixes.

---

## Estimación total

| Fase | Esfuerzo | Cobertura |
|------|----------|-----------|
| Fase 1 | ~1.5 h | Protege contra cascada por latencia |
| Fase 2 | ~3 h | Protege mensajes Kafka críticos |
| Fase 3 | ~6-8 h | Resiliencia activa ante caídas |
| Fase 4 | ~4 h | Observabilidad real del estado |
| **Total** | **~15 h** | Sistema resiliente por capas |
