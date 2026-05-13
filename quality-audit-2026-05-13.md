# Informe de calidad global — Kitties (2026-05-13)

Auditoría de los 11 servicios + either-mon. Foco: patrones de dominio, seguridad, reactividad y consistencia.

---

## CRÍTICO ✅ (resueltos en fix/quality-audit)

### 1. ✅ `Either.right(null)` en AdoptionService
**Fichero:** `adoption-service/src/main/java/es/kitti/adoption/service/AdoptionService.java` ~línea 122  
**Problema:** `Either.<DomainError, Void>right(null)` en el happy path de `updateStatus`.  
**Fix:** Reemplazar por `Either.unit()`.

### 2. ✅ `Uni.combine()` con 4 queries `@WithSession` en paralelo
**Fichero:** `adoption-service/src/main/java/es/kitti/adoption/service/AdoptionService.java` ~línea 277 (`exportByAdopterId`)  
**Problema:** `@WithSession` + `Uni.combine().all().unis(...)` con cuatro repositorios en paralelo. Hibernate Reactive no permite abrir dos sesiones simultáneamente en el mismo contexto Vert.x. En tests no se aprecia porque Mockito bypassa la gestión de sesiones.  
**Fix:** Encadenar con `transformToUni` en lugar de combinar en paralelo (patrón estándar del resto del proyecto).

---

## ALTO ✅ (resueltos en fix/quality-audit)

### 3. ✅ `StorageResource` sin anotaciones de seguridad
**Ficheros:**
- `storage-service/src/main/java/es/kitti/storage/resource/StorageResource.java` — `POST /storage/upload` y `DELETE /storage/{key}` sin `@Authenticated`/`@RolesAllowed`
- `storage-service/src/main/java/es/kitti/storage/resource/FileResource.java` — `GET /storage/files/{key}` es intencionalmente público (documentado en CLAUDE.md), pero la clase no tiene `@PermitAll` explícito

**Fix:** Añadir `@Authenticated` + `@RolesAllowed` apropiados en `StorageResource`; `@PermitAll` en `FileResource`.

### 4. ✅ `@Incoming` con `Panache.withTransaction()` directo en `AdoptionService`
**Fichero:** `adoption-service/src/main/java/es/kitti/adoption/service/AdoptionService.java` ~línea 294 (`onFormAnalysed`)  
**Problema:** El método `@Incoming` contiene `Panache.withTransaction()` inline. El gotcha documentado en CLAUDE.md indica que `@Incoming` + `@WithTransaction` combinados directamente pueden fallar; la lógica de persistencia debe delegarse a un bean separado con `@WithTransaction` en su método.  
**Fix:** Extraer la lógica transaccional a un método `@ApplicationScoped` + `@WithTransaction`.

---

## MEDIO ✅ (resueltos en fix/quality-audit)

### 5. ✅ `new ErrorResponse(500, ...)` directo en los 7 `GlobalExceptionMapper`
**Ficheros:** `GlobalExceptionMapper.java` en adoption, user, chat, organization, auth, cat, storage.  
**Problema:** El `default` case usa el constructor directo (`new ErrorResponse(500, "INTERNAL_SERVER_ERROR", null, LocalDateTime.now())`) mientras que los otros casos (422, 403) usan el factory `ErrorResponse.of(error)`.  
**Fix:** Añadir `ErrorResponse.internalError()` en either-mon y usarlo en los 7 mappers.

### 6. ✅ `ChatResource.blockUser` — null-coalescing antipatrón
**Fichero:** `chat-service/src/main/java/es/kitti/chat/resource/ChatResource.java` ~línea 70  
**Problema:** `BlockUserRequest req = request != null ? request : new BlockUserRequest(null)` — crea un objeto con `null` en lugar de rechazar el request vacío. JAX-RS devolvería 400 automáticamente sin este fallback.  
**Fix:** Eliminar la asignación; usar `request` directamente.

---

## BAJO

### 7. ⏳ 10+ endpoints de colección sin paginación (deuda conocida, pendiente)
Afecta: adoption × 4 (`/adoptions/my`, `/adoptions/organization`, `/adoptions/organization/cats/{catId}`, `/intake-requests/mine`, `/intake-requests/organization`), cat (`/cats/mine`), chat × 2 (`/chats/mine`, `/chats/organization`), user-internal (`/users/internal/active`), organization-internal (`/organizations/internal/by-region/{region}`).

### 8. ✅ Import `@Blocking` sin usar
**Fichero:** `form-analysis-service/src/main/java/es/kitti/formanalysis/service/FormAnalysisService.java` línea 6  
**Fix:** Eliminar `import io.smallrye.common.annotation.Blocking;`.

---

## Validado — sin problemas

| Aspecto | Estado |
|---|---|
| DTOs como Java Records | ✓ todos |
| Enums en PascalCase | ✓ todos |
| `@Id` explícito en entidades | ✓ ninguno |
| Active Record fuera de contexto | ✓ ninguno |
| `.await().indefinitely()` fuera de tests | ✓ ninguno |
| Fire-and-forget sin `onFailure` | ✓ ninguno |
| HQL con concatenación de strings | ✓ ninguno |
| Secretos hardcodeados | ✓ ninguno |
| TODOs/FIXMEs en código | ✓ ninguno |
| Nombrado inconsistente | ✓ ninguno |
| `Multi<T>` con `@WithSession` en método | ✓ ninguno |
| Endpoints `@InternalOnly` correctos | ✓ todos los que aplican |

---

## Priorización sugerida

| # | Hallazgo | Esfuerzo | Riesgo si no se arregla |
|---|---|---|---|
| 1 | `Either.right(null)` | 5 min | Bug semántico latente |
| 2 | `Uni.combine()` + `@WithSession` en adoption | 30 min | 500 en producción con carga real |
| 3 | StorageResource sin auth | 20 min | Upload/delete sin autenticación |
| 4 | `@Incoming` + withTransaction inline | 30 min | Fallo silencioso bajo concurrencia |
| 5 | ErrorResponse factory inconsistency | 45 min (7 ficheros) | Cosmético / DRY |
| 6 | ChatResource null-coalescing | 5 min | Comportamiento confuso |
| 7 | Paginación (deuda conocida) | días | OOM en producción |
| 8 | Import unused | 2 min | Ruido |
