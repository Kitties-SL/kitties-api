# either-mon — Manual de uso

Librería de tipos funcionales para manejo de errores y validación en los servicios de Kitties.
Dos tipos principales: `Either<L, R>` para errores de dominio en servicios, y `Validation<T>` para
acumulación de violaciones en la capa de entrada.

---

## Either<L, R>

Representa un resultado que puede ser un error (`Left`) o un valor (`Right`). Hace los errores
de dominio explícitos en el tipo de retorno: si un método devuelve
`Uni<Either<DomainError, CatResponse>>`, el compilador obliga a tratar ambos casos.

### Cuándo usarlo

En métodos de servicio que pueden fallar por razones de negocio esperadas: entidad no encontrada,
acceso denegado, conflicto de estado. Los errores inesperados (infraestructura, bugs) siguen
propagándose como fallos del `Uni` y los captura el `GlobalExceptionMapper`.

### API

```java
// Construcción
Either<DomainError, Cat> ok  = Either.right(cat);
Either<DomainError, Cat> err = Either.left(new NotFoundError("CAT_NOT_FOUND"));

// Para operaciones de escritura sin valor de retorno útil: Unit en lugar de Void/null
Either<DomainError, Unit> ok = Either.unit();          // Right(Unit.Instance)
Either<DomainError, Unit> ok = Either.<DomainError>unit(); // con type witness explícito

// Inspección
result.isRight()   // true si es Right
result.isLeft()    // true si es Left

// Extraer valor con fallback
Cat cat = result.getOrElse(null);

// Transformar el Right (Left se propaga sin tocar)
Either<DomainError, CatResponse> response = result.map(cat -> mapper.toResponse(cat));

// Encadenar operaciones que también pueden fallar
Either<DomainError, String> name = result.flatMap(cat ->
        cat.name == null ? Either.left(new BadRequestError("NAME_MISSING")) : Either.right(cat.name));

// Colapsar ambas ramas a un mismo tipo
Response httpResponse = result.fold(
        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
        cat -> Response.ok(cat).build()
);
```

### Patrón completo: service → resource

```java
// Service
@WithSession
public Uni<Either<DomainError, CatResponse>> findById(Long id, Long callerId) {
    return catRepository.findById(id)
            .onItem().transformToUni(cat -> {
                if (cat == null)
                    return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                if (!cat.organizationId.equals(callerId))
                    return Uni.createFrom().item(Either.left(new ForbiddenError("CAT_ACCESS_DENIED")));
                return Uni.createFrom().item(Either.<DomainError, CatResponse>right(mapper.toResponse(cat)));
            });
}

// Resource
public Uni<Response> findById(@PathParam("id") Long id) {
    Long callerId = Long.parseLong(jwt.getSubject());
    return catService.findById(id, callerId)
            .onItem().transform(either -> either.fold(
                    err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                    cat -> Response.ok(cat).build()
            ));
}
```

---

## DomainError — tipos disponibles

| Tipo | HTTP | Cuándo usarlo |
|---|---|---|
| `NotFoundError(String code)` | 404 | Entidad no encontrada |
| `ForbiddenError(String code)` | 403 | Acceso denegado por permisos de dominio |
| `ConflictError(String code)` | 409 | Estado inválido: duplicado, límite alcanzado, adopción activa… |
| `UnauthorizedError(String code)` | 401 | Token inválido o credenciales incorrectas |
| `BadRequestError(String code)` | 400 | Contenido inválido que no encaja en validación de campos |
| `ValidationError(List<FieldViolation>)` | 422 | Resultado de `Validation` — ver sección siguiente |

El campo `code` es un identificador machine-readable para que el cliente pueda localizar el mensaje
en el idioma del usuario. Convención: `ENTIDAD_MOTIVO` en mayúsculas, p.ej. `CAT_NOT_FOUND`,
`EMAIL_ALREADY_EXISTS`, `CAT_HAS_ACTIVE_ADOPTIONS`.

---

## Validation<T>

Acumula todas las violaciones de validación en lugar de parar en la primera. Diseñado para validar
la entrada HTTP antes de llegar al servicio.

**Diferencia clave con Either**: `Either` cortocircuita (si algo falla, lo demás no se evalúa).
`Validation` sigue evaluando todos los campos y devuelve todas las violaciones juntas.

### Estados

```java
Validation.Valid<T>   // contiene un valor de tipo T
Validation.Invalid<T> // contiene un ValidationError con lista de FieldViolation
```

### Fábricas estáticas

```java
Validation.valid(value)                    // construye un Valid<T>
Validation.invalid(validationError)        // construye un Invalid<T> con lista ya formada
Validation.invalidOne("field", "CODE")     // atajo: Invalid con una sola violación
```

### Operadores de cadena

Todos devuelven `Validation<T>` (el tipo del que se llaman), lo que permite encadenar.

| Operador | Cuándo usar |
|---|---|
| `.required("field", value)` | Campo obligatorio de cualquier tipo (Long, Boolean, enum…). Falla si `null`. |
| `.requiredString("field", value)` | Campo de texto obligatorio. Falla si `null` o blank. |
| `.optional(value, validator)` | Campo opcional. Si `value` es `null`, se salta. Si no, aplica el validator. |
| `.and(Validation<?> other)` | Concatena una validación ya construida (p.ej. resultado de un VO). |

### Transformación

```java
// Transforma el tipo del Valid (el Invalid se propaga sin tocar)
Validation<String> nameValidation = Name.of("name", raw).map(vo -> vo.value());

// Combina dos Validation independientes en un único resultado
// Si alguna es Invalid, acumula los errores de ambas
Validation<AuthRequest> auth = Email.of(email).zip(Password.of(password),
        (e, p) -> new AuthRequest(e.value(), p.value()));
```

### Terminales

```java
// Colapsar a cualquier tipo (el uso más habitual en el resource)
Uni<Response> response = validation.match(
        err -> Uni.createFrom().item(Response.status(422).entity(ErrorResponse.of(err)).build()),
        valid -> service.create(valid).onItem().transform(result -> Response.ok(result).build())
);

// Convierte a Either<DomainError, T> (útil si el resto del flujo usa Either)
Either<DomainError, T> either = validation.toEither();

// Extrae el valor cuando se sabe con certeza que es Valid (lanza IllegalStateException si no)
// Usar solo tras una cadena de validación que ya garantiza el Valid
T value = validation.fromBusiness();
```

### Patrón validate() en un DTO

```java
public record CatCreateRequest(
        @JsonProperty("name")    String name,
        @JsonProperty("age")     Integer age,
        @JsonProperty("sex")     String sex,
        @JsonProperty("city")    String city,
        @JsonProperty("country") String country
) {
    public Validation<CatCreateRequest> validate() {
        return Validation.valid(this)
                .and(Name.of("name", name))   // VO con sus propias reglas
                .and(CatAge.of(age))
                .and(Sex.of(sex))
                .and(City.of(city))
                .and(Country.of(country));
    }
}
```

### Campos opcionales

```java
public record CatUpdateRequest(...) {
    public Validation<CatUpdateRequest> validate() {
        return Validation.valid(this)
                .optional(name,    v -> Name.of("name", v))  // solo valida si name != null
                .optional(age,     CatAge::of)
                .optional(city,    City::of)
                .optional(country, Country::of);
    }
}
```

### Campos con lógica compuesta (required + rango)

Cuando un campo requiere más de una regla (null check + validación de rango), extraer un
validador privado estático en el propio record:

```java
public record AdoptionFormRequest(...) {
    public Validation<AdoptionFormRequest> validate() {
        return Validation.valid(this)
                .required("hasChildren",   hasChildren)
                .and(adultsInHousehold(adultsInHousehold))   // null + rango
                .and(hoursAlonePerDay(hoursAlonePerDay));
    }

    private static Validation<?> adultsInHousehold(Integer v) {
        if (v == null) return Validation.invalidOne("adultsInHousehold", "REQUIRED");
        if (v < 1)     return Validation.invalidOne("adultsInHousehold", "TOO_SMALL");
        return Validation.valid(v);
    }

    private static Validation<?> hoursAlonePerDay(Integer v) {
        if (v == null)        return Validation.invalidOne("hoursAlonePerDay", "REQUIRED");
        if (v < 0 || v > 24) return Validation.invalidOne("hoursAlonePerDay", "INVALID_FORMAT");
        return Validation.valid(v);
    }
}
```

---

## Value Objects

Un Value Object (VO) garantiza que un campo es válido por construcción: si tienes una instancia
de `Name`, sabes que el valor cumple todas las reglas de negocio del nombre.

### Patrón estándar

```java
public final class Name {

    private static final int MAX_LENGTH = 100;

    private final String value;

    private Name(String value) {           // constructor privado
        this.value = value;
    }

    public static Validation<Name> of(String field, String raw) {
        if (raw == null || raw.isBlank()) return Validation.invalidOne(field, "REQUIRED");
        if (raw.length() > MAX_LENGTH)    return Validation.invalidOne(field, "INVALID_SIZE");
        return Validation.valid(new Name(raw.trim()));
    }

    public String value() { return value; }
}
```

Reglas:
- `final class` — no se puede extender
- Constructor privado — la única ruta de creación es `of()`
- `of()` devuelve `Validation<VO>`, nunca lanza excepciones
- El campo `field` permite que el mismo VO valide campos con distinto nombre (`Name.of("name", ...)`,
  `Name.of("surname", ...)`)

---

## Try<T>

Captura excepciones de código síncrono y las convierte en `Either`. Especialmente útil en la
capa resource para parsear parámetros de query string que pueden lanzar en el parsing:

```java
// Parsear fecha de un query param sin propagar la excepción
Try.attempt(() -> LocalDateTime.parse(holdUntilIso))
   .<DomainError>toEither(e -> new BadRequestError("INVALID_DATE_FORMAT"))
   .fold(
       err       -> Uni.createFrom().item(Response.status(err.httpStatus())...),
       holdUntil -> service.setLegalHold(userId, holdUntil)...
   );
```

`Try.attempt(Callable)` ejecuta el lambda: si tiene éxito devuelve `Try.Success(value)`;
si lanza, devuelve `Try.Failure(exception)`. `toEither(Function<Exception, DomainError>)`
convierte el fallo en el tipo de error de dominio apropiado.

---

## ErrorResponse y FieldViolation

Lo que llega al cliente cuando hay errores:

```json
{
  "status": 422,
  "code": "VALIDATION_FAILED",
  "violations": [
    { "field": "name",    "code": "REQUIRED" },
    { "field": "age",     "code": "INVALID_RANGE" },
    { "field": "country", "code": "REQUIRED" }
  ],
  "timestamp": "2026-05-13T10:30:00"
}
```

Para errores de dominio sin violaciones:

```json
{ "status": 404, "code": "CAT_NOT_FOUND", "timestamp": "2026-05-13T10:30:00" }
```

### Códigos normalizados

| Código | Cuándo |
|---|---|
| `REQUIRED` | Campo null o blank |
| `INVALID_SIZE` | Longitud fuera de rango (texto) |
| `INVALID_FORMAT` | Formato incorrecto (email, patrón…) |
| `TOO_SMALL` | Valor numérico por debajo del mínimo |
| `TOO_LARGE` | Valor numérico por encima del máximo |
| `INVALID_VALUE` | Valor no pertenece al conjunto válido (p.ej. enum desconocido) |

### Factory para errores 500

El `GlobalExceptionMapper` de cada servicio usa `ErrorResponse.internalError()` para el
caso por defecto en lugar de construir el record a mano:

```java
default -> {
    Log.errorf(exception, "Unhandled exception: %s", exception.getMessage());
    yield Response.status(500).entity(ErrorResponse.internalError()).build();
}
```

### Añadir params a una violación

Para códigos como `INVALID_SIZE` que necesitan comunicar los límites al cliente:

```java
new FieldViolation("password", "INVALID_SIZE", Map.of("min", 8, "max", 100))
```

El campo `params` se omite en el JSON si está vacío.

---

## Patrones en contexto Quarkus

### Resource — el helper validationFailed

Todos los resources tienen este helper privado para no repetir el 422 en cada endpoint:

```java
private Uni<Response> validationFailed(ValidationError err) {
    return Uni.createFrom().item(
            Response.status(422).entity(ErrorResponse.of(err)).build()
    );
}
```

Uso:

```java
@POST
public Uni<Response> create(CatCreateRequest request) {
    Long callerId = Long.parseLong(jwt.getSubject());
    return request.validate().match(
            this::validationFailed,
            valid -> catService.create(valid, callerId)
                    .onItem().transform(cat -> Response.status(201).entity(cat).build())
    );
}
```

### Service — firma estándar para operaciones que pueden fallar

Las lecturas usan `@WithSession`; las escrituras se delegan a un bean `*WriteService`
con `@WithTransaction` (ver sección Reactividad en CLAUDE.md — DIP).

```java
@WithSession
public Uni<Either<DomainError, CatResponse>> updateCat(Long id, CatUpdateRequest request, Long callerId) {
    return catRepository.findById(id)
            .onItem().transformToUni(cat -> {
                if (cat == null)
                    return Uni.createFrom().item(Either.left(new NotFoundError("CAT_NOT_FOUND")));
                if (!cat.organizationId.equals(callerId))
                    return Uni.createFrom().item(Either.left(new ForbiddenError("CAT_ACCESS_DENIED")));
                // actualizar y persistir...
                return catRepository.persist(cat)
                        .onItem().transform(saved ->
                                Either.<DomainError, CatResponse>right(mapper.toResponse(saved)));
            });
}
```

### Service — operaciones sin error de dominio posible

Si la operación no puede fallar por razones de negocio, no es necesario `Either`:

```java
@WithTransaction
public Uni<CatResponse> createCat(CatCreateRequest request, Long callerId) {
    Cat cat = mapper.toEntity(request);
    cat.organizationId = callerId;
    return catRepository.persist(cat)
            .onItem().transform(saved -> mapper.toResponse(saved, List.of()));
}
```
