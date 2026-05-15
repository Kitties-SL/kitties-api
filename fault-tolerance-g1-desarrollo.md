# Desarrollo de tolerancia a fallos — iteración G1

**Rama:** `feat/fault-tolerance-g1`  
**Fecha:** 2026-05-14  
**Alcance:** gaps G1–G6 del [informe de análisis previo](fault-tolerance-report-2026-05-14.md)

---

## Resumen

18 commits, 33 ficheros modificados, +370 líneas. Se implementaron cuatro patrones de resiliencia
(Circuit Breaker, Retry, Timeout HTTP/gRPC, Health Check) y se activaron Dead Letter Queues en los
consumers Kafka de dos servicios. El sistema pasa de no tener ningún límite de tiempo en llamadas
inter-servicio a tener una estrategia defensiva coherente en todos los puntos de integración.

---

## Patrones implementados

### G1 — Timeouts en REST clients

Todos los clientes MicroProfile REST Client tenían los defaults del framework (indefinidos).
Se añadió `connect-timeout` y `read-timeout` en `application.properties` de cada servicio:

| Servicio | Cliente | connect (ms) | read (ms) |
|---|---|---:|---:|
| adoption-service | cat-service | 2 000 | 5 000 |
| adoption-service | organization-service | 2 000 | 5 000 |
| adoption-service | user-service | 2 000 | 5 000 |
| cat-service | storage-service | 2 000 | 10 000 |
| cat-service | adoption-service | 2 000 | 5 000 |
| notification-service | user-service | 2 000 | 5 000 |
| schedule-service | user-service | 2 000 | 10 000 |
| schedule-service | auth-service | 2 000 | 10 000 |
| schedule-service | adoption-service | 2 000 | 10 000 |
| schedule-service | chat-service | 2 000 | 10 000 |

El `read-timeout` de storage-service y de los clientes del scheduler es de 10 000 ms porque
sus operaciones (subida de imágenes, purgas nightly) son inherentemente más lentas.

**Commits:** `e3ab646`, `b897e3f`, `b7320da`, `4c3b76b`, `f7c449c`

---

### G2 — Timeout en el gateway (Vert.x WebClient)

`ProxyService` usaba `WebClient` sin ningún límite de tiempo. Se añadieron dos cambios:

**`WebClientConfig.java`** — connect timeout global de 2 s:

```java
new WebClientOptions().setConnectTimeout(2000)
```

**`ProxyService.java`** — read timeout diferenciado por ruta:

```java
long timeoutMs = path.startsWith("/api/storage/upload") ? 30_000L : 5_000L;
request.timeout(timeoutMs).sendBuffer(...)
```

Si el downstream supera el tiempo límite, el proxy captura `IOException` o `TimeoutException`
y devuelve HTTP 504:

```java
.onFailure(t -> t instanceof IOException || t instanceof TimeoutException)
.recoverWithItem(t -> {
    Log.warnf("Proxy network error [%s]: %s", t.getClass().getSimpleName(), t.getMessage());
    return Response.status(504).build();
});
```

**Test** (`GatewayResourceTest`): stub WireMock con delay de 6 s sobre `/auth/login`;
el test verifica que el gateway responde 504 y el tiempo total es inferior a 7 s.

**Commits:** `e8985fc`, `f82c9ba`

---

### G3 — Timeout en gRPC (deadline por llamada)

`auth-service` llama a `user-service` via gRPC sin deadline. Un user-service lento bloqueaba
el thread indefinidamente. Se añadió deadline de 5 s directamente en `GrpcClientAuthInterceptor`:

```java
CallOptions withDeadline = callOptions.withDeadlineAfter(5, TimeUnit.SECONDS);
return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, withDeadline)) {
    @Override
    public void start(Listener<R> responseListener, Metadata headers) {
        headers.put(TOKEN_KEY, secret);
        super.start(responseListener, headers);
    }
};
```

El deadline se aplica en cada llamada, no en la configuración del canal, por lo que coexiste
correctamente con el interceptor de autenticación existente.

**Commits:** `fd10d93`, `a9f636e`

---

### G4 — Circuit Breaker en clientes REST

Cuatro clientes que llaman a servicios críticos ahora usan `@CircuitBreaker` de SmallRye
Fault Tolerance. Configuración uniforme en todos:

```java
@CircuitBreaker(
    requestVolumeThreshold = 10,
    failureRatio           = 0.5,
    delay                  = 30,
    delayUnit              = ChronoUnit.SECONDS
)
```

| Servicio | Interfaz | Método protegido |
|---|---|---|
| adoption-service | `CatClient` | `findById(Long id)` |
| adoption-service | `OrganizationClient` | `findByRegion(String region, String token)` |
| cat-service | `StorageClient` | `upload(FileUpload file)`, `delete(String key)` |
| notification-service | `UserServiceClient` | `findById(Long id, String token)` |

El circuito necesita al menos 10 llamadas para evaluar el ratio de fallos. Si la tasa de error
supera el 50 %, abre durante 30 s y luego pasa a half-open para un intento de recuperación.

**Integración con `Either`** — `AdoptionService.verifyCatActive()` captura
`CircuitBreakerOpenException` y la convierte en un `Left` en lugar de propagar la excepción:

```java
.onFailure(CircuitBreakerOpenException.class)
.recoverWithItem(__ -> {
    Log.warnf("cat-service circuit breaker OPEN — catId=%d", catId);
    return Either.<DomainError, Unit>left(new ConflictError("CAT_SERVICE_UNAVAILABLE"));
});
```

El cliente recibe HTTP 409 con código `CAT_SERVICE_UNAVAILABLE` en lugar de un error 500
genérico mientras el circuito está abierto.

**Test** (`AdoptionServiceTest`): Mockito simula `CircuitBreakerOpenException` y verifica que
el servicio devuelve `Either.Left<ConflictError>` con código `CAT_SERVICE_UNAVAILABLE`.

**Commits:** `327b5e0`, `7bcc9b6`, `b03eb99`

---

### G5 — Retry en clientes del scheduler

Los cuatro clientes REST del `schedule-service` ejecutan tareas cron nightly. Un fallo transitorio
de red hacía que la tarea se saltase completa. Se añadió `@Retry` en todos ellos:

```java
@Retry(maxRetries = 3, delay = 2, delayUnit = ChronoUnit.SECONDS)
```

Clientes afectados:

| Interfaz | Método(s) |
|---|---|
| `UserInternalClient` | `triggerErasurePurge`, `triggerActivationPurge` |
| `AdoptionInternalClient` | `triggerRetention` |
| `ChatInternalClient` | `triggerRetention` |
| `AuthInternalClient` | `triggerTokenPurge` |

Para que los tests no esperen 2 s entre reintentos, se anuló el delay en el perfil de test:

```properties
%test.Retry/delay=0
```

**Test** (`UserInternalClientRetryTest`, WireMock): dos escenarios:

1. **Éxito en el tercer intento** — los dos primeros stubs devuelven `CONNECTION_RESET_BY_PEER`;
   el tercero devuelve 200. Se verifica que se realizaron exactamente 3 llamadas HTTP.
2. **Reintentos agotados** — todos los stubs fallan. Se verifica que se lanza excepción y que
   se realizaron 4 llamadas (1 original + 3 reintentos).

**Commits:** `938790d`, `67f7f04`

---

### G6 — Health check de MinIO

`storage-service` podía arrancar con MinIO inaccesible sin ninguna señal visible al orquestador.
Se añadió `MinioHealthCheck` anotado con `@Readiness`:

```java
@Readiness
@ApplicationScoped
public class MinioHealthCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build())
              .get(5, TimeUnit.SECONDS);
            return HealthCheckResponse.up("minio");
        } catch (TimeoutException e) {
            return HealthCheckResponse.named("minio").down()
                .withData("error", "timeout after 5s").build();
        } catch (ExecutionException e) {
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return HealthCheckResponse.named("minio").down()
                .withData("error", cause).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HealthCheckResponse.named("minio").down()
                .withData("error", "interrupted").build();
        }
    }
}
```

`headBucket` es una operación ligera (no transfiere datos). El timeout de 5 s evita que el
health check bloquee el thread si MinIO no responde. El probe de readiness en
`/q/health/ready` devuelve 503 mientras MinIO no esté disponible, lo que impide que el
orquestador enrute tráfico al servicio.

**Commits:** `e3e3fbc`, `a9bb639` (fix de timeout en el mismo health check)

---

### Complemento — Dead Letter Queue en consumers Kafka

Los consumers de `notification-service` y `form-analysis-service` tragaban excepciones
silenciosamente (el mensaje se perdía sin traza). Se activó la estrategia DLQ de SmallRye
y se corrigió el manejo de errores para que los consumers propaguen los fallos:

**notification-service:**
```properties
mp.messaging.incoming.user-registered.failure-strategy=dead-letter-queue
mp.messaging.incoming.user-registered.dead-letter-queue.topic=user-registered-dlq
mp.messaging.incoming.adoption-form-analysed.failure-strategy=dead-letter-queue
mp.messaging.incoming.adoption-form-analysed.dead-letter-queue.topic=adoption-form-analysed-dlq
```

**form-analysis-service:**
```properties
mp.messaging.incoming.adoption-form-submitted.failure-strategy=dead-letter-queue
mp.messaging.incoming.adoption-form-submitted.dead-letter-queue.topic=adoption-form-submitted-dlq
```

Los mensajes que fallen tras los reintentos de Kafka van al topic `*-dlq` en lugar de
perderse, lo que permite procesarlos manualmente o con un consumer de recuperación.

**Tests:**
- `FormAnalysisServiceTest` — verifica que si la persistencia falla, no se emite evento downstream
  y la excepción se propaga (para que Kafka la enrute a DLQ).
- `UserRegisteredConsumerUnitTest` — verifica que JSON inválido y fallo de SMTP propagan
  excepción en lugar de silenciarla.

**Commits:** `9e78ddb`, `cb33968`

---

## Cobertura por servicio

| Servicio | Timeout HTTP | Circuit Breaker | Retry | Timeout gRPC | Health Check | DLQ |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| adoption-service | ✓ | ✓ | — | — | — | — |
| cat-service | ✓ | ✓ | — | — | — | — |
| notification-service | ✓ | ✓ | — | — | — | ✓ |
| schedule-service | ✓ | — | ✓ | — | — | — |
| gateway-service | ✓ | — | — | — | — | — |
| storage-service | — | — | — | — | ✓ | — |
| form-analysis-service | — | — | — | — | — | ✓ |
| auth-service | — | — | — | ✓ | — | — |

---

## Dependencias Maven añadidas

| Módulo | Dependencia | Scope |
|---|---|---|
| adoption-service | `quarkus-smallrye-fault-tolerance` | compile |
| cat-service | `quarkus-smallrye-fault-tolerance` | compile |
| notification-service | `quarkus-smallrye-fault-tolerance` | compile |
| schedule-service | `quarkus-smallrye-fault-tolerance` | compile |
| schedule-service | `quarkus-wiremock` | provided |
| schedule-service | `quarkus-wiremock-test` | test |
| gateway-service | `quarkus-wiremock` | provided |
| gateway-service | `quarkus-wiremock-test` | test |
| gateway-service | `assertj-core` 3.26.3 | test |

---

## Deuda pendiente

Los gaps G7–G9 del análisis previo (bulkhead, métricas de CB en Grafana, retry exponencial
en el scheduler) no se abordaron en esta iteración. Quedan registrados en el informe de
análisis como trabajo futuro.
