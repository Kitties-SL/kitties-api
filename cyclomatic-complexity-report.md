# Informe de complejidad ciclomática — Kitties

**Fecha:** 2026-05-14  
**Método:** McCabe (M = D + 1 por método; M_total = Σ D_i + N_métodos)  
**Alcance:** `src/main/java` de todos los módulos, excluyendo `target/`

---

## Qué cuenta como punto de decisión (D)

| Construcción | Categoría |
|---|---|
| `if` / `else if` | control explícito |
| ternario `? :` | control explícito |
| `case ->` en switch expression | control explícito |
| `&&` / `\|\|` | lógica booleana compuesta |
| `for` / `while` | bucle |
| `catch` | manejo de excepción |
| `.fold()` | bifurcación Left / Right (Either) |
| `.flatMap()` sobre Either | cortocircuito si Left, transforma si Right |
| `.onFailure()` | rama de error condicional (Mutiny) |
| `recoverWith*` | fallback condicional (Mutiny) |
| `.filter()` | predicado en stream / Mutiny |
| `instanceof` / pattern matching | chequeo de tipo condicional |
| `anyMatch` / `allMatch` / `noneMatch` | predicado de colección |
| `orElse` / `orElseGet` / `orElseThrow` | valor alternativo condicional (Optional) |

Los patrones funcionales y reactivos no llevan la palabra `if`, pero crean
exactamente el mismo número de caminos independientes de ejecución.

> **Nota sobre `.map()` en Either:** `map` también cortocircuita en Left, pero su semántica
> es una transformación de pipeline, no una decisión de control. Se excluye del conteo por
> convención — el punto de decisión real ya fue capturado por quien construyó el Left/Right.
> `flatMap` sí se incluye porque el resultado de la función aplicada puede ser a su vez Left o Right.

---

## Resumen por servicio

| Servicio | Métodos | Tests `@Test` | D (decisiones) | CC total | CC medio/método | Tests/CC |
|---|---:|---:|---:|---:|---:|---:|
| adoption-service | 173 | 136 | 105 | 278 | 1,6 | 0,49 |
| user-service | 87 | 35 | 66 | 153 | 1,8 | **0,23** |
| cat-service | 74 | 35 | 48 | 122 | 1,6 | 0,29 |
| either-mon | 43 | 57 | 67 | 110 | 2,6 | **0,52** |
| chat-service | 58 | 42 | 36 | 94 | 1,6 | 0,45 |
| organization-service | 49 | 44 | 39 | 88 | 1,8 | 0,50 |
| gateway-service | 33 | 29 | 45 | 78 | 2,4 | 0,37 |
| form-analysis-service | 15 | 12 | 37 | 52 | 3,5 | **0,23** |
| auth-service | 36 | 20 | 14 | 50 | 1,4 | 0,40 |
| storage-service | 13 | 13 | 20 | 33 | 2,5 | 0,39 |
| notification-service | 7 | 6 | 6 | 13 | 1,9 | 0,46 |
| schedule-service | ~15 | 2 | 1 | ~16 | ~1,1 | **0,13** |
| **TOTAL** | **~603** | **~431** | **~484** | **~1 087** | **~1,8** | **~0,40** |

> `Tests/CC` es el ratio tests-por-unidad-de-complejidad. Cuanto más cercano a 1, mejor cubierto está el servicio relativo a su complejidad. No equivale a porcentaje de cobertura de líneas.

### Lecturas del ratio Tests/CC

- **≥ 0,5** — buena densidad de tests respecto a la complejidad (adoption, either-mon, organization)
- **0,3–0,5** — aceptable, hay recorrido de mejora
- **< 0,3** — baja cobertura relativa: user-service (0,23), form-analysis-service (0,23), schedule-service (0,13)

### Operadores adicionales incorporados (+24 D respecto al análisis inicial)

| Operador | Total src/main | Concentración |
|---|---:|---|
| `instanceof` / pattern matching | 14 | either-mon (9), gateway+user+storage (5) |
| `.flatMap()` sobre Either | 9 | adoption-service (8), user-service (1) |
| `orElse` / `orElseGet` / `orElseThrow` | 1 | form-analysis-service |
| `anyMatch` / `allMatch` / `noneMatch` | 0 | solo aparecen en tests, no en src/main |

Los 9 `instanceof` de either-mon incluyen 4 implementaciones de predicados (`isRight`, `isLeft`,
`isSuccess`, `isFailure`): son decisiones reales aunque su semántica es exponer el estado del
tipo sellado. Los `flatMap` sobre Either son cortocircuitos explícitos que crean dos caminos
de ejecución distintos, igual que un `if (isLeft) return left`.

---

## Top 25 clases por complejidad ciclomática

La columna **Tests** indica cuántos `@Test` tiene la clase de test directamente asociada.
`—` significa que no existe clase de test dedicada.

| # | Clase | Servicio | CC | D | Métodos | Test class | Tests | Estado |
|---|---|---|---:|---:|---:|---|---:|---|
| 1 | `AdoptionService` | adoption | 46 | 26 | 20 | `AdoptionServiceTest` | 23 | ⚠ parcial |
| 2 | `FormAnalysisRules` | form-analysis | 43 | 37 | 6 | `FormAnalysisRulesTest` | 8 | 🔴 bajo |
| 3 | `AdoptionRequestForm` | adoption | 35 | 0 | 35 | — | — | entidad¹ |
| 4 | `CatService` | cat | 33 | 22 | 11 | `CatServiceTest` | 15 | ⚠ parcial |
| 5 | `ConstraintViolationMapper` | either-mon | 30 | 21 | 9 | `ConstraintViolationMapperTest` | 11 | ⚠ parcial |
| 6 | `ChatService` | chat | 30 | 18 | 12 | `ChatServiceTest` | 17 | ⚠ parcial |
| 7 | `AdoptionWriteService` | adoption | 25 | 17 | 8 | `AdoptionWriteServiceTest` | 14 | ✓ |
| 8 | `ProxyService` | gateway | 22 | 19 | 3 | `GatewayResourceTest` | 27 | ✓ |
| 9 | `ErasureService` | user | 22 | 15 | 7 | — | — | 🔴 sin test |
| 10 | `GatewayResource` | gateway | 21 | 8 | 13 | `GatewayResourceTest` | 27 | ✓ |
| 11 | `UserResource` | user | 21 | 11 | 10 | `UserResourceTest` | 10 | ⚠ parcial |
| 12 | `OrganizationService` | organization | 20 | 15 | 5 | `OrganizationServiceTest` | 7 | ⚠ parcial |
| 13 | `OrganizationMemberService` | organization | 20 | 11 | 9 | `OrganizationMemberServiceTest` | 9 | ⚠ parcial |
| 14 | `IntakeRequestService` | adoption | 20 | 11 | 9 | `IntakeRequestServiceTest` | 9 | ⚠ parcial |
| 15 | `UserService` | user | 20 | 10 | 10 | `UserServiceTest` | 8 | ⚠ parcial |
| 16 | `AdoptionForm` | adoption | 20 | 0 | 20 | — | — | entidad¹ |
| 17 | `IdNumber` | adoption | 18 | 6 | 12 | `IdNumberTest` | 8 | ⚠ parcial |
| 18 | `AdoptionResource` | adoption | 18 | 6 | 12 | `AdoptionResourceTest` | 26 | ✓ |
| 19 | `Organization` | organization | 18 | 1 | 17 | — | — | entidad¹ |
| 20 | `Cat` | cat | 18 | 0 | 18 | — | — | entidad¹ |
| 21 | `OrganizationResource` | organization | 17 | 7 | 10 | `OrganizationResourceTest` | 23 | ✓ |
| 22 | `Validation` | either-mon | 17 | 16 | 1 | `ValidationTest` | 20 | ✓ |
| 23 | `User` | user | 17 | 0 | 17 | — | — | entidad¹ |
| 24 | `StorageService` | storage | 16 | 8 | 8 | `StorageServiceTest` | 9 | ⚠ parcial |
| 25 | `CatResource` | cat | 16 | 5 | 11 | `CatResourceTest` | 17 | ✓ |

> ¹ Las entidades (`@Entity`) acumulan CC alto por la cantidad de métodos de acceso y ciclo de vida
> (`@PrePersist`, getters implícitos de record-style, etc.), no por lógica de negocio. Son de baja
> prioridad para tests unitarios.

---

## Gaps críticos — dónde invertir primero

Ordenados por impacto esperado (CC × ausencia de cobertura):

### 🔴 ErasureService — CC 22, sin ningún test

**Servicio:** `user-service`  
**Riesgo:** Es el servicio que ejecuta la purga GDPR (derecho al olvido). Con 15 puntos de
decisión y 7 métodos, ningún camino está validado automáticamente. Un fallo silencioso aquí
tiene implicaciones legales.  
**Acción:** crear `ErasureServiceTest` con Mockito; cubrir al menos los caminos de usuario
activo, usuario con datos en borrado pendiente y usuario ya procesado.

### 🔴 FormAnalysisRules — CC 43, solo 8 tests

**Servicio:** `form-analysis-service`  
**Riesgo:** Es la clase con mayor densidad de decisiones del proyecto (D=37 en solo 6 métodos,
media de 6,2 decisiones/método). Las 8 pruebas actuales apenas cubren los caminos felices.
Las reglas de análisis de formularios son el núcleo del scoring de solicitudes; un bug aquí
afecta directamente qué solicitudes se aprueban.  
**Acción:** ampliar `FormAnalysisRulesTest` hasta ~20 tests cubriendo combinaciones de
criterios (edad, experiencia, vivienda, etc.) y valores límite.

### ⚠ schedule-service — CC ~16, solo 2 tests

**Servicio:** `schedule-service`  
**Riesgo:** Los jobs nightly son todos delegación a clientes con `@Retry`, por lo que el riesgo
unitario es bajo. Los 2 tests existentes (`UserInternalClientRetryTest`) verifican el retry.
Sin embargo, no hay ningún test de los propios `Job` que verifique que disparan los métodos
correctos del cliente.  
**Acción:** 1 test por Job (5 clases) con Mockito para verificar que el método del cliente
se invoca exactamente una vez por ejecución del scheduler.

### ⚠ user-service — ratio 0,23 (más bajo de los servicios complejos)

**CC total:** 150 | **Tests:** 35  
Las clases con más carencia relativa son:
- `ErasureService` (ya listada arriba)
- `UserService` (CC=20, solo 8 tests): los flujos de activación, reactivación y cambio de
  contraseña no están cubiertos
- `UserResource` (CC=21, 10 tests): faltan casos de validación de entrada y respuestas 4xx

### ⚠ form-analysis-service — ratio 0,24

Además de `FormAnalysisRules`, `FormAnalysisService` (consumer Kafka) tiene 4 tests pero
ninguno cubre el camino de evento mal formado con DLQ activa en un contexto de integración.

---

## Clases bien cubiertas — referencia de buenas prácticas

| Clase | CC | Tests | Ratio |
|---|---:|---:|---:|
| `Validation` (either-mon) | 17 | 20 | 1,2 |
| `AdoptionResource` | 18 | 26 | 1,4 |
| `OrganizationResource` | 17 | 23 | 1,4 |
| `GatewayResource` + `ProxyService` | 21 + 22 | 27 | 0,6 |
| `AdoptionWriteService` | 25 | 14 | 0,6 |

Estas clases tienen tests que superan (o se acercan a) su complejidad ciclomática.
`Validation` es el mejor ejemplo: 20 tests para CC=17, con casos parametrizados que cubren
combinaciones de errores.

---

## Índices de referencia McCabe

| Rango CC por método | Interpretación | Tests mínimos recomendados |
|---|---|---:|
| 1–5 | Simple | 1–2 |
| 6–10 | Moderado | ≥ 5 |
| 11–20 | Complejo | ≥ 10 (uno por rama principal) |
| > 20 | Muy complejo — considerar refactorizar | tantos como ramas independientes |

La media del proyecto es **~1,8 por método**, que está en el rango «simple». El problema no
es la complejidad media sino la distribución: unas pocas clases concentran muchas ramas y
tienen cobertura desproporcionadamente baja.

---

## Hoja de ruta de mejora sugerida

| Prioridad | Acción | Impacto esperado |
|---|---|---|
| 1 | Crear `ErasureServiceTest` (user-service) | Cubre un riesgo legal real |
| 2 | Ampliar `FormAnalysisRulesTest` a ~20 casos | Mayor confianza en el scoring |
| 3 | Añadir tests a `UserService` (activación, cambio de pwd) | Cierra ratio user-service |
| 4 | 1 test por Job en schedule-service | Verifica el cableado de los crons |
| 5 | Ampliar `OrganizationServiceTest` a ≥ 12 casos | Cierra ratio org-service |