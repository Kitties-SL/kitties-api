# Cumplimiento RGPD / LOPDGDD

Auditoría de protección de datos realizada el 2026-05-01.
Marco legal: Reglamento (UE) 2016/679 (RGPD) y Ley Orgánica 3/2018 (LOPDGDD).

---

## Tabla de contenidos

- [Inventario de datos personales](#inventario-de-datos-personales)
- [Problemas críticos](#problemas-críticos)
- [Problemas importantes](#problemas-importantes)
- [Problemas menores](#problemas-menores)
- [Plan de acción priorizado](#plan-de-acción-priorizado)
- [Marco legal de referencia](#marco-legal-de-referencia)

---

## Inventario de datos personales

### Datos del usuario (`users.users` — user-service)

| Campo | Tipo | Sensibilidad |
|---|---|---|
| `email` | identificador único | alta |
| `password_hash` | hash bcrypt/argon2 | alta (bien protegido) |
| `name`, `surname` | nombre real | media |
| `birthdate` | fecha de nacimiento | media |
| `activation_token` | token temporal | baja (transitorio) |

### Tokens de sesión (`auth.refresh_tokens` — auth-service)

| Campo | Tipo | Sensibilidad | Nota |
|---|---|---|---|
| `email` | duplicado del user-service | alta | innecesario, solo se necesita `user_id` |
| `role` | duplicado del user-service | baja | innecesario |
| `expires_at` | caducidad | — | tokens expirados no se purgan |

### Formulario de contrato de adopción (`adoption.adoption_forms` — adoption-service)

| Campo | Tipo | Sensibilidad |
|---|---|---|
| `full_name` | nombre legal completo | alta |
| `id_number` | **DNI / NIE** | **muy alta — almacenado en texto plano** |
| `phone` | teléfono | alta |
| `address`, `city`, `postal_code` | domicilio | alta |
| `contract_signed_at` | timestamp de firma | media |

### Formulario de screening (`adoption.adoption_request_forms` — adoption-service)

| Campo | Tipo | Sensibilidad |
|---|---|---|
| `children_ages` | edades de menores en el hogar | alta |
| `anyone_has_allergies` | indicador de salud | **muy alta (Art. 9 RGPD)** |
| `allergies_detail` | descripción de alergias | **muy alta (Art. 9 RGPD)** |
| `housing_instability_reason` | situación económica/personal | alta |
| `adults_in_household`, `has_children` | composición familiar | media |
| resto de campos | hábitos y entorno doméstico | media |

> Los campos de alergia son **datos de salud** según el Art. 9 RGPD y la LOPDGDD.
> Requieren base legal específica y consentimiento explícito separado de los T&C generales.

### Solicitud de adopción (`adoption.adoption_requests` — adoption-service)

| Campo | Tipo | Sensibilidad |
|---|---|---|
| `adopter_email` | duplicado del user-service | alta (innecesario) |
| `rejection_reason` | texto libre de la org | baja |

### Mensajes de chat (`chat.messages` — chat-service)

| Campo | Tipo | Sensibilidad |
|---|---|---|
| `content` | texto libre | variable — puede contener datos muy sensibles |

### Eventos Kafka (`adoption-form-submitted`)

El evento que adoption-service publica hacia form-analysis-service incluye:
- `adopterEmail` (identificador personal)
- todos los campos del screening form, incluidos los datos de salud

Los datos de categoría especial (alergias) viajan por el bus de mensajes sin cifrar.

### Imágenes (`minio` / Cloudflare R2 en producción)

Las URLs de imágenes de gatos (`profile_image_url`, `cat_images.url`) son públicas por diseño.
En producción se usa Cloudflare R2 (EE.UU.) — transferencia internacional cubierta por SCC de Cloudflare (verificar vigencia antes del despliegue).

### Trazas de observabilidad (Grafana Cloud)

OpenTelemetry captura atributos HTTP. Una petición a `GET /users/email@example.com` expone el email en el span. Grafana Cloud procesa estos datos fuera de la UE por defecto.

---

## Problemas críticos

Incumplimientos que la AEPD considera graves y que pueden generar sanción antes del primer dato de usuario real.

### C-1 — DNI/NIE almacenado en texto plano ✅ resuelto

**Afecta a:** `adoption.adoption_forms.id_number`
**Riesgo:** Una brecha de la base de datos expone directamente el documento de identidad de todos los adoptantes.
**Base legal:** LOPDGDD art. 9; Circular 1/2019 AEPD sobre tratamiento de datos de identificación personal.
**Solución:** Cifrado a nivel de aplicación (AES-256-GCM) antes de persistir. La clave de cifrado no debe almacenarse en la BD ni en el código — inyectarla vía variable de entorno / secreto Docker.

### C-2 — Datos de salud sin base legal específica ni consentimiento explícito ✅ resuelto

**Afecta a:** `anyone_has_allergies`, `allergies_detail` en `adoption_request_forms`
**Riesgo:** Los datos de salud son categoría especial (Art. 9 RGPD). El `acceptsTermsAndConditions` actual no es un consentimiento explícito para esta categoría.
**Solución implementada:** Campo `consent_health_data BOOLEAN NOT NULL` añadido a `adoption_forms` (V4) con consentimiento separado y explícito. El campo `consentHealthData: true` es obligatorio (`@NotNull`) al enviar el formulario de adopción. Los registros anteriores a la migración quedan con `false` y deberán re-enviarse.

### C-3 — Derecho al olvido no implementado (Art. 17 RGPD) ✅ resuelto

**Afecta a:** todos los servicios
**Riesgo:** Un usuario puede exigir legalmente el borrado de sus datos. El borrado lógico actual (`status → Inactive`) no satisface este derecho — los datos permanecen en BD indefinidamente.
**Solución pendiente de diseño:**
- `user-service`: borrado físico de la fila + revocar tokens.
- `adoption-service`: los expedientes de adopción deben conservarse para retención legal (5 años, art. 1964 CC) → los campos PII se anonimizarían irreversiblemente, preservando la estructura del expediente para la organización.
- `auth-service`: revocar y eliminar todos los refresh tokens.
- `chat-service`: anonimizar `sender_id` (conservar historial de la org).

**Opciones de implementación en evaluación:**
- *Anonimización directa*: sobreescribir campos PII con valores nulos o placeholders irreversibles en el momento del borrado.
- *Crypto-shredding*: cifrar todos los campos PII por usuario con una clave individual; el borrado consiste en destruir esa clave. Más complejo (requiere gestión de claves por usuario) pero resuelve C-3 y C-4 simultáneamente sin complicar retenciones.

**⚠ Apunte de diseño — legal hold y período de gracia (Art. 17.3.e RGPD):**
El derecho al olvido no es absoluto. Si los datos de un usuario son necesarios como prueba en un procedimiento penal o civil (abuso animal, fraude, etc.), el art. 17.3.e permite denegar la solicitud de borrado mientras dure el procedimiento. Se debe implementar un mecanismo de **legal hold**:
- Campo `legal_hold_until: TIMESTAMPTZ` (o flag booleano) en `users.users`.
- Solo activable por un administrador de la plataforma (o por integración con requerimiento judicial).
- Mientras esté activo, cualquier solicitud de borrado devuelve 409 con texto legal explícito.
- El hold expira automáticamente o se levanta manualmente al concluir el procedimiento.

**⚠ Apunte de diseño — requerimiento judicial posterior al borrado:**
Si el requerimiento llega después de que el usuario ya solicitó el borrado, la anonimización inmediata eliminaría la evidencia sin posibilidad de recuperación. Solución: **período de gracia de 30 días** entre la petición y la ejecución real:

```
usuario solicita borrado
        ↓
cuenta desactivada inmediatamente (no puede autenticarse)
datos marcados: deleted_at = now(), scheduled_purge = now() + 30 días
        ↓
durante 30 días: un legal hold puede bloquear la purga
        ↓
día 30 sin hold activo → job nocturno ejecuta la anonimización definitiva
```

Si el requerimiento llega tras ejecutarse la purga, la plataforma queda protegida porque actuó de buena fe cumpliendo el art. 17 sin notificación previa de retención. Las autoridades disponen de 30 días de margen razonable desde la solicitud del usuario. Este patrón es el estándar de facto en grandes plataformas (Google, Meta, etc.).

**⚠ Apunte de diseño — registro inmutable de solicitudes de borrado (Art. 5.2 RGPD):**
La solicitud de borrado en sí debe persistirse como registro de auditoría, independientemente de si se ejecuta o es bloqueada por un legal hold. En una investigación penal, el momento exacto en que alguien pidió borrar sus datos puede ser evidencia relevante. El registro no contiene PII — solo identificadores y timestamps, que no son datos personales una vez anonimizado el usuario:

```sql
-- esquema audit (separado de los datos de usuario)
CREATE TABLE audit.erasure_requests (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,   -- sobrevive al borrado del usuario
    requested_at        TIMESTAMPTZ  NOT NULL,
    requested_ip        INET,                    -- IP de la petición
    scheduled_purge_at  TIMESTAMPTZ  NOT NULL,
    purged_at           TIMESTAMPTZ,             -- NULL hasta ejecución
    blocked_by_hold     BOOLEAN      DEFAULT false,
    hold_lifted_at      TIMESTAMPTZ
);
```

Este registro es **append-only e inmutable** — ni el usuario ni un admin pueden eliminarlo. Sirve simultáneamente como prueba de cumplimiento ante la AEPD (accountability, art. 5.2) y como evidencia forense en caso de investigación.

### C-4 — Sin política de retención de datos (Art. 5.1.e RGPD) ✅ resuelto

**Afecta a:** todos los servicios
**Riesgo:** Los datos no se deben conservar más tiempo del necesario para la finalidad. Sin plazos definidos, cualquier dato almacenado indefinidamente es un incumplimiento.
**Plazos orientativos:**
- Solicitudes de adopción rechazadas: 1 año desde rechazo (reclamaciones y seguimiento).
- Contratos de adopción completados: 5 años (prescripción civil, art. 1964 CC).
- Mensajes de chat: 1 año desde el cierre de la conversación.
- Refresh tokens expirados/revocados: purga semanal automática.
- Tokens de activación caducados: purga diaria.

---

## Problemas importantes

Deben resolverse antes del lanzamiento a producción.

### I-1 — Datos personales en eventos Kafka ✅ resuelto

`AdoptionFormSubmittedEvent` incluye `adopterEmail` y los campos de salud del screening.
Los mensajes Kafka persisten en disco según la retención del topic y no están cifrados a nivel de mensaje.
**Solución implementada:** `adopterEmail` sustituido por `adoptionRequestId` y `adopterId` en el evento. Ambas copias del record (adoption-service y form-analysis-service) están actualizadas.

### I-2 — Email en el payload del JWT ✅ resuelto

El claim `email` viaja en cada request y puede aparecer en logs del gateway, application server y trazas OTEL.
**Solución:** Eliminar el claim `email` del JWT. Usar solo `sub` (userId). Los servicios que necesitan el email deben recuperarlo del user-service por `userId`.
> **Advertencia:** este cambio rompe `AdoptionService` (que extrae `adopterEmail` del JWT al crear la solicitud) y `UserResource.requireSelf()`. Requiere refactor coordinado.

### I-3 — `adopter_email` duplicado en `adoption_requests` ✅ resuelto

Se copia el email del usuario en `adoption_requests.adopter_email` al crear la solicitud. Si el usuario cambia su email (futura feature) o ejerce rectificación, el dato queda desincronizado.
**Solución implementada:** Columna `adopter_email` eliminada de `adoption_requests` (migración V5). Solo se persiste `adopter_id`. El resource ya no llama a user-service en tiempo de creación; el email se recupera de user-service en tiempo de lectura si el contexto lo requiere.

### I-4 — Refresh tokens expirados no se purgan ✅ resuelto

`auth.refresh_tokens` acumula filas con `revoked = true` o `expires_at` pasado. Son datos personales (contienen email) sin finalidad activa.
**Solución implementada:** `AuthTokenPurgeJob` en schedule-service dispara semanalmente (domingos 03:00) una llamada interna a auth-service que elimina los tokens expirados o revocados.

### I-5 — Sin endpoint de portabilidad (Art. 20 RGPD) ✅ resuelto

No existe mecanismo para que un usuario descargue todos sus datos en formato estructurado.
**Solución implementada:** `GET /users/me/export` (@RolesAllowed("User")) en user-service. Orquesta en paralelo (`Uni.combine`) dos llamadas internas: `adoption-service /adoptions/internal/users/{id}/export` y `chat-service /chats/internal/users/{id}/export`. Devuelve perfil + historial de adopciones (solicitudes, formularios, entrevistas, gastos) + conversaciones con mensajes en un único JSON.

### I-6 — Trazas OTEL con datos personales

Los spans de OpenTelemetry pueden incluir URLs con emails o IDs, cabeceras HTTP, y payloads si el agente está configurado con nivel de detalle alto.
**Solución:** Configurar el agente OTEL para excluir headers de autorización y sanitizar URLs con patrones de email (`/users/{email}` → `/users/{redacted}`). Añadir `quarkus.otel.traces.sampler` con filtros.

---

## Problemas menores

### M-1 — Token de activación sin TTL verificado

El campo `activation_token_expires_at` existe en la entidad `User` pero `POST /users/activate` no comprueba si el token ha caducado.
**Solución:** Añadir la comprobación `activationTokenExpiresAt.isBefore(LocalDateTime.now())` en `UserService.activate()` y devolver 410 Gone si ha expirado.

### M-2 — Sin audit log de accesos a datos sensibles

Los accesos a `adoption_forms` (DNI, dirección) y `adoption_request_forms` (datos de salud) no se registran.
La LOPDGDD recomienda trazabilidad en el tratamiento de datos sensibles.
**Solución:** Emitir un evento de auditoría (Kafka topic `audit-log` o tabla dedicada) cada vez que se lee o escribe un formulario de adopción con datos de categoría especial.

### M-3 — Cloudflare R2 como procesador en EE.UU.

En producción las imágenes se alojan en Cloudflare R2. Las imágenes de gatos no son datos personales, pero las URLs firmadas o los metadatos podrían serlo.
**Solución:** Verificar y documentar las SCC vigentes de Cloudflare antes del lanzamiento. Añadir a Cloudflare en el registro de actividades de tratamiento como encargado del tratamiento (Art. 28 RGPD).

---

## Plan de acción priorizado

| #   | Problema                                    | Servicio(s) afectado(s)       | Esfuerzo | Prioridad            |
|-----|---------------------------------------------|-------------------------------|----------|----------------------|
| C-1 | Cifrar DNI/NIE                              | adoption-service              | medio    | ✅ resuelto           |
| C-2 | Consentimiento explícito datos de salud     | adoption-service + frontend   | bajo     | ✅ resuelto (backend) |
| C-3 | Derecho al olvido (borrado/anonimización)   | user, adoption, auth, chat    | alto     | ✅ resuelto           |
| C-4 | Política de retención + jobs de purga       | auth, adoption, chat          | medio    | ✅ resuelto           |
| I-1 | Quitar datos personales del evento Kafka    | adoption, form-analysis       | bajo     | ✅ resuelto           |
| I-2 | Quitar email del JWT                        | auth + todos los consumidores | alto     | ✅ resuelto           |
| I-3 | Quitar `adopter_email` de adoption_requests | adoption-service              | medio    | ✅ resuelto           |
| I-4 | Job de purga de refresh tokens              | auth-service                  | bajo     | ✅ resuelto           |
| I-5 | Endpoint de portabilidad de datos           | user-service                  | medio    | ✅ resuelto           |
| I-6 | Sanitización de trazas OTEL                 | todos                         | bajo     | 🟠 importante        |
| M-1 | Verificar TTL del activation token          | user-service                  | bajo     | 🟡 menor             |
| M-2 | Audit log de accesos a datos sensibles      | adoption-service              | medio    | 🟡 menor             |
| M-3 | Documentar SCC de Cloudflare                | — (documentación)             | bajo     | 🟡 menor             |

---

## Marco legal de referencia

| Norma                | Artículos relevantes                                                                                                                                                                                                                               |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| RGPD                 | Art. 5 (principios), Art. 6 (bases legales), Art. 9 (datos de salud), Art. 13-14 (transparencia), Art. 15-22 (derechos), Art. 25 (privacy by design), Art. 30 (registro de actividades), Art. 32 (seguridad), Art. 33-34 (notificación de brechas) |
| LOPDGDD              | Art. 6 (consentimiento), Art. 9 (datos de menores), Art. 12-18 (derechos), Disposición adicional 17ª (DNI/NIF)                                                                                                                                     |
| Circular AEPD 1/2019 | Tratamiento del número de identificación personal                                                                                                                                                                                                  |

La autoridad de control competente en España es la **Agencia Española de Protección de Datos (AEPD)** — [aepd.es](https://www.aepd.es).
Las sanciones por incumplimiento grave (Art. 83.5 RGPD) pueden alcanzar los **20 millones de euros o el 4 % del volumen de negocio anual**.
