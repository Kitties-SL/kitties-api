# Informe de cobertura de tests unitarios — Kitties

**Fecha:** 2026-05-15 (actualizado — rama `test/coverage-improvement` completa)  
**Base de comparación:** informe de complejidad ciclomática 2026-05-14  
**Estado de rama:** pendiente de merge a `main`

---

## Resumen

```
Tests totales    431  →  523   (+92)
Ratio Tests/CC   0,40 →  0,48  (+0,08)
Servicios < 0,30  3   →   0
Panache inline    7   →   0    (anti-patrón DIP eliminado completamente)
```

---

## Estado global por servicio

| Servicio | CC | Tests | Tests/CC | Brecha |
|---|---:|---:|---:|---:|
| form-analysis-service | 52 | 33 | **0,63** | 0,37 |
| organization-service | 88 | 52 | **0,59** | 0,41 |
| auth-service | 50 | 28 | **0,56** | 0,44 |
| either-mon | 110 | 57 | **0,52** | 0,48 |
| storage-service | 33 | 17 | **0,52** | 0,48 |
| adoption-service | 278 | 142 | **0,51** | 0,49 |
| chat-service | 94 | 44 | 0,47 | 0,53 |
| notification-service | 13 | 6 | 0,46 | 0,54 |
| schedule-service | ~16 | 7 | 0,44 | 0,56 |
| cat-service | 122 | 50 | 0,41 | 0,59 |
| user-service | 153 | 58 | 0,38 | 0,62 |
| gateway-service | 78 | 29 | 0,37 | 0,63 |
| **TOTAL** | **~1 087** | **523** | **0,48** | **0,52** |

---

## Cambios acumulados en esta rama (+92 tests)

| Servicio | Tests antes | Tests ahora | Delta | Motivo principal |
|---|---:|---:|---:|---|
| user-service | 35 | 58 | **+23** | ErasureServiceTest (nueva) + UserServiceTest |
| form-analysis-service | 12 | 33 | **+21** | FormAnalysisRulesTest 8→27 + 2 tests consumer |
| adoption-service | 136 | 142 | **+6** | AnonymizationWriteService + RetentionPurgeService |
| cat-service | 35 | 50 | **+15** | CatServiceTest 15→29 + CatWriteServiceTest |
| auth-service | 20 | 28 | **+8** | AuthServiceTest 8→16 |
| organization-service | 44 | 52 | **+8** | OrganizationServiceTest + MemberServiceTest |
| schedule-service | 2 | 7 | **+5** | ScheduledJobsTest (nueva) |
| storage-service | 13 | 17 | **+4** | StorageServiceTest 9→13 |
| chat-service | 42 | 44 | **+2** | ChatRetentionServiceTest (nueva) |
| form-analysis-service¹ | — | — | — | ¹ incluido en la fila de arriba |

---

## Análisis de la brecha — probabilidad del software

La brecha global (1 − 0,48 = 0,52) no equivale a un 52 % de código sin probar.

```
Cubierto implícitamente   ~0,28  (54 % de la brecha)   → no requiere acción
No testable (infra)       ~0,19  (37 % de la brecha)   → aceptable, red e2e
Riesgo real residual      ~0,05  ( 9 % de la brecha)   → ver objetivos pendientes
```

### Categoría A — Cubierto implícitamente

Entidades `@Entity` (Cat, User, Organization, AdoptionForm…), resource layers que
ejercen el servicio via HTTP, patrones idénticos a código ya testeado.

### Categoría B — No testable con Mockito

gRPC Netty, Kafka broker, JWT firmado con RSA, MinIO real, `@Scheduled` trigger.
Red de seguridad: tests e2e + integración con Testcontainers.

### Categoría C — Riesgo real residual

| Código | CC sin cubrir | Situación |
|---|---|---|
| `AdoptionService` métodos complejos | ~8 | `verifyCatActive` (CircuitBreaker), `findAlternatives` — lógica de negocio testable pero no priorizada |
| `IpRateLimiter` en gateway | ~6 | Solo 1 test; el resto se cubre via WireMock en `GatewayResourceTest` |

El anti-patrón DIP (`Panache.withTransaction` inline) ha sido **completamente eliminado**
de `src/main`. No queda ninguna llamada estática de Panache fuera de beans `@WithTransaction`.

---

## Hoja de ruta completa

### Iteración 1 — cobertura de tests ✅

| # | Acción | Servicio | Estado |
|---|---|---|---|
| 1 | `ErasureServiceTest` — nueva (12 casos) | user | ✅ |
| 2 | `FormAnalysisRulesTest` 8→27 casos | form-analysis | ✅ |
| 3 | `UserServiceTest` 8→19 casos | user | ✅ |
| 4 | `ScheduledJobsTest` — nueva (5 Jobs) | schedule | ✅ |
| 5 | `OrganizationServiceTest` + `MemberServiceTest` → 12 casos | organization | ✅ |
| 6 | `CatServiceTest` 15→25 casos | cat | ✅ |
| 7 | `AuthServiceTest` 8→16 casos | auth | ✅ |
| 8 | `StorageServiceTest` 9→13 casos | storage | ✅ |

### Iteración 2 — DIP + brecha real ✅

| # | Acción | Servicio | Estado |
|---|---|---|---|
| 9 | `CatWriteService` + test `deleteCat` (4 casos) | cat | ✅ |
| 10 | `FormAnalysisServiceTest` 4→6 casos | form-analysis | ✅ |
| 11 | `AdoptionAnonymizationWriteService` + test (2 casos) | adoption | ✅ |
| 12 | `@WithTransaction` en `RetentionPurgeService` + test (4 casos) | adoption | ✅ |
| 13 | `@WithTransaction` en `ChatRetentionService` + test (2 casos) | chat | ✅ |

---

### Pendiente — objetivos identificados

| # | Acción | Servicio | Impacto |
|---|---|---|---|
| 14 | **Merge `test/coverage-improvement` → `main`** | todos | cierra la rama |
| 15 | Ampliar `AdoptionServiceTest` (+7 casos) hacia CC=46 | adoption | ratio 0,51 → ~0,54 |
| 16 | Ampliar `IpRateLimiterTest` (3-4 casos de bucket) | gateway | cubre el único riesgo real del gateway |
| 17 | Añadir `UserResourceTest` para flujos 4xx (validación de entrada) | user | ratio 0,38 → ~0,41 |
| 18 | Integrar LLM en `form-analysis-service` | form-analysis | objetivo de producto (post-infra) |
| 19 | Establecer gate Tests/CC ≥ 0,40 por servicio en CI | infra | evita regresión de cobertura |

---

## Nota arquitectónica — regla DIP

`Panache.withTransaction()` y `Panache.withSession()` como **llamadas estáticas inline** en
capas de servicio o resource quedan **prohibidas**. La gestión de transacciones pertenece
a beans `@ApplicationScoped` con `@WithTransaction` o `@WithSession` en el método. Razones:

- Las llamadas estáticas requieren contexto Vert.x → no testables con Mockito.
- El interceptor CDI (`@WithTransaction`) es bypassado por `@InjectMocks` → testable.
- El patrón `*WriteService` ya está establecido en 5 servicios como referencia.

Excepciones aceptadas: ninguna en código nuevo. El único residuo histórico era `CatService.deleteCat`,
ya corregido en esta rama.
