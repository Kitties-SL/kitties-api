# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Kitties — Claude Code Rules

## Stack
- Java 21, Quarkus 3.34.3, Hibernate Reactive + Panache, SmallRye Mutiny
- PostgreSQL 16 (esquemas separados por servicio), MinIO (S3-compatible)
- Kafka (SmallRye Reactive Messaging), gRPC (auth↔user), SmallRye JWT (15 min)

## Servicios y puertos

Los 11 servicios son todos activos e intencionales. No eliminar ninguno de esta tabla; los puertos están verificados contra `application.properties` de cada módulo.

| Servicio             | HTTP | gRPC        | Rutas públicas (sin JWT)                        |
|----------------------|------|-------------|-------------------------------------------------|
| gateway-service      | 8080 | —           | proxy de todas las rutas públicas               |
| user-service         | 8081 | 9090 server | `POST /users`, `POST /users/activate`           |
| auth-service         | 8082 | —           | `POST /auth/login`, `POST /auth/refresh`        |
| storage-service      | 8083 | —           | `GET /storage/files/{key}`                      |
| cat-service          | 8084 | —           | `GET /cats`, `GET /cats/{id}`                   |
| notification-service | 8085 | —           | —                                               |
| adoption-service     | 8086 | —           | —                                               |
| form-analysis-service| 8087 | —           | —                                               |
| organization-service | 8088 | —           | —                                               |
| chat-service         | 8089 | —           | —                                               |
| schedule-service     | 8090 | —           | — (sin endpoints públicos; solo `/q/health`)    |

## Arquitectura — reglas duras
- Sin dependencias Maven entre módulos de servicio. Comunicación solo vía gRPC o Kafka.
- Excepción explícita: todos los servicios dependen de **`either-mon`** (módulo de utilidades puras, sin lógica de negocio). Ver sección _"Manejo de errores — either-mon"_.
- Sin relaciones JPA cross-service. Cada servicio tiene su propio esquema PostgreSQL.
- DTOs siempre (Records Java). Nunca exponer entidades Panache directamente.
- Patrón Repository, no Active Record.

## Patrones de código

### Entidades — Panache Active Entity (PanacheEntity)

Las entidades extienden `PanacheEntity`, que ya aporta `public Long id` con `@Id @GeneratedValue`. **No declarar `@Id` nunca.** Los campos son `public` (Panache los intercepta en bytecode). Timestamps con `@PrePersist` / `@PreUpdate`.

```java
@Entity
@Table(name = "adoption_requests", schema = "adoption")
public class AdoptionRequest extends PanacheEntity {

    @Column(nullable = false)
    public Long catId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AdoptionStatus status;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        status = AdoptionStatus.Pending;
    }
}
```

### Repositorios — PanacheRepository

`implements PanacheRepository<Entity>`, `@ApplicationScoped`. Usar los métodos de Panache (`list`, `find`, `count`, `persist`, `deleteById`) directamente. No extender `PanacheEntity` desde el repositorio.

```java
@ApplicationScoped
public class AdoptionRequestRepository implements PanacheRepository<AdoptionRequest> {

    public Uni<List<AdoptionRequest>> findByAdopterId(Long adopterId) {
        return list("adopterId", adopterId);
    }
}
```

### DTOs — Records Java

Records sin constructores especiales. Anotaciones de validación Jakarta en los de request; los de response sin anotaciones.

```java
// Request
public record AdoptionRequestCreateRequest(
        @NotNull Long catId,
        @NotNull Long organizationId
) {}

// Response
public record AdoptionRequestResponse(
        Long id,
        Long catId,
        AdoptionStatus status,
        LocalDateTime createdAt
) {}
```

### Enums

Valores en PascalCase, nunca SCREAMING_SNAKE_CASE:

```java
public enum AdoptionStatus { Pending, Reviewing, Accepted, Rejected, Completed }
```

## Manejo de errores — either-mon

Módulo Maven `either-mon` (`es.kitti.mon`). Contiene tipos funcionales puros sin lógica de negocio. **No añadir nada fuera de ese perímetro** (clientes HTTP, eventos Kafka, utilidades de Quarkus → van en el servicio correspondiente).

### Either<L, R>

Sealed interface con `Left` y `Right`. Métodos: `map`, `flatMap`, `fold`, `getOrElse`, `isLeft`, `isRight`.

Para operaciones de escritura cuyo happy path no devuelve ningún valor útil, usar `Either<DomainError, Unit>` con el factory `Either.unit()`:
```java
return Uni.createFrom().item(Either.<DomainError>unit()); // Right(Unit.Instance)
```

### Try<T>

Captura excepciones de código síncrono y las convierte en `Either`. Útil en resources para parsear parámetros que pueden lanzar:
```java
Try.attempt(() -> LocalDateTime.parse(param))
   .<DomainError>toEither(e -> new BadRequestError("INVALID_DATE_FORMAT"))
   .fold(err -> Uni.createFrom().item(Response.status(err.httpStatus())...),
         date -> service.doSomething(date))
```

```java
import es.kitti.mon.either.Either;
import es.kitti.mon.error.*;
```

### Cuándo usar Uni<Either<DomainError, T>>

Cualquier método de service que pueda devolver un error de dominio esperado (not found, forbidden, conflict). Los errores inesperados (infraestructura, bugs) siguen propagándose como fallo del Uni y los captura `GlobalExceptionMapper`.

```java
// Service
@WithSession
public Uni<Either<DomainError, CatResponse>> findById(Long id) {
    return catRepository.findById(id)
            .onItem().transformToUni(cat -> {
                if (cat == null)
                    return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                return ...
                        .onItem().transform(images ->
                                Either.<DomainError, CatResponse>right(catMapper.toResponse(cat, images)));
            });
}

// Resource
public Uni<Response> findById(@PathParam("id") Long id) {
    return catService.findById(id)
            .onItem().transform(either -> either.fold(
                    err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                    cat -> Response.ok(cat).build()
            ));
}
```

### Tipos base de DomainError

| Record | HTTP | Cuándo usarlo |
|--------|------|---------------|
| `NotFoundError(String code)` | 404 | Entidad no encontrada |
| `ForbiddenError(String code)` | 403 | Acceso denegado por permisos de dominio |
| `ConflictError(String code)` | 409 | Conflicto de estado (duplicado, límite, adopciones activas…) |
| `UnauthorizedError(String code)` | 401 | Credenciales o token inválidos |
| `BadRequestError(String code)` | 400 | Validación de contenido (p.ej. tipo MIME inválido) |
| `ValidationError(List<FieldViolation>)` | 422 | Resultado de `ConstraintViolationMapper` |

El campo `code` es un identificador machine-readable para que el cliente pueda mostrar el mensaje en el idioma del usuario. Convención: `ENTIDAD_MOTIVO` en mayúsculas, p.ej. `CAT_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`, `CAT_HAS_ACTIVE_ADOPTIONS`.

### Validación de formularios — ConstraintViolationMapper

El `GlobalExceptionMapper` de cada servicio convierte `ConstraintViolationException` en `ValidationError` con lista de `FieldViolation(field, code, params)`:

```json
{ "status": 422, "code": "VALIDATION_FAILED",
  "violations": [
    { "field": "email", "code": "INVALID_EMAIL" },
    { "field": "password", "code": "INVALID_SIZE", "params": { "min": 8 } }
  ]
}
```

Códigos normalizados: `REQUIRED` (NotNull/NotBlank), `INVALID_SIZE` (Size), `INVALID_EMAIL` (Email), `INVALID_FORMAT` (Pattern), `TOO_SMALL`/`TOO_LARGE` (Min/Max).

El `default` case del `GlobalExceptionMapper` usa `ErrorResponse.internalError()`:
```java
default -> {
    Log.errorf(exception, "Unhandled exception: %s", exception.getMessage());
    yield Response.status(500).entity(ErrorResponse.internalError()).build();
}
```

> **Nota:** el `GlobalExceptionMapper` implementa `ExceptionMapper<Throwable>`, que es menos específico que el mapper built-in de Quarkus para `ConstraintViolationException`. El mapper built-in tiene precedencia para validaciones `@Valid` en endpoints JAX-RS y devuelve 400. El `ConstraintViolationMapper` solo actúa para violaciones lanzadas manualmente desde código de servicio.

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

---

## Paginación — PageResponse<T>

Todos los endpoints de colección públicos deben paginar. El record `PageResponse<T>` vive en el servicio que lo usa (actualmente en `cat-service/dto/PageResponse.java`).

```json
{ "content": [...], "page": 0, "size": 20, "total": 142, "totalPages": 8 }
```

### Patrón de repositorio paginado

Dos métodos por variante: uno para los datos paginados y otro para el total. **Siempre secuenciales** (ver gotcha de `Uni.combine()` más abajo):

```java
@WithSession
public Uni<List<Cat>> findByCity(String city, int page, int size) {
    return find("status = ?1 and lower(city) like lower(?2)",
            CatStatus.Available, "%" + city + "%").page(page, size).list();
}

@WithSession
public Uni<Long> countByCity(String city) {
    return count("status = ?1 and lower(city) like lower(?2)",
            CatStatus.Available, "%" + city + "%");
}
```

### Patrón de service — cadena secuencial

```java
public Uni<PageResponse<CatSummaryResponse>> search(String city, int page, int size) {
    if (size > 100) size = 100;
    final int effectiveSize = size;
    return catRepository.findByCity(city, page, effectiveSize)
            .onItem().transformToUni(cats ->
                    catRepository.countByCity(city)
                            .onItem().transform(count -> {
                                var content = cats.stream().map(catMapper::toSummaryResponse).toList();
                                return PageResponse.of(content, page, effectiveSize, count);
                            }));
}
```

### Patrón de resource

```java
@GET @PermitAll
public Uni<Response> search(
        @QueryParam("city") String city,
        @QueryParam("page") @DefaultValue("0") int page,
        @QueryParam("size") @DefaultValue("20") int size) {
    return catService.search(city, page, size)
            .onItem().transform(result -> Response.ok(result).build());
}
```

---

## Reactividad (Mutiny)
- `@WithSession` para lecturas en Service; `@WithTransaction` para escrituras.
- `@WithSession` NO funciona en métodos que devuelven `Multi<T>` → poner `@WithSession` en el Repository y transformar en Service:
  ```java
  public Multi<T> search() {
      return repo.findAll()
          .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
  }
  ```
- **`@Incoming` (Kafka) + `@WithTransaction` combinados directamente → fallo.** Delegar la persistencia a un bean `@ApplicationScoped` separado con `@WithTransaction` en el método:
  ```java
  // MAL — @Incoming con Panache.withTransaction() inline
  @Incoming("topic") public Uni<Void> onEvent(String msg) { return Panache.withTransaction(() -> ...); }

  // BIEN — @Incoming delega a un bean @WithTransaction
  @Incoming("topic") public Uni<Void> onEvent(String msg) { return writeService.persist(...); }

  @ApplicationScoped class WriteService {
      @WithTransaction public Uni<Void> persist(...) { ... }
  }
  ```

## Autenticación interna (servicio-a-servicio)

Algunos endpoints no deben ser accesibles por usuarios ni por el gateway — solo por otros servicios del sistema (p.ej. `schedule-service` disparando un job, o `user-service` llamando a `adoption-service` durante un borrado). Para esto existe el patrón `@InternalOnly`.

### Cómo funciona

Se basa en el mecanismo `@NameBinding` de JAX-RS: una anotación personalizada que enlaza un `ContainerRequestFilter` únicamente a los recursos o métodos marcados con ella. El filtro comprueba el header `X-Internal-Token` contra el secreto compartido `kitties.internal.secret`. Si no coincide, aborta con 401.

```
petición entrante
      │
      ▼
InternalTokenFilter.filter()          ← solo se ejecuta en métodos/clases @InternalOnly
  compara X-Internal-Token con kitties.internal.secret
  ✗ → 401 Unauthorized
  ✓ → continúa al resource
```

### Los dos ficheros que se copian en cada servicio

Ambos viven en `<servicio>/src/main/java/es/kitti/<servicio>/security/`:

**`InternalOnly.java`** — la anotación de binding (idéntica en todos los servicios):
```java
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface InternalOnly {}
```

**`InternalTokenFilter.java`** — el filtro que valida el header:
```java
@Provider
@InternalOnly                                   // ← el binding enlaza filtro y anotación
public class InternalTokenFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    @ConfigProperty(name = "kitties.internal.secret")
    String secret;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String token = ctx.getHeaderString(HEADER);
        if (token == null || !token.equals(secret)) {
            ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"status\":401,\"message\":\"Missing or invalid internal token\"}")
                    .build());
        }
    }
}
```

> **Por qué no hay un módulo Maven compartido:** La regla de arquitectura prohíbe dependencias Maven entre módulos. Además, son ~60 líneas de código sin lógica de negocio que no evoluciona. El coste de la duplicación es menor que el acoplamiento en compilación que introduciría un módulo compartido. Si la implementación cambiara (p.ej. HMAC en lugar de secreto plano), el cambio es mecánico en los 5 servicios afectados.

### Servicios que ya tienen el patrón

| Servicio | Ruta de los ficheros |
|---|---|
| `cat-service` | `security/InternalOnly.java`, `security/InternalTokenFilter.java` |
| `adoption-service` | `security/InternalOnly.java`, `security/InternalTokenFilter.java` |
| `auth-service` | `security/InternalOnly.java`, `security/InternalTokenFilter.java` |
| `chat-service` | `security/InternalOnly.java`, `security/InternalTokenFilter.java` |
| `user-service` | `security/InternalOnly.java`, `security/InternalTokenFilter.java` |

### Cómo añadir el patrón a un nuevo servicio

1. Copiar los dos ficheros a `<servicio>/src/main/java/es/kitti/<servicio>/security/` ajustando el `package`.
2. Verificar que `application.properties` tenga la config del secreto (igual en todos los servicios):
   ```properties
   %dev.kitties.internal.secret=${KITTIES_INTERNAL_SECRET:kitties-dev-secret}
   %prod.kitties.internal.secret=${KITTIES_INTERNAL_SECRET}
   %test.kitties.internal.secret=test-internal-secret
   ```
3. Anotar el resource o el método con `@InternalOnly`:
   ```java
   @Path("/foo/internal")
   @InternalOnly                    // ← toda la clase queda protegida
   public class FooInternalResource { ... }
   ```

### Cómo llamar a un endpoint interno desde otro servicio

El cliente REST debe enviar el secreto en el header. Patrón estándar con MicroProfile REST Client:

```java
@RegisterRestClient(configKey = "foo-service")
@Path("/foo/internal")
public interface FooInternalClient {

    @POST
    @Path("/alguna-accion")
    Uni<Response> ejecutar(@HeaderParam("X-Internal-Token") String token);
}
```

En el servicio llamante, inyectar `@ConfigProperty(name = "kitties.internal.secret") String internalSecret` y pasarlo al método del cliente.

### Regla: nunca exponer endpoints `@InternalOnly` por el gateway

El gateway (`gateway-service`, puerto 8080) no debe hacer proxy de rutas `/*/internal/*`. Estos endpoints deben ser accesibles únicamente desde la red interna de contenedores, no desde internet.

---

## Gotchas conocidos
- Records Java: añadir `@JsonProperty` en cada campo, o registrar `ParameterNamesModule` en JacksonConfig.
- `"order"` es palabra reservada HQL → usar `imageOrder` con `@Column(name = "image_order")`.
- Copiar el `.proto` en `src/main/proto/` de cada servicio que lo necesite. No compartir módulo Maven.
- Borrado de usuarios: lógico (status → Inactive), nunca físico.
- `ProxyService` explota con NPE si la respuesta upstream no tiene body (ej. 204). Siempre guardar `r.body() != null`.
- `JwtAuthFilter` tiene una lista explícita de rutas públicas (`PUBLIC_EXACT`). Añadir ahí cualquier endpoint nuevo que no requiera Bearer token.
- **Auth servicio-a-servicio (interno)**: ver sección completa **"Autenticación interna (servicio-a-servicio)"** más arriba. Resumen: para HTTP usar `@InternalOnly` + `X-Internal-Token`; para gRPC usar metadata `x-internal-token` validada por `GrpcAuthInterceptor` (server) / `GrpcClientAuthInterceptor` (client).
- El rate limiter (`IpRateLimiter`) usa email como clave para login y IP para el resto. No cambiar a global o los tests se contaminarán entre sí.
- **Tests e2e + rate limiter**: enviar siempre `X-Forwarded-For: TEST_IP` (único por ejecución, ej. `"test-" + System.currentTimeMillis()`) en los requests que consuman del mismo bucket de rate limit. Sin esto los tests se contaminan entre ejecuciones dentro de la ventana de 60 s.
- **`minio/minio:latest` NO crea buckets automáticamente** con `MINIO_DEFAULT_BUCKETS` (eso es una feature de `bitnami/minio`). Usar un `BucketInitializer` (`@Observes StartupEvent`) que haga `headBucket` → `createBucket` con `S3AsyncClient.get()` (bloqueante en startup está bien).
- **Recursos no-código no se empaquetan en build nativo por defecto.** Ficheros de `src/main/resources` leídos por classloader en runtime (CSV, datos semilla, etc.) necesitan declararse en `quarkus.native.resources.includes` o `getResourceAsStream` devuelve `null` solo en nativo (en JVM funciona, así que el fallo aparece tarde). Ej.: `organization-service` con `geo/*.csv` para el diccionario ciudad→coordenadas cargado en `@Observes StartupEvent`.
- **Quarkus dev cwd = directorio del módulo**, no la raíz del proyecto. El `.env` raíz NO se carga cuando se arranca con `mvn -pl <módulo>`. Solución: symlink `<módulo>/.env → ../.env` (ya existe en `storage-service`). Si los defaults en `application.properties` no coinciden con el `.env` raíz, el servicio usará credenciales incorrectas.
- Kafka EXTERNAL listener debe vincularse a `0.0.0.0` dentro del contenedor, no a `127.0.0.1` (Docker no redirige al loopback del contenedor).
- `MailHogClient.extractActivationToken` espera el body decodificado de Quoted-Printable. Los emails HTML llegan con soft line breaks (`=\n`) y `=3D` en lugar de `=`.
- **DIP — nunca `Panache.withTransaction()` / `Panache.withSession()` inline en la capa de servicio.** La gestión de transacciones pertenece a beans de infraestructura dedicados (`*WriteService`) con `@WithTransaction` en el método. Un servicio de dominio llama a ese bean inyectado; Mockito puede mockearlo. Las llamadas estáticas de Panache requieren el contexto Quarkus completo y no son testables con `@InjectMocks`.
- **Testabilidad Mockito:** `@WithSession` / `@WithTransaction` como anotaciones CDI son bypassadas por `@InjectMocks` → no necesitan contexto Vert.x. Las llamadas `Panache.withSession()` / `Panache.withTransaction()` **estáticas e inline** sí necesitan el contexto Quarkus → no testables con Mockito puro.
- **`Uni.combine()` con dos métodos `@WithSession` en paralelo → 500 en producción.** Hibernate Reactive no permite abrir dos sesiones simultáneamente en el mismo contexto Vert.x. En unit tests no se aprecia porque Mockito bypassa la gestión de sesiones. Solución: siempre encadenar con `transformToUni` (secuencial), nunca combinar en paralelo queries que requieran sesión.
- **Excepciones de dominio eliminadas.** No crear clases `XxxNotFoundException`, `XxxAlreadyExistsException`, etc. Usar los tipos de `either-mon` (`NotFoundError`, `ConflictError`…) y devolver `Either.left(...)`.

## Comandos habituales

```bash
# Compilar módulo
mvn compile -pl <módulo>

# Tests de un módulo (unit + integración)
mvn test -pl <módulo>

# Una sola clase de test
mvn test -pl <módulo> -Dtest=CatServiceTest

# Un solo método
mvn test -pl <módulo> -Dtest=CatServiceTest#createCat_shouldPersist

# Arrancar un servicio en dev mode
mvn compile quarkus:dev -pl <módulo> -am

# Arrancar todos los servicios (gateway incluido)
./dev.sh

# Arrancar gateway + servicios concretos
./dev.sh user-service,auth-service

# Arrancar todo y correr e2e al terminar
./dev.sh --e2e

# Solo e2e (requiere stack completo corriendo)
mvn test -Pe2e -pl e2e-tests
```

## Estructura de paquetes (igual en todos los servicios)

```
src/main/java/es/kitti/<servicio>/
  entity/      PanacheEntity subclasses
  repository/  PanacheRepository implementations (@ApplicationScoped)
  service/     Business logic (@WithSession para lecturas; escribe delegando a *WriteService)
  service/*WriteService  Beans @ApplicationScoped con @WithTransaction para escrituras atómicas
  resource/    JAX-RS endpoints (@Path, @RolesAllowed)
  dto/         Java Records (request / response)
  mapper/      Manual entity ↔ DTO conversion
  exception/   GlobalExceptionMapper únicamente (las excepciones de dominio están en either-mon)
  client/      HTTP/gRPC clients to other services (if needed)
  config/      @ConfigProperty beans
```

## Comportamiento esperado
- **Antes de comenzar cualquier desarrollo**: crear una rama con el formato `<tipo>/<descripción>`, donde `<tipo>` sigue la convención de conventional commits (`feat`, `fix`, `chore`, `docs`, `refactor`, `test`). Ejemplos: `feat/flyway-migrations`, `fix/rate-limiter-bucket`.
- **Compilar solo el módulo afectado**: `mvn compile -pl <módulo>` o `mvn test -pl <módulo>`.
- **Tests e2e**: `mvn test -Pe2e -pl e2e-tests` (requiere stack completo corriendo).
- **Si un fix falla dos veces seguidas por el mismo motivo → parar y preguntar al usuario.**
- Cambios atómicos: modificar solo lo necesario. No reescribir ficheros completos salvo que sea inevitable.
- No añadir comentarios salvo que el WHY sea no obvio.
- No añadir atribución `Co-Authored-By: Anthropic` en commits.