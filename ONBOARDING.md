# Guía de incorporación — Kitties Backend

Esta guía cubre los conceptos y patrones que debes entender antes de tocar código de producción. No es una lista de reglas a memorizar — es una explicación del *porqué* detrás de cada decisión. Un error en esta stack puede funcionar perfectamente en local y explotar bajo carga real sin dejar rastro obvio.

Tiempo estimado de lectura: 70 minutos.

---

## Índice

1. [El modelo de concurrencia: Vert.x y el event loop](#1-el-modelo-de-concurrencia-vertx-y-el-event-loop)
2. [Mutiny: Uni y Multi](#2-mutiny-uni-y-multi)
3. [Hibernate Reactive: sesiones y transacciones](#3-hibernate-reactive-sesiones-y-transacciones)
4. [Autenticación interna: @InternalOnly](#4-autenticación-interna-internalonly)
5. [Manejo de errores: either-mon](#5-manejo-de-errores-either-mon)
6. [GraalVM Native Image: registro de reflexión](#6-graalvm-native-image-registro-de-reflexión)
7. [Gestión de deploys: ramas deployment/](#7-gestión-de-deploys-ramas-deployment)
8. [Gotchas explicados](#8-gotchas-explicados)
9. [Checklist antes de tu primer PR](#9-checklist-antes-de-tu-primer-pr)

---

## 1. El modelo de concurrencia: Vert.x y el event loop

### El problema que resuelve

En un servidor tradicional (Spring MVC), cada request HTTP ocupa un thread del pool mientras espera una respuesta de la base de datos, de otro servicio, o de cualquier operación de I/O. Si tienes 200 requests concurrentes esperando, necesitas 200 threads activos.

Los threads son caros: cada uno consume ~1 MB de stack, y el sistema operativo paga un coste en cada cambio de contexto entre ellos. En la práctica, un servidor con este modelo se queda sin threads antes de quedarse sin CPU o memoria.

```
Modelo bloqueante — 3 requests concurrentes:

thread-1: ──── request-A ──── [espera BD: 50ms] ──── response-A ────
thread-2: ──── request-B ──── [espera BD: 50ms] ──── response-B ────
thread-3: ──── request-C ──── [espera BD: 50ms] ──── response-C ────

Los 3 threads están bloqueados durante 50ms. No pueden hacer nada más.
```

### La solución: event loop

Quarkus corre sobre **Vert.x**, que usa un número reducido de threads llamados **event loop threads** (por defecto: número de cores × 2). En lugar de bloquear un thread esperando, la operación registra un callback y devuelve el thread al pool inmediatamente. Cuando llega la respuesta, el event loop retoma el trabajo.

```
Modelo reactivo — 3 requests con 1 event loop thread:

event-loop: ─ A_inicio ─ B_inicio ─ C_inicio ─ A_callback ─ B_callback ─ C_callback ─

Las 3 requests se procesan con 1 solo thread. El thread nunca espera.
```

Con 4-8 threads de event loop, el servidor puede gestionar miles de requests concurrentes.

### La regla crítica: nunca bloquear el event loop

Si bloqueas el event loop thread (con una llamada síncrona, un `Thread.sleep`, un `await().indefinitely()`), no solo paras esa request — paras **todas las requests** que ese thread estaba gestionando.

```java
// ❌ MAL — bloquea el event loop
@GET
@Path("/cats")
public List<Cat> getCats() {
    return catRepository.listAll().await().indefinitely(); // bloquea el thread
}

// ✅ BIEN — devuelve el control al event loop inmediatamente
@GET
@Path("/cats")
public Uni<List<Cat>> getCats() {
    return catRepository.listAll(); // el thread queda libre hasta que llegue el resultado
}
```

Quarkus detecta operaciones bloqueantes en el event loop y lanza una advertencia:
```
You have attempted to perform a blocking operation on the IO thread.
```
Si ves este warning, es un bug — no lo ignores.

---

## 2. Mutiny: Uni y Multi

Mutiny es la librería reactiva de Quarkus. Reemplaza `CompletableFuture`, callbacks anidados y código bloqueante con una API legible.

### Uni\<T\> — exactamente un resultado

Representa una operación que producirá **un valor** (o un error) en el futuro. Es el equivalente reactivo de un método que devuelve `T`.

```java
Uni<Cat> cat = catRepository.findById(1L);
// El Uni no hace nada hasta que alguien se suscribe.
// Quarkus se suscribe automáticamente cuando el resource devuelve el Uni.
```

Encadenar operaciones:

```java
Uni<CatResponse> response = catRepository.findById(id)           // Uni<Cat>
        .onItem().ifNull().failWith(() -> new NotFoundException())
        .onItem().transform(cat -> catMapper.toResponse(cat));    // Uni<CatResponse>
```

### Multi\<T\> — cero o más resultados

Representa una operación que producirá **varios valores** a lo largo del tiempo. Útil para streams, exports o datasets grandes.

```java
Multi<Cat> cats = catRepository.streamAll(); // emite un Cat cada vez
```

### Cuándo usar cada uno

| Situación | Tipo |
|---|---|
| Buscar por ID, crear, actualizar, borrar | `Uni<T>` |
| Endpoint REST que devuelve lista | `Uni<PageResponse<T>>` (con paginación) |
| Export, job interno, stream real | `Multi<T>` |
| Lista pequeña y acotada por diseño | `Uni<List<T>>` es aceptable |

> **Por qué no `Uni<List<T>>` para endpoints públicos:** una lista sin paginar carga todos los registros en memoria de una vez. Con 50 registros de prueba funciona. Con 50.000 en producción, agota la memoria del servidor. Añade siempre `page` y `size` como parámetros antes de devolver colecciones.

### Operadores más usados

```java
// Transformar el valor
uni.onItem().transform(value -> ...)          // síncrono
uni.onItem().transformToUni(value -> ...)     // cuando la transformación devuelve otro Uni

// Encadenar sin usar el valor anterior
uni.chain(() -> otroUni)

// Manejar errores
uni.onFailure().recoverWithItem(fallback)
uni.onFailure().invoke(e -> Log.error("...", e))

// Convertir lista en Multi
uni.onItem().transformToMulti(list -> Multi.createFrom().iterable(list))
```

### Lo que Uni NO hace hasta que alguien se suscribe

Un `Uni` es una **descripción** de una operación, no la operación en sí. No ejecuta nada hasta que hay un suscriptor. En el contexto de Quarkus REST, el framework se suscribe automáticamente cuando el resource devuelve el `Uni`. En tests o código no-REST, necesitas suscribirte explícitamente.

```java
Uni<Cat> uni = catRepository.findById(1L);
// Hasta aquí, no se ha ejecutado ninguna query.

uni.subscribe().with(
    cat -> System.out.println(cat),
    error -> System.err.println(error)
);
// Ahora sí se ejecuta.
```

---

## 3. Hibernate Reactive: sesiones y transacciones

### Por qué Hibernate Reactive y no JDBC

JDBC es bloqueante. Una query con JDBC bloquea el thread hasta que llega la respuesta de la base de datos. En un event loop, eso bloquea todas las requests en curso. Hibernate Reactive usa el **reactive PostgreSQL client** para hacer las queries sin bloquear ningún thread.

### @WithSession y @WithTransaction

Hibernate Reactive necesita una **sesión** abierta para cualquier operación con la base de datos. La sesión es la conexión lógica con la BD dentro de la que viven las entidades gestionadas.

```java
@WithSession          // abre una sesión para esta operación (lecturas)
public Uni<Cat> findById(Long id) { ... }

@WithTransaction      // abre sesión + transacción (escrituras)
public Uni<Cat> save(Cat cat) { ... }
```

**Regla práctica:**
- Lecturas → `@WithSession` en el Service
- Escrituras → `@WithTransaction` en el Service
- Dentro de `Panache.withTransaction(() -> ...)`, no añadas `@WithSession` ni `@WithTransaction` en los métodos que llama — ya tienen sesión activa

### El gotcha de @WithSession con Multi

`@WithSession` **no funciona** en métodos que devuelven `Multi<T>`. La sesión se cierra antes de que el Multi emita todos sus elementos.

```java
// ❌ MAL — la sesión se cierra antes de que Multi emita
@WithSession
public Multi<Cat> findAll() {
    return list("status", Active).onItem()
            .transformToMulti(list -> Multi.createFrom().iterable(list));
}

// ✅ BIEN — @WithSession en el repositorio, transformación en el service
// Repository:
@WithSession
public Uni<List<Cat>> findAllActive() {
    return list("status", Active);
}

// Service:
public Multi<Cat> streamAllActive() {
    return repository.findAllActive()
            .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
}
```

### @Incoming (Kafka) + @WithTransaction = fallo

Un consumer Kafka anotado directamente con `@WithTransaction` falla porque Mutiny no puede propagar el contexto de transacción a través del canal de mensajería.

```java
// ❌ MAL
@Incoming("adoption-form-submitted")
@WithTransaction
public Uni<Void> consume(String message) { ... }

// ✅ BIEN — delegar la persistencia a un bean separado
@Incoming("adoption-form-submitted")
public Uni<Void> consume(String message) {
    return persistenceService.save(message); // el bean tiene @WithTransaction
}
```

### PanacheEntity vs PanacheRepository

Este proyecto usa el patrón **Repository**, no Active Record:

```java
// ❌ No hacer esto (Active Record — prohibido en este proyecto)
cat.persist();

// ✅ Correcto (Repository pattern)
catRepository.persist(cat);
```

Las entidades extienden `PanacheEntity` que ya provee `public Long id`. **Nunca declarar `@Id` manualmente.**

Los campos de las entidades son `public` (Panache los intercepta a nivel de bytecode):

```java
@Entity
@Table(name = "cats", schema = "cat")
public class Cat extends PanacheEntity {
    public String name;          // public — Panache intercepta el acceso
    public CatStatus status;
    public LocalDateTime createdAt;
}
```

---

## 4. Autenticación interna: @InternalOnly

### El problema

Algunos endpoints deben ser accesibles únicamente por otros servicios del sistema — nunca por usuarios finales ni por el gateway. Por ejemplo, `schedule-service` dispara jobs de retención de datos llamando a `adoption-service`, o `user-service` llama a `chat-service` durante el borrado de un usuario.

Si estos endpoints fueran accesibles públicamente, cualquiera podría borrar datos o disparar operaciones privilegiadas con una simple petición HTTP.

### Cómo funciona

El mecanismo es un `@NameBinding` de JAX-RS: una anotación que enlaza un `ContainerRequestFilter` exclusivamente a los recursos o métodos marcados con ella. El filtro valida el header `X-Internal-Token` contra el secreto compartido `kitties.internal.secret`.

```
petición entrante a /adoptions/internal/retention/run
        │
        ▼
InternalTokenFilter.filter()     ← solo se ejecuta en endpoints @InternalOnly
  lee header X-Internal-Token
  compara con kitties.internal.secret
  ✗ → 401 Unauthorized (la petición muere aquí)
  ✓ → continúa al resource
        │
        ▼
AdoptionInternalResource.runRetention()
```

### El secreto compartido

`kitties.internal.secret` tiene el mismo valor en todos los servicios:
- **Desarrollo:** `kitties-dev-secret` (por defecto, no requiere configuración)
- **Producción:** inyectado vía variable de entorno `KITTIES_INTERNAL_SECRET`

### Usar @InternalOnly en un resource

```java
@Path("/adoptions/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly          // toda la clase queda protegida
public class AdoptionInternalResource {

    @POST
    @Path("/retention/run")
    public Uni<Response> runRetention() { ... }
}
```

### Llamar a un endpoint interno desde otro servicio

```java
@RegisterRestClient(configKey = "adoption-service")
@Path("/adoptions/internal")
public interface AdoptionInternalClient {

    @POST
    @Path("/retention/run")
    Uni<Response> triggerRetention(@HeaderParam("X-Internal-Token") String token);
}

// En el servicio llamante:
@ConfigProperty(name = "kitties.internal.secret")
String internalSecret;

adoptionInternalClient.triggerRetention(internalSecret);
```

### La regla más importante

**Los endpoints `@InternalOnly` nunca se exponen por el gateway.** El gateway (puerto 8080) no debe hacer proxy de rutas `/*/internal/*`. Estos endpoints son accesibles únicamente desde la red privada de contenedores.

Si añades un endpoint interno nuevo, verifica que no está en la lista de rutas del gateway.

> Para la guía completa de cómo añadir el patrón a un nuevo servicio, ver [CLAUDE.md — Autenticación interna](CLAUDE.md#autenticación-interna-servicio-a-servicio).

---

## 5. Manejo de errores: either-mon

### Por qué no usamos excepciones para errores de dominio

Las excepciones en Java son invisibles en la firma del método. Si `catService.findById(id)` puede devolver "no encontrado", eso no aparece en ningún lugar del tipo de retorno — solo en la documentación (si existe) o en el código de quien llama (si recordó comprobarlo).

```java
// ❌ Patrón que queremos evitar: la firma miente
public Uni<CatResponse> findById(Long id) {
    // puede lanzar CatNotFoundException — pero el caller no lo sabe sin leer el código
}
```

El módulo `either-mon` hace explícitos los errores de dominio en el tipo de retorno. Si el método puede fallar por una razón de negocio conocida, el tipo lo dice.

```java
// ✅ La firma es honesta: puede devolver un error o una respuesta
public Uni<Either<DomainError, CatResponse>> findById(Long id) { ... }
```

### Either\<L, R\>

`Either<L, R>` es un `sealed interface` con dos implementaciones: `Left` (el caso de error, por convención) y `Right` (el caso de éxito).

```java
import es.kitti.mon.either.Either;
import es.kitti.mon.error.*;   // NotFoundError, ConflictError, etc.

// Construir resultados
Either.left(new NotFoundError("CAT_NOT_FOUND"))    // error
Either.right(catResponse)                          // éxito

// Inspeccionar
either.isLeft()    // ¿es un error?
either.isRight()   // ¿es un éxito?

// Extraer el valor de éxito (o un fallback)
either.getOrElse(null)

// Transformar sin salir del Either
either.map(response -> response.id())              // solo transforma el Right

// Consumir ambos lados (fold es el operador clave en los resources)
either.fold(
    error   -> Response.status(error.httpStatus()).entity(ErrorResponse.of(error)).build(),
    success -> Response.ok(success).build()
)
```

### Cuándo usar Uni\<Either\<DomainError, T\>\>

- **Usa Either** cuando el error es esperado y tiene significado de negocio: entidad no encontrada, conflicto de estado, acceso denegado, credenciales inválidas.
- **No uses Either** para errores de infraestructura (BD caída, timeout de red, bug inesperado). Esos deben propagarse como fallo del `Uni` y los captura el `GlobalExceptionMapper`.

### Tipos de DomainError disponibles

| Record | HTTP | Cuándo usarlo |
|---|---|---|
| `NotFoundError(String code)` | 404 | Entidad no encontrada |
| `ForbiddenError(String code)` | 403 | Acceso denegado por permisos de dominio |
| `ConflictError(String code)` | 409 | Conflicto de estado (duplicado, límite, adopción activa…) |
| `UnauthorizedError(String code)` | 401 | Credenciales o token inválidos |
| `BadRequestError(String code)` | 400 | Validación de contenido (p.ej. tipo MIME no soportado) |
| `ValidationError(List<FieldViolation>)` | 422 | Resultado de validación de formulario |

El campo `code` es un identificador machine-readable para que el cliente pueda mostrar el mensaje en el idioma del usuario. Convención: `ENTIDAD_MOTIVO` en mayúsculas — `CAT_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `CAT_HAS_ACTIVE_ADOPTIONS`.

### Patrón completo: service + resource

```java
// Service — devuelve Either
@WithSession
public Uni<Either<DomainError, CatResponse>> findById(Long id) {
    return catRepository.findById(id)
            .onItem().transformToUni(cat -> {
                if (cat == null)
                    return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                return imageRepository.findByCatId(id)
                        .onItem().transform(images ->
                                Either.<DomainError, CatResponse>right(catMapper.toResponse(cat, images)));
            });
}

// Resource — fold convierte Either en Response HTTP
public Uni<Response> findById(@PathParam("id") Long id) {
    return catService.findById(id)
            .onItem().transform(either -> either.fold(
                    err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                    cat -> Response.ok(cat).build()
            ));
}
```

### Tests con Either

```java
// Happy path
var result = service.findById(1L).await().indefinitely();
assertTrue(result.isRight());
assertEquals(1L, result.getOrElse(null).id());

// Error path
var result = service.findById(999L).await().indefinitely();
assertTrue(result.isLeft());
assertEquals(404, result.fold(DomainError::httpStatus, __ -> 0));
assertInstanceOf(NotFoundError.class, ((Either.Left<?, ?>) result).value());
```

### Lo que NO debes hacer

No crees clases de excepción de dominio propias (`CatNotFoundException`, `EmailAlreadyExistsException`). Esa práctica existía antes de `either-mon` y está siendo eliminada. Si ves una excepción de dominio residual, es deuda técnica.

---

## 6. GraalVM Native Image: registro de reflexión

### Por qué existe este problema

Cuando el proyecto se compila a imagen nativa con GraalVM (lo que ocurre en los despliegues de producción), el compilador analiza estáticamente el código y elimina todo lo que considera "inalcanzable" — incluyendo clases que solo se usan vía reflexión en tiempo de ejecución.

Jackson (serialización JSON), Hibernate (entidades) y el bus de eventos de Quarkus usan reflexión intensamente. Si una clase no está registrada explícitamente, la imagen nativa la elimina y el servicio falla en producción con un error como:

```
ClassNotFoundException: es.kitti.cat.dto.CatResponse
```

— un error que en desarrollo (JVM normal) nunca aparece.

### Dónde está el registro

Cada servicio tiene un fichero `NativeConfig.java` en su paquete `config/`:

```
cat-service/src/main/java/es/kitti/cat/config/NativeConfig.java
adoption-service/src/main/java/es/kitti/adoption/config/NativeConfig.java
...
```

Este fichero declara con `@RegisterForReflection` las clases que GraalVM debe preservar:

```java
@RegisterForReflection(
    targets = {
        // DTOs
        CatResponse.class,
        CatSummaryResponse.class,
        CreateCatRequest.class,
        PageResponse.class,

        // Enums
        CatStatus.class,

        // Eventos Kafka (si el servicio los produce o consume)
        CatCreatedEvent.class,
        CatDeletedEvent.class,
    }
)
public class NativeConfig {}
```

### La regla: siempre en paralelo

**Cada vez que añadas una de estas clases, actualiza el `NativeConfig` del mismo servicio:**

| Qué añades | Lo que hay que registrar |
|---|---|
| Nuevo DTO (record de request o response) | El record en `targets` |
| Nuevo enum | El enum en `targets` |
| Nuevo evento Kafka | El record de evento en `targets` |
| Nueva entidad | Normalmente no hace falta — Hibernate las registra solo |

Si no lo haces, el servicio pasará todos los tests (que corren en JVM) y fallará en producción en cuanto se reciba o envíe un objeto de ese tipo.

### Cómo verificar

Si quieres comprobar que el NativeConfig está completo antes de abrir un PR:

```bash
# Buscar todos los records y enums del servicio
grep -r "public record\|public enum" cat-service/src/main/java/es/kitti/cat/dto/
grep -r "public record\|public enum" cat-service/src/main/java/es/kitti/cat/event/

# Comparar con lo que está en NativeConfig
cat cat-service/src/main/java/es/kitti/cat/config/NativeConfig.java
```

---

## 7. Gestión de deploys: ramas deployment/

### La convención de ramas

Las ramas de despliegue siguen el esquema `deployment/{entorno}/{tipo}`:

| Rama | Entorno | Tipo de cambio |
|---|---|---|
| `deployment/pre-production/release` | Pre-producción | Release planificada |
| `deployment/pre-production/hotfix` | Pre-producción | Corrección urgente |
| `deployment/production/release` | Producción | Release planificada |
| `deployment/production/hotfix` | Producción | Corrección urgente |

Estas ramas no son ramas de desarrollo — son ramas de despliegue. El CI/CD las detecta y lanza el proceso de build y despliegue correspondiente al entorno.

### Versionado semántico (semver)

El proyecto usa semver en tres partes: `MAJOR.MINOR.PATCH`, con sufijo `-rcN` para candidatos a release.

- **Release candidate** (`n.n.0-rcN`) — todo lo que se despliega a pre-producción sale como RC. Cada iteración sobre la misma release incrementa N: `rc1`, `rc2`, `rc3`…
- **Release final** (`n.n.0`) — cuando un RC supera QA se promueve a producción eliminando el sufijo. El artefacto es el mismo; solo cambia el tag.
- **Hotfix** (`n.n.patch`) — incrementa solo PATCH sobre una release final ya publicada.

Ejemplos:
- `v1.3.0-rc1` — primer candidato de la release 1.3.0, desplegado en pre-prod
- `v1.3.0-rc2` — segunda iteración tras encontrar un problema en pre-prod
- `v1.3.0` — promoción a producción del rc2 validado
- `v1.3.1` — primer hotfix sobre la release 1.3.0 en producción

### El flujo de una release

```
feat/nueva-funcionalidad ──┐
fix/bug-encontrado ─────────┤  PRs normales a main
refactor/limpieza ──────────┘
                            │
                            ▼
                          main  ← integración continua
                            │
                            │  (cuando el ciclo de desarrollo cierra)
                            ▼
        deployment/pre-production/release  ← tag v1.4.0-rc1
                            │
                  ✗ bug encontrado en QA
                            │
        deployment/pre-production/hotfix   ← fix, tag v1.4.0-rc2
                            │
                            │  ✓ rc2 validado
                            ▼
           deployment/production/release  ← tag v1.4.0 (mismo artefacto que rc2)
```

El tag de producción es siempre el semver limpio. El RC que lo originó queda en el historial de pre-prod para trazabilidad.

### El flujo de un hotfix

Un hotfix nunca parte de `main` (que puede tener trabajo en curso). Parte de la rama de despliegue del entorno afectado.

```
deployment/production/release (v1.4.0)
        │
        ├── deployment/production/hotfix  ← fix puntual, tag v1.4.1
        │
        │   (cherry-pick a main para no perder el fix)
        └──────────────────────────────────────► main
```

Un hotfix en pre-producción genera un nuevo RC, no un patch:

```
deployment/pre-production/release (v1.4.0-rc1)
        │
        └── deployment/pre-production/hotfix  ← tag v1.4.0-rc2
```

### Reglas de la rama

- **Nunca se desarrolla directamente en `deployment/`**. Son ramas de promoción, no de trabajo.
- **`main` nunca se despliega directamente a producción**. Todo pasa por la rama de pre-producción primero.
- Todo lo que llega a pre-producción es un RC — sin excepción, aunque sea un cambio de una línea.
- Los hotfixes sobre producción siempre se cherry-pick a `main` después de publicarse, para evitar que el fix se pierda en la siguiente release.
- El tag de producción (`v1.4.0`) y el último RC que lo originó (`v1.4.0-rc2`) apuntan al mismo commit, lo que permite verificar que exactamente el mismo artefacto ha pasado QA.

---

## 8. Gotchas explicados

Estos son los errores más comunes en este stack. No los memorices — entiende el porqué.

### Records Java necesitan @JsonProperty

Los Records de Java no exponen los nombres de los parámetros del constructor en tiempo de ejecución por defecto. Jackson no sabe cómo deserializarlos.

```java
// ❌ Jackson no puede deserializar esto
public record CreateCatRequest(String name, String breed) {}

// ✅ Opción A — @JsonProperty en cada campo
public record CreateCatRequest(
        @JsonProperty("name") String name,
        @JsonProperty("breed") String breed
) {}

// ✅ Opción B — registrar ParameterNamesModule en JacksonConfig (ya configurado en el proyecto)
```

### "order" es palabra reservada en HQL

HQL (el dialecto de Hibernate) reserva la palabra `order` para `ORDER BY`. Si tienes un campo llamado `order` en una entidad, las queries fallarán con un error de parseo críptico.

```java
// ❌ Rompe las queries HQL
public int order;

// ✅ Nombre alternativo en Java, nombre real en la columna
@Column(name = "image_order")
public int imageOrder;
```

### Los .proto se copian en cada servicio

No existe un módulo Maven compartido para los ficheros `.proto`. Copia el `.proto` en `src/main/proto/` de cada servicio que lo necesite. La regla de arquitectura prohíbe dependencias Maven entre módulos para evitar acoplamiento en compilación.

### El borrado de usuarios es siempre lógico, nunca físico

`UserStatus` cambia a `Inactive`. La fila nunca se elimina directamente. El borrado físico (anonimización RGPD) lo gestiona el job de erasure de `schedule-service` con un período de gracia de 30 días y registro de auditoría inmutable.

### ProxyService explota con NPE en respuestas sin body

Si un servicio upstream devuelve 204 (sin body) y el `ProxyService` intenta leer el body sin comprobarlo, lanza `NullPointerException`.

```java
// ✅ Siempre comprobar antes de leer el body
if (r.body() != null) {
    // procesar body
}
```

### JwtAuthFilter requiere registro explícito de rutas públicas

El filtro JWT tiene una lista explícita de rutas que no requieren token (`PUBLIC_EXACT`). Si añades un endpoint nuevo que debe ser público (sin JWT), añádelo a esa lista. Si no lo haces, el endpoint devolverá 401 aunque no tenga `@RolesAllowed`.

### Rate limiter: usar X-Forwarded-For en tests e2e

El rate limiter usa la IP como clave para la mayoría de endpoints. Los tests e2e que golpean endpoints con rate limit deben enviar un header `X-Forwarded-For` único por ejecución para evitar que los tests se contaminen entre sí dentro de la ventana de 60 segundos.

```java
// En tests e2e
String testIp = "test-" + System.currentTimeMillis();
given().header("X-Forwarded-For", testIp).when().post("/api/auth/login")...
```

### Quarkus dev usa el directorio del módulo como cwd

Cuando arrancas un servicio con `mvn quarkus:dev -pl <módulo>`, el working directory es el directorio del módulo, no la raíz del proyecto. El fichero `.env` de la raíz no se carga automáticamente.

```bash
# Crear symlink una vez tras clonar (ya existe en storage-service)
ln -sf ../.env <módulo>/.env
```

### Kafka EXTERNAL listener debe escuchar en 0.0.0.0

Docker no redirige al loopback del contenedor. Si el listener externo de Kafka escucha en `127.0.0.1`, los servicios en otros contenedores no pueden conectar.

### Uni.combine() en paralelo con @WithSession → 500 en producción

Hibernate Reactive no permite abrir dos sesiones simultáneamente dentro del mismo contexto Vert.x. En tests unitarios no se aprecia porque Mockito bypassa la gestión de sesiones, pero en producción explota con 500.

```java
// ❌ MAL — abre dos sesiones en paralelo, falla en producción
return Uni.combine().all().unis(
        catRepository.findByCity(city, page, size),   // @WithSession
        catRepository.countByCity(city)               // @WithSession
).asTuple().onItem().transform(...);

// ✅ BIEN — secuencial: la segunda query reutiliza la sesión de la primera
return catRepository.findByCity(city, page, size)
        .onItem().transformToUni(cats ->
                catRepository.countByCity(city)
                        .onItem().transform(count -> PageResponse.of(cats, page, size, count)));
```

Regla general: **nunca uses `Uni.combine()` con métodos que tengan `@WithSession`**. Encadénalos siempre con `transformToUni`.

### Gestión de esquemas: dev, test y prod no son iguales

El modo en que Hibernate gestiona el esquema de la base de datos cambia según el perfil:

| Perfil | Comportamiento | Por qué |
|---|---|---|
| `%dev` | `update` — altera las tablas si hace falta | Para no perder datos locales entre reinicios |
| `%test` | `drop-and-create` — recrea el esquema en cada test | Para garantizar un estado limpio y predecible |
| `%prod` | `validate` + Flyway | Flyway aplica las migraciones; Hibernate solo verifica que el esquema coincide |

Implicaciones:
- **No uses Flyway en dev** — Hibernate gestiona el esquema solo.
- **No uses `update` en prod** — si añades una columna `NOT NULL` sin valor por defecto, Hibernate no sabe cómo migrar las filas existentes.
- Cuando añadas una feature que requiere cambios de esquema, crea el fichero de migración Flyway correspondiente en `src/main/resources/db/migration/` del servicio. El nombre sigue el patrón `V{version}__{descripcion}.sql`.

---

## 9. Checklist antes de tu primer PR

Antes de abrir un pull request, verifica estos puntos:

**Reactividad**
- [ ] Ningún método del resource, service o repository devuelve un tipo bloqueante (`List<T>`, `Optional<T>`, `void`)
- [ ] No hay ningún `.await().indefinitely()` ni `Thread.sleep` en código de producción
- [ ] Los métodos que devuelven `Multi<T>` no tienen `@WithSession` (el gotcha de sesión)
- [ ] Los consumers `@Incoming` de Kafka delegan la persistencia a un bean separado
- [ ] Las queries que requieren `@WithSession` están encadenadas con `transformToUni`, no con `Uni.combine()`

**Base de datos**
- [ ] Las escrituras tienen `@WithTransaction`, las lecturas tienen `@WithSession`
- [ ] Dentro de `Panache.withTransaction(() -> ...)` no se añaden anotaciones de sesión redundantes
- [ ] Los endpoints que devuelven colecciones tienen paginación (`page`, `size`)
- [ ] Si el cambio requiere modificar el esquema, hay un fichero de migración Flyway en `src/main/resources/db/migration/`

**Seguridad**
- [ ] Los endpoints internos están anotados con `@InternalOnly`
- [ ] Ningún endpoint `@InternalOnly` está registrado en las rutas del gateway
- [ ] Los endpoints nuevos que deben ser públicos están en la lista `PUBLIC_EXACT` del `JwtAuthFilter`

**Manejo de errores**
- [ ] Los errores de dominio esperados se devuelven como `Either.left(new XxxError(...))`, no como excepciones
- [ ] No se han creado clases de excepción de dominio nuevas

**Native Image**
- [ ] Cada DTO, enum o evento de Kafka nuevo está registrado en `NativeConfig.java` del servicio

**Código**
- [ ] Los DTOs son Records Java con `@JsonProperty` o con `ParameterNamesModule` configurado
- [ ] Las entidades no declaran `@Id` (lo provee `PanacheEntity`)
- [ ] Los campos de entidad son `public`
- [ ] Los valores de enums están en PascalCase (`Pending`, no `PENDING`)
- [ ] Ningún nombre de campo de entidad es una palabra reservada HQL (`order`, `group`, `select`...)

**Arquitectura**
- [ ] No hay dependencias Maven entre módulos
- [ ] No hay relaciones JPA entre entidades de distintos servicios
- [ ] La comunicación entre servicios es solo vía HTTP interno (`@InternalOnly`), gRPC o Kafka

**Commits y ramas**
- [ ] La rama sigue la convención `<tipo>/<descripción>` (nunca se trabaja en `main` ni en ramas `deployment/`)
- [ ] Un commit por capa o servicio (no un commit gigante con todo)
- [ ] Si el cambio lleva migrations SQL, van en un commit separado antes del dominio

---

*Para cualquier duda sobre patrones del proyecto, consulta [CLAUDE.md](CLAUDE.md). Para la arquitectura general y los servicios, consulta [README.md](README.md).*