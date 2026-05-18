# Kitties — Documentación de Negocio y API para Frontend

**Versión:** 2.3.0  
**Fecha:** 2026-05-18  
**Estado:** API funcional en dev. Swagger UI en `http://localhost:8080/swagger-ui`

---

## Tabla de Contenidos

1. [Visión General del Negocio](#visión-general-del-negocio)
2. [Roles y Permisos](#roles-y-permisos)
3. [Autenticación JWT](#autenticación-jwt)
4. [Flujos Principales](#flujos-principales)
- [Flujo A: Registro de Usuario](#flujo-a-registro-y-activación)
- [Flujo C: Registro de Organización](#flujo-c-registro-de-organización)
5. [Motor de Análisis Automático](#motor-de-análisis-automático)
6. [Referencia de Endpoints](#referencia-de-endpoints)
7. [Estados y Transiciones](#estados-y-transiciones)
8. [Eventos Asíncronos (Kafka)](#eventos-asíncronos-kafka)
9. [Configuración y Entorno](#configuración-y-entorno)

---

## Visión General del Negocio

**Kitties** conecta adoptantes con refugios de gatos (protectoras/organizaciones).

- **Usuarios (Adoptantes):** buscan y solicitan adoptar gatos
- **Organizaciones (Refugios):** publican gatos, gestionan solicitudes y reciben gatos cedidos
- **Sistema:** analiza automáticamente candidatos con reglas de negocio + LLM semántico

### Características Principales

1. **Catálogo público paginado:** búsqueda por ciudad y nombre
2. **Proceso de adopción multietapa:** solicitud → cuestionario → análisis automático → decisión → firma legal
3. **Análisis automático con IA:** 14 reglas de negocio + análisis LLM de texto libre (5 campos)
4. **Intake de gatos:** usuarios pueden ceder sus gatos a organizaciones
5. **Chat integrado:** mensajería entre adoptante y organización por adopción
6. **Gestión de organizaciones:** membresía, roles, límites por plan
7. **GDPR:** exportación y borrado de datos personales
8. **Autenticación JWT:** access token 15 min + refresh token 7 días

---

## Roles y Permisos

| Rol | Descripción | Permisos |
|-----|-------------|----------|
| **Anónimo** | No autenticado | Ver catálogo, ver detalle gato, registrarse |
| **User** | Adoptante | Todo lo anterior + crear adopción, rellenar cuestionarios, ver mis solicitudes, chat con org, ceder gatos (intake), GDPR |
| **Organization** | Refugio | Publicar y gestionar gatos, ver y procesar solicitudes, agendar entrevistas, aceptar/rechazar solicitudes e intakes, chat con adoptantes, bloquear usuarios |
| **Admin** | Administrador | Todo lo anterior + gestión global de usuarios, legal-hold GDPR |

### Planes de Organización

| Plan | Max Miembros |
|------|-------------|
| **Free** | 1 (solo el fundador) |
| **Basic** | 5 |
| **Pro** | Sin límite |

---

## Autenticación JWT

### Flujo

```
POST /auth/login {email, password}
→ 200 { accessToken, refreshToken, expiresIn: 900 }

Cada request: Header "Authorization: Bearer {accessToken}"

POST /auth/refresh {refreshToken}
→ 200 { accessToken, refreshToken, expiresIn: 900 }
  (el refresh anterior queda revocado)

POST /auth/logout {refreshToken}
→ 204 No Content
```

### Claims del JWT

**Usuario adoptante (`User`):**
```json
{
"sub": "42",
"groups": ["User"],
"iss": "https://www.kitti.es",
"exp": 1234567890
}
```

**Administrador de refugio (`Organization`):**
```json
{
"sub": "17",
"groups": ["Organization"],
"organizationId": 5,
"memberRole": "Admin",
"iss": "https://www.kitti.es",
"exp": 1234567890
}
```

> - `sub` es siempre el `userId` (nunca el `organizationId`)
> - `organizationId` y `memberRole` solo presentes para rol `Organization`
> - `memberRole`: `"Admin"` (gestión completa de la org) | `"Staff"` (operaciones)
> - Los claims están firmados con RS256 — no pueden modificarse sin invalidar la firma

---

## Flujos Principales

### Flujo A: Registro y Activación

1. `POST /users` → 201, status=`Pending`
2. Sistema envía email con token → frontend redirige a `/activate?token=...`
3. `POST /users/activate { token }` → 200, status=`Active`
4. Ya puede hacer login

```json
// POST /users
{
"email": "juan@ejemplo.com",
"password": "MySecure123!",
"name": "Juan",
"surname": "García"
}
```

> El campo `role` ya no se acepta en el registro — el backend siempre asigna `User`. Para registrar un refugio, usar el **Flujo C: Registro de Organización**.

---

### Flujo B: Login y Gestión de Sesión

```json
// POST /auth/login
{ "email": "juan@ejemplo.com", "password": "MySecure123!" }

// Respuesta
{
"accessToken": "eyJhbGciOiJSUzI1NiJ9...",
"refreshToken": "550e8400-e29b-41d4-a716-446655440000",
"expiresIn": 900
}
```

- Almacenar ambos tokens (localStorage / sessionStorage)
- Renovar `accessToken` ≈ 1 min antes de que expire (900s = 15 min)
- En logout: `POST /auth/logout { refreshToken }` → 204

---

### Flujo C: Registro de Organización

Endpoint público que crea la organización y su usuario admin en una sola llamada. No requiere autenticación previa.

```json
// POST /organizations/register
{
"name": "Refugio Gatuno Madrid",
"description": "Somos un refugio...",
"address": "Calle Principal 123",
"city": "Madrid",
"region": "Comunidad de Madrid",
"country": "España",
"phone": "+34 912 345 678",
"email": "info@refugio.es",
"logoUrl": null,
"adminEmail": "admin@refugio.es",
"adminPassword": "Secure123!",
"adminName": "Ana",
"adminSurname": "Gómez",
"adminBirthdate": "1985-03-15"
}
```

**Respuestas:**

| Código | Situación |
|--------|-----------|
| `201` | Org creada, admin creado con rol `Organization`, email de activación enviado |
| `409 ADMIN_EMAIL_ALREADY_EXISTS` | El email del admin ya está en uso — usar otro |
| `503 USER_SERVICE_UNAVAILABLE` | Error temporal — reintentar en unos minutos |
| `422` | Datos inválidos (`name`, `adminEmail`, `adminPassword` ≥ 8 chars, `adminName`, `adminSurname` son obligatorios) |

> Tras el `201`, el admin recibe un **email de activación**. Debe activar la cuenta antes de poder hacer login. Usar el Flujo A con el token recibido.

**Lo que hace el backend (informativo):**
```
1. Comprueba que adminEmail no existe → 409 si ya existe (nada creado)
2. Crea organización (status=Pending)
3. Crea usuario admin (rol=Organization, status=Pending)
4. Vincula usuario como miembro Admin de la org
5. Org pasa a status=Active → 201
```

**Gestión de miembros (una vez logueado como Organization):**

```json
// Añadir miembro: POST /organizations/{id}/members
{ "userId": 99, "role": "Staff" }
// → 201; el miembro queda inmediatamente como Active (no hay paso de aceptación en v1)

// Cambiar rol: PATCH /organizations/{id}/members/99/role
{ "role": "Admin" }

// Eliminar: DELETE /organizations/{id}/members/99 → 204
```

> **MemberRole:** `Admin` | `Staff`

---

### Flujo D: Publicar Gato con Imágenes

```json
// POST /cats (rol: Organization)
{
"name": "Misu",
"age": 3,
"sex": "Female",
"description": "Gata tranquila y cariñosa",
"neutered": true,
"city": "Madrid",
"region": "Comunidad de Madrid",
"country": "España",
"latitude": 40.4168,
"longitude": -3.7038
}
// → 201 { id, organizationId, status: "Available", images: [], ... }

// Subir imágenes: POST /cats/{id}/images
// Body: multipart/form-data, campo "file"
// Formatos: JPEG, PNG, WebP — máximo 5 MB por imagen
```

La `organizationId` se infiere del JWT — no se envía en el body.

---

### Flujo E: Proceso de Adopción Completo

```
PASO 1: Buscar gatos disponibles (público)
GET /cats?city=Madrid&page=0&size=20
→ PageResponse { content: [...], page, size, total, totalPages }

PASO 2: Crear solicitud (rol: User)
POST /adoptions { catId: 1, organizationId: 42 }
→ 201, status = Pending
→ 409 si el gato ya tiene una solicitud activa

PASO 3: Rellenar cuestionario pre-adopción (rol: User)
POST /adoptions/{id}/form { 31 campos — ver referencia completa abajo }
→ 201, status pasa automáticamente a Reviewing
→ Kafka emite adoption-form-submitted

PASO 4: Análisis automático (asíncrono)
→ Motor de reglas evalúa 14 flags (Critical/Warning/Notice)
→ LLM analiza 5 campos de texto libre (semántico)
→ Kafka emite adoption-form-analysed con decisión

PASO 5: Adopción-service procesa decisión
→ Approved / ReviewRequired → la organización revisa
→ Rejected → email al usuario con razón, no intervención org

PASO 6: Organización decide (rol: Organization)
PATCH /adoptions/{id}/status { "status": "Accepted" }
→ o "Rejected" con razón

PASO 7: Organización agenda entrevista (opcional)
POST /adoptions/{id}/interview
{ "scheduledAt": "2026-06-01T10:00:00", "notes": "Traer DNI" }

PASO 8: Usuario firma contrato legal
POST /adoptions/{id}/adoption-form { 12 campos — ver referencia abajo }
→ 201, status = FormCompleted
```

#### Body completo del cuestionario (PASO 3)

```json
{
"hasPreviousCatExperience": true,
"previousPetsHistory": "Tuve dos gatos, murieron de vejez",
"adultsInHousehold": 2,
"hasChildren": false,
"childrenAges": null,
"hasOtherPets": false,
"otherPetsDescription": null,
"hoursAlonePerDay": 6,
"stableHousing": true,
"housingInstabilityReason": null,
"housingType": "Apartment",
"housingSize": 80,
"hasOutdoorAccess": false,
"isRental": false,
"rentalPetsAllowed": null,
"hasWindowsWithView": true,
"hasVerticalSpace": true,
"hasHidingSpots": true,
"householdActivityLevel": "Moderate",
"whyCatsNeedToPlay": "Para su estimulación mental y salud física",
"dailyPlayMinutes": 30,
"plannedEnrichment": "Torres, juguetes interactivos, rascadores",
"reactionToUnwantedBehavior": "Redirigir con juguetes, nunca castigar",
"hasScratchingPost": true,
"willingToEnrichEnvironment": true,
"motivationToAdopt": "Dar un hogar a un gato que lo necesita",
"understandsLongTermCommitment": true,
"hasVetBudget": true,
"allHouseholdMembersAgree": true,
"anyoneHasAllergies": false,
"allergiesDetail": null
}
```

> **Enums:** `housingType` → `Apartment | House | Studio | Other`; `householdActivityLevel` → `Quiet | Moderate | VeryActive`

#### Body del contrato legal (PASO 8)

```json
{
"fullName": "Juan García García",
"idNumber": "12345678A",
"phone": "+34 612 345 678",
"address": "Calle Principal 123, 4B",
"city": "Madrid",
"postalCode": "28001",
"acceptsVetVisits": true,
"acceptsFollowUpContact": true,
"acceptsReturnIfNeeded": true,
"acceptsTermsAndConditions": true,
"consentHealthData": true,
"additionalNotes": null
}
```

---

### Flujo F: Intake de Gatos (Ceder a Protectora)

Un usuario que no puede seguir con su gato lo cede a una organización.

```
PASO 1: Usuario envía solicitud de ingreso (rol: User)
POST /intake-requests
{
 "targetOrganizationId": 1,
 "catName": "Michi",
 "catAge": 3,
 "region": "Comunidad de Madrid",
 "city": "Madrid",
 "vaccinated": true,
 "description": "Gato rescatado, muy tranquilo"
}
→ 201, status = Pending

PASO 2: Organización revisa (rol: Organization)
GET /intake-requests/organization
GET /intake-requests/organization/stats
→ estadísticas: { pending, approved, rejected }

PASO 3a: Aprobar
PATCH /intake-requests/{id}/approve → 200, status = Approved

PASO 3b: Rechazar
PATCH /intake-requests/{id}/reject
{ "reason": "No disponemos de espacio actualmente" }
→ 200, status = Rejected

PASO 4: Usuario puede ver sus solicitudes
GET /intake-requests/mine
```

---

### Flujo G: Chat entre Adoptante y Organización

El chat se crea automáticamente al aprobar un intake. Una vez creado:

```
Ver conversaciones (User):
GET /chats/mine → [{ id, userId, organizationId, createdAt, lastMessageAt }]

Ver conversaciones (Organization):
GET /chats/organization

Leer mensajes:
GET /chats/{id}/messages
→ [{ id, senderId, senderType: "User"|"Organization", content, createdAt }]

Enviar mensaje:
POST /chats/{id}/messages { "content": "Hola, tengo una pregunta sobre Luna" }
→ 201

Bloquear usuario (rol: Organization):
POST /chats/{id}/block { "reason": "Comportamiento inapropiado" }
DELETE /chats/{id}/block  (desbloquear)
```

---

## Motor de Análisis Automático

El `form-analysis-service` procesa el cuestionario en dos capas complementarias:

### Capa 1: Reglas de Negocio (14 flags)

#### CRITICAL — 1 flag → Rechazo inmediato

| Flag | Condición |
|------|-----------|
| `PHYSICAL_PUNISHMENT` | `reactionToUnwantedBehavior` contiene: pegar, golpe, castigo físico, bofetada, palo, hit, smack, beat |
| `ABANDONMENT_HISTORY` | `previousPetsHistory` contiene: abandoné, tiré, solté, dejé en la calle |
| `RENTAL_NO_PERMISSION` | `isRental=true` AND `rentalPetsAllowed` no es `true` |
| `ALLERGY_CONFIRMED` | `anyoneHasAllergies=true` |

#### WARNING — 3+ flags → Rechazo; 1-2 flags → Revisión requerida

| Flag | Condición |
|------|-----------|
| `INSUFFICIENT_PLAY_TIME` | `dailyPlayMinutes < 15` |
| `TOO_MANY_HOURS_ALONE` | `hoursAlonePerDay > 10` AND `hasOtherPets=false` |
| `NO_ENRICHMENT_SPACE` | `hasVerticalSpace=false` AND `hasHidingSpots=false` |
| `YOUNG_CHILDREN_NO_EXPERIENCE` | `hasChildren=true` AND `hasPreviousCatExperience=false` AND `childrenAges` contiene 0-3 |
| `UNSTABLE_HOUSING` | `stableHousing=false` |
| `SUPERFICIAL_MOTIVATION` | `motivationToAdopt` contiene: es bonito, de regalo, para los niños, capricho, me parece gracioso |

#### NOTICE — Informativos, no afectan decisión

| Flag | Condición |
|------|-----------|
| `NO_WINDOW_VIEW` | `hasWindowsWithView=false` |
| `SMALL_HOUSING` | `housingSize < 40` |
| `NO_PREVIOUS_EXPERIENCE` | `hasPreviousCatExperience=false` |
| `NO_SCRATCHING_POST` | `hasScratchingPost=false` |

---

### Capa 2: Análisis Semántico LLM

Analiza los 5 campos de texto libre buscando señales de alerta que las palabras clave no detectarían (parafraseos, eufemismos, evasión):

- `previousPetsHistory`
- `reactionToUnwantedBehavior`
- `motivationToAdopt`
- `whyCatsNeedToPlay`
- `plannedEnrichment`

**Flags generados por el LLM (nunca Critical — el LLM solo puede añadir Warning/Notice):**

| Flag | Severidad | Descripción |
|------|-----------|-------------|
| `LLM_PUNISHMENT_RISK` HIGH | Warning | Indicios claros de castigo físico en el texto |
| `LLM_PUNISHMENT_RISK` LOW | Notice | Señal ambigua relacionada con castigo |
| `LLM_ABANDONMENT_RISK` HIGH | Warning | Indicios claros de abandono previo |
| `LLM_ABANDONMENT_RISK` LOW | Notice | Señal ambigua de abandono |
| `LLM_SUPERFICIAL_MOTIVATION` | Warning | Motivación evaluada como superficial o impulsiva |
| `LLM_UNCLEAR_MOTIVATION` | Notice | El LLM no puede determinar la motivación |
| `LLM_EVASIVENESS` HIGH | Warning | Evasión clara de preguntas concretas |
| `LLM_EVASIVENESS` MODERATE | Notice | Evasión moderada detectada |
| `LLM_INCONSISTENCY` | Warning | Inconsistencias internas entre respuestas |

> Si el LLM no está disponible (timeout, error de red): fallback silencioso — solo se usan las reglas de negocio. No hay degradación visible en la experiencia.

---

### Lógica de Decisión Final

```
flags = rulesFlags + llmFlags

if criticalFlags >= 1   → REJECTED
if warningFlags >= 3    → REJECTED
if warningFlags >= 1    → REVIEW_REQUIRED
else                    → APPROVED
```

El resultado incluye el `reasoning` del LLM (texto explicativo para el revisor humano).

### Consultar el resultado del análisis (rol: Organization)

Las organizaciones pueden acceder al detalle completo del análisis de cualquier solicitud de adopción que les pertenezca:

```
GET /form-analysis/request/{adoptionRequestId}
Authorization: Bearer <token Organization>

200 OK
{
  "id": 1,
  "adoptionRequestId": 51,
  "organizationId": 3,
  "decision": "ReviewRequired",
  "rejectionReason": null,
  "criticalFlags": 0,
  "warningFlags": 1,
  "noticeFlags": 2,
  "llmReasoning": "El solicitante describe experiencia previa sólida...",
  "createdAt": "2026-05-18T14:32:00",
  "flags": [
    { "id": 1, "severity": "Warning", "code": "UNSTABLE_HOUSING", "description": "La vivienda no es estable o hay mudanza prevista" },
    { "id": 2, "severity": "Notice",  "code": "NO_WINDOW_VIEW",   "description": "Sin ventanas con vistas accesibles para el gato" }
  ]
}

404 FORM_ANALYSIS_NOT_FOUND  → el análisis aún no ha terminado (pipeline Kafka en curso)
403 FORM_ANALYSIS_FORBIDDEN  → la solicitud pertenece a otra organización
```

> El 404 es transitorio: el análisis tarda segundos desde que el usuario envía el cuestionario. Reintentar con backoff si se recibe 404 justo tras el envío del formulario.

---

## Referencia de Endpoints

Todos los endpoints se exponen a través del gateway en `http://localhost:8080/api/`.

### Auth

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/auth/login` | ✗ | Login → `{ accessToken, refreshToken, expiresIn }` |
| POST | `/auth/refresh` | ✗ | Renovar tokens |
| POST | `/auth/logout` | ✗ | Revocar refresh token |

### Users

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| POST | `/users` | ✗ | — | Registro (siempre rol `User`; sin campo `role`) |
| POST | `/users/activate` | ✗ | — | Activar con token de email |
| GET | `/users/{email}` | ✓ | Self | Ver perfil |
| PUT | `/users/{email}` | ✓ | Self | Actualizar perfil |
| PUT | `/users/{email}/deactivate` | ✓ | Self | Desactivar cuenta |
| PUT | `/users/{email}/activate` | ✓ | Self/Admin | Reactivar cuenta |
| PATCH | `/users/{id}/role` | ✓ | Organization | Promover usuario a rol `Organization` |
| GET | `/users/me/export` | ✓ | User | Exportar mis datos (GDPR) |
| POST | `/users/me/erasure-request` | ✓ | User | Solicitar borrado de datos (GDPR) |

### Organizations

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| POST | `/organizations/register` | ✗ | — | Registro completo: org + admin (ver Flujo C) |
| POST | `/organizations` | ✓ | Organization | Crear org adicional (creator → miembro Admin) |
| GET | `/organizations/mine` | ✓ | — | Mi organización |
| GET | `/organizations/{id}` | ✓ | — | Detalle (solo miembros) |
| PUT | `/organizations/{id}` | ✓ | — | Editar |
| GET | `/organizations/{id}/members` | ✓ | — | Listar miembros |
| POST | `/organizations/{id}/members` | ✓ | — | Invitar miembro (`role: Admin\|Staff`) |
| PATCH | `/organizations/{id}/members/{userId}/role` | ✓ | — | Cambiar rol |
| DELETE | `/organizations/{id}/members/{userId}` | ✓ | — | Eliminar miembro |

### Cats

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| GET | `/cats` | ✗ | — | Búsqueda paginada `?city=&name=&page=0&size=20` → `PageResponse<CatSummaryResponse>` |
| GET | `/cats/{id}` | ✗ | — | Detalle completo con imágenes |
| GET | `/cats/mine` | ✓ | Organization | Mis gatos (inventario) |
| GET | `/cats/mine/stats` | ✓ | Organization | Estadísticas: `{ available, unavailable, deleted, total }` |
| POST | `/cats` | ✓ | Organization | Crear gato |
| PUT | `/cats/{id}` | ✓ | Organization | Editar gato |
| DELETE | `/cats/{id}` | ✓ | Organization | Borrar gato (lógico) |
| POST | `/cats/{id}/images` | ✓ | Organization | Subir imagen (multipart, ≤5MB, JPEG/PNG/WebP) |
| DELETE | `/cats/{catId}/images/{imageId}` | ✓ | Organization | Borrar imagen |

### Intake Requests

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| POST | `/intake-requests` | ✓ | User | Ceder gato a org |
| GET | `/intake-requests/mine` | ✓ | User | Mis solicitudes de ingreso |
| GET | `/intake-requests/organization` | ✓ | Organization | Solicitudes recibidas |
| GET | `/intake-requests/organization/stats` | ✓ | Organization | Estadísticas por estado |
| PATCH | `/intake-requests/{id}/approve` | ✓ | Organization | Aprobar |
| PATCH | `/intake-requests/{id}/reject` | ✓ | Organization | Rechazar con razón |

### Adoptions

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| POST | `/adoptions` | ✓ | User | Crear solicitud de adopción |
| GET | `/adoptions/{id}` | ✓ | — | Detalle |
| GET | `/adoptions/my` | ✓ | User | Mis solicitudes |
| GET | `/adoptions/organization` | ✓ | Organization | Solicitudes para mi org |
| GET | `/adoptions/organization/pipeline` | ✓ | Organization | Estadísticas por estado |
| GET | `/adoptions/organization/cats/{catId}` | ✓ | Organization | Solicitudes para un gato concreto |
| PATCH | `/adoptions/{id}/status` | ✓ | Organization | Cambiar estado (`Accepted\|Rejected`) |
| POST | `/adoptions/{id}/form` | ✓ | User | Enviar cuestionario (31 campos) |
| POST | `/adoptions/{id}/interview` | ✓ | Organization | Agendar entrevista |
| POST | `/adoptions/{id}/adoption-form` | ✓ | User | Firmar contrato legal (12 campos) |

### Form Analysis

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| GET | `/form-analysis/request/{adoptionRequestId}` | ✓ | Organization | Detalle del análisis automático + flags individuales |

### Chat

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| GET | `/chats/mine` | ✓ | User | Mis conversaciones |
| GET | `/chats/organization` | ✓ | Organization | Conversaciones de la org |
| GET | `/chats/{id}/messages` | ✓ | — | Mensajes de una conversación |
| POST | `/chats/{id}/messages` | ✓ | — | Enviar mensaje |
| POST | `/chats/{id}/block` | ✓ | Organization | Bloquear usuario en conversación |
| DELETE | `/chats/{id}/block` | ✓ | Organization | Desbloquear |

### Storage

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/storage/upload` | ✓ | Subir archivo (multipart, campo `file`) |
| DELETE | `/storage/{key}` | ✓ | Borrar archivo |
| GET | `/storage/files/{key}` | ✗ | Servir archivo públicamente |

---

## Estados y Transiciones

### User Status

```
Pending → Active → Inactive
              ↘ Banned (Admin)
```

### Organization Status

```
Pending → Active    (flujo de registro completado con éxito)
Pending → (purga)   (registro fallido a mitad; job de limpieza lo elimina)
Active  → Suspended (Admin)
```

> El estado `Pending` es transitorio (segundos durante el registro). El frontend siempre recibirá la org en `Active` en el 201.

### Adoption Status

```
Pending
↓ (POST /form)
Reviewing ← análisis en curso
↓ (Kafka: adoption-form-analysed)
├── Rejected  (crítico o 3+ warnings → sin intervención org)
│
└── Accepted  (org aprueba) → FormCompleted (usuario firma) → Completed
    ↘ Rejected  (org rechaza manualmente)
```

### Intake Status

```
Pending → Approved
     ↘ Rejected
```

### Cat Status

```
Available → Unavailable (gato ya adoptado o temporalmente no disponible)
       ↘ Deleted  (lógico, no físico)
```

### Organization Member Status

```
Active → Removed
```

> Los miembros se crean directamente como `Active` al añadirlos. El estado `Invited` existe en el modelo pero no se usa en v1 (sin flujo de aceptación).

---

## Eventos Asíncronos (Kafka)

### `user-registered` (user-service → notification-service)

```json
{ "userId": 1, "email": "juan@ejemplo.com", "name": "Juan", "activationToken": "uuid" }
```
→ notification-service envía email de activación

---

### `adoption-form-submitted` (adoption-service → form-analysis-service)

```json
{
"adoptionRequestId": 1, "adopterId": 5, "catId": 1, "organizationId": 42,
"hasPreviousCatExperience": true, "reactionToUnwantedBehavior": "Redirigir...",
"motivationToAdopt": "Dar un hogar...",
"dailyPlayMinutes": 30,
"...": "31 campos del cuestionario"
}
```

---

### `adoption-form-analysed` (form-analysis-service → adoption-service)

```json
{
"adoptionRequestId": 1,
"decision": "Approved",
"rejectionReason": null,
"adopterId": 5,
"criticalFlags": 0,
"warningFlags": 0,
"noticeFlags": 2
}
```

→ adoption-service actualiza status automáticamente  
→ notification-service envía email al adoptante con resultado

---

## Configuración y Entorno

### Puertos

| Servicio | HTTP | Notas |
|----------|------|-------|
| gateway-service | 8080 | Punto de entrada único para el frontend |
| user-service | 8081 | |
| auth-service | 8082 | |
| storage-service | 8083 | |
| cat-service | 8084 | |
| notification-service | 8085 | Sin endpoints públicos |
| adoption-service | 8086 | |
| form-analysis-service | 8087 | `GET /form-analysis/request/{id}` (rol Organization) |
| organization-service | 8088 | |
| chat-service | 8089 | |
| schedule-service | 8090 | Solo `/q/health` |

> El frontend solo habla con el **gateway (8080)**. Los puertos de los servicios son solo para desarrollo/debug.

### Variables de Entorno Clave

```env
DB_USER=kitties
DB_PASSWORD=kitties
DB_HOST=localhost
DB_PORT=5432
DB_NAME=kitties

JWT_PRIVATE_KEY_LOCATION=privateKey.pem
JWT_PUBLIC_KEY_LOCATION=publicKey.pem

MINIO_ROOT_USER=kitties
MINIO_ROOT_PASSWORD=change_me_min16chars

KAFKA_HOST=localhost
KAFKA_PORT=9092

CORS_ORIGIN=http://localhost:5173
NVIDIA_API_KEY=<clave de NVIDIA NIM para el análisis LLM>
```

---

## Notas para el Desarrollo Frontend

1. **Renovar token** ≈ 1-2 min antes de expirar (900s = 15 min). Interceptar 401 y llamar a `/auth/refresh`.
2. **Rate limiting:** ~10 req/min en login, ~20 en refresh, ~5 en upload de imágenes.
3. **Multipart upload:** usar `FormData` con campo `file`, no JSON. El `Content-Type` lo gestiona el browser.
4. **Imágenes:** máximo 5 MB, formatos JPEG / PNG / WebP.
5. **Paginación:** `/cats` devuelve `{ content, page, size, total, totalPages }`. Parámetros: `?page=0&size=20`.
6. **Errores de validación:** HTTP 422 con `{ status, code: "VALIDATION_FAILED", violations: [{ field, code, params? }] }`. Códigos de field: `REQUIRED`, `INVALID_SIZE`, `INVALID_EMAIL`, `INVALID_FORMAT`, `TOO_SMALL`, `TOO_LARGE`.
7. **Errores de dominio:** HTTP 400/404/409 con `{ status, code: "ENTIDAD_MOTIVO" }`. El `code` es machine-readable para mostrar mensajes i18n.
   - `403 CAT_ACCESS_DENIED` — la organización autenticada intenta editar/borrar un gato que pertenece a otra org.
   - `403 ACCESS_DENIED` — la organización autenticada intenta actualizar el estado o agendar entrevista de una adopción/intake que pertenece a otra org.
8. **Soft deletes:** usuarios, gatos y miembros nunca se borran físicamente.
9. **CORS:** configurado para `http://localhost:5173` en dev. Cambiar `CORS_ORIGIN` en producción.
10. **form-analysis-service** expone `GET /form-analysis/request/{adoptionRequestId}` (rol `Organization`) para que las organizaciones consulten el detalle del análisis automático. Si se recibe 404, el análisis aún está en curso — reintentar con backoff.
11. **Chat v1 es REST polling** (no WebSocket todavía). Implementar polling manual cada N segundos para mensajes nuevos.
12. **Registro de organizaciones:** usar `POST /organizations/register` (público, sin JWT). El campo `role` en `POST /users` ya no se acepta — el backend siempre asigna `User`.

---

## Roadmap Pendiente

- [ ] WebSocket real-time para chat (actualmente REST polling)
- [ ] Auto-crear chat al aprobar intake (pendiente de integración en adoption-service)
- [ ] Pagos integrados
- [ ] Notificaciones push (SSE o WebSocket)
- [ ] Geolocalización avanzada (radio de búsqueda)
- [ ] Reportes PDF del contrato firmado

---

**Última actualización:** 2026-05-18 — v2.3.0