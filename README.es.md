<div align="right">
  <a href="README.md">🇬🇧 English</a> &nbsp;|&nbsp;
  <strong>🇪🇸 Español</strong>
</div>

# Kitties API

Backend de microservicios para Kitties, una plataforma de adopción de gatos para protectoras y veterinarios. Monorepo multi-módulo Maven construido con Quarkus 3.34.3 y Java 21.

---

## Tabla de contenidos

- [Visión general](#visión-general)
- [Servicios](#servicios)
- [Arquitectura](#arquitectura)
  - [Despliegue en DMZ](#despliegue-en-dmz)
  - [Mapa de puertos](#mapa-de-puertos)
- [Stack tecnológico](#stack-tecnológico)
- [Desarrollo](#desarrollo)
  - [Requisitos previos](#requisitos-previos)
  - [Infraestructura](#infraestructura)
  - [Arrancar servicios](#arrancar-servicios)
  - [Claves de seguridad](#claves-de-seguridad)
- [Despliegue](#despliegue)
- [Tests](#tests)
- [Patrones conocidos](#patrones-conocidos)
- [either-mon — Librería de manejo de errores](#either-mon--librería-de-manejo-de-errores)
- [Variables de entorno](#variables-de-entorno)
- [Roadmap](#roadmap)
- [Privacidad y protección de datos](PRIVACY.md)
- [Guía de incorporación para desarrolladores](ONBOARDING.md)

---

## Visión general

Kitties conecta adoptantes con protectoras y veterinarios. Cada servicio es propietario de su esquema PostgreSQL y se comunica vía gRPC (síncrono) o Kafka (asíncrono). El gateway es el único punto de entrada expuesto a internet; el resto de servicios corren en una red privada.

---

## Servicios

### gateway-service — puerto 8080

Punto de entrada único desplegado en la DMZ. Valida JWT antes de enrutar a los servicios internos. Las rutas públicas bypasan la validación JWT.

**Rutas públicas (sin JWT):**
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/users` (registro)
- `POST /api/users/activate` (activación de cuenta)
- `GET /api/cats`, `GET /api/cats/{id}`
- `GET /api/storage/files/{key}`

**Rutas con rate limit** (SmallRye Fault Tolerance `@RateLimit`, por instancia JVM):
- `POST /api/auth/login` — 10 req/min
- `POST /api/auth/refresh` — 20 req/min
- `POST /api/storage/upload` — 5 req/min

Superar el límite devuelve HTTP **429 Too Many Requests**.

---

### user-service — puerto 8081

Gestión de cuentas de usuario. Expone un servidor gRPC consumido por `auth-service`.

**Endpoints:**
- `POST /users` — Registro (estado: Pending, envía email de activación vía Kafka)
- `POST /users/activate` — Activar cuenta, body `{"token":"..."}` (público)
- `GET /users/{email}` — Obtener por email (JWT requerido)
- `PUT /users/{email}` — Actualizar (JWT, solo el propio usuario)

**Ciclo de vida del usuario:** `Pending` → `Active` → `Inactive` / `Banned`

**Flujo de activación:** el registro publica un evento Kafka `user-registered` → `notification-service` envía un email con enlace a `FRONTEND_URL/activate?token=...` → el frontend llama a `POST /users/activate` con el token en el body (el token nunca queda expuesto en los logs del servidor API).

**gRPC:**
```proto
service UserService {
  rpc ValidateCredentials(ValidateCredentialsRequest) returns (ValidateCredentialsResponse);
  rpc GetUserById(GetUserByIdRequest) returns (GetUserResponse);
}
```

---

### auth-service — puerto 8082

Emite y rota tokens JWT. Valida credenciales vía gRPC contra `user-service`. El canal gRPC está protegido con un header de secreto compartido (`x-internal-token`). Implementa una interfaz `IdentityProvider` diseñada para migración futura a Keycloak sin cambios en la capa de servicio.

- Token de acceso: 15 min (firmado con RSA-2048)
- Token de refresco: 7 días (con estado, almacenado en BD, revocable)
- El claim `groups` del JWT porta el rol del usuario (`User`, `Organization`, `Admin`) para RBAC downstream

**Endpoints:**
- `POST /auth/login` — Devuelve access + refresh token
- `POST /auth/refresh` — Rota ambos tokens
- `POST /auth/logout` — Revoca el refresh token

---

### cat-service — puerto 8084

Perfiles de gatos en adopción. Las imágenes se proxian a través del gateway; las URLs del bucket nunca se exponen directamente.

**Ciclo de vida del gato:** `Available` → `Unavailable` / `Deleted` (solo lógico — nunca se elimina ninguna fila)

**Endpoints:**
- `GET /cats?city=X&name=Y` — Búsqueda (público; excluye gatos `Deleted`)
- `GET /cats/{id}` — Detalle con imágenes (público; devuelve **404** para gatos `Deleted`)
- `GET /cats/mine` — Gatos propios de la organización (JWT; excluye `Deleted`)
- `GET /cats/mine/stats` — Estadísticas de inventario `{available, unavailable, deleted, total}` (JWT; incluye `Deleted`)
- `POST /cats` — Crear perfil (JWT)
- `PUT /cats/{id}` — Actualizar (JWT, solo propietario)
- `DELETE /cats/{id}` — Borrado lógico (`status → Deleted`, JWT, solo propietario); devuelve **409** si existe alguna solicitud de adopción activa para este gato
- `POST /cats/{id}/images` — Subir imagen
- `DELETE /cats/{catId}/images/{imageId}` — Eliminar imagen

---

### storage-service — puerto 8083

Almacenamiento de ficheros compatible con S3. Backend MinIO en desarrollo y Cloudflare R2 en producción.

**Endpoints:**
- `POST /storage/upload` — Subir fichero (jpg/png, máx. 5 MB)
- `DELETE /storage/{key}` — Eliminar fichero
- `GET /storage/files/{key}` — Servir fichero (proxiado a través del gateway)

---

### notification-service — puerto 8085

Consume eventos Kafka y envía emails transaccionales vía SMTP.

**Topics consumidos:**
- `user-registered` → email de activación de cuenta (enlace a `FRONTEND_URL/activate?token=...`)
- `adoption-form-analysed` → email de resultado de adopción (aceptado / rechazado) al email del adoptante del claim JWT

---

### adoption-service — puerto 8086

Flujo completo de adopción. Los adoptantes envían solicitudes y rellenan formularios de cribado; las organizaciones gestionan el proceso, programan entrevistas y registran gastos.

**Ciclo de vida de la adopción:** `Pending` → `Reviewing` → `Accepted` → `FormCompleted` → `PaymentPending` → `Completed` / `Rejected` / `PaymentFailed`

**Gastos:** costes veterinarios facturados a la organización; comisión de gestión retenida por Kitties.

**Endpoints:**

| Método | Ruta | Rol | Notas |
|--------|------|-----|-------|
| `POST` | `/adoptions` | `User` | Crear solicitud |
| `GET` | `/adoptions/{id}` | Cualquiera (JWT) | El llamante debe ser adoptante **u** organización de esa solicitud |
| `GET` | `/adoptions/my` | `User` | Mis solicitudes como adoptante |
| `GET` | `/adoptions/organization` | `Organization` | Solicitudes para mi organización |
| `GET` | `/adoptions/organization/pipeline` | `Organization` | Conteos de adopciones agrupados por estado |
| `GET` | `/adoptions/organization/cats/{catId}` | `Organization` | Historial de caso para un gato concreto |
| `PATCH` | `/adoptions/{id}/status` | `Organization` | Actualizar estado; solo propietario; devuelve **409** si el gato está eliminado y el nuevo estado no es terminal |
| `POST` | `/adoptions/{id}/form` | `User` | Enviar formulario de cribado (`Pending → Reviewing`); devuelve **409** si el gato está eliminado |
| `POST` | `/adoptions/{id}/interview` | `Organization` | Programar entrevista; devuelve **409** si el gato está eliminado |
| `POST` | `/adoptions/{id}/adoption-form` | `User` | Enviar contrato legal; devuelve **409** si el gato está eliminado |

Los roles se refuerzan vía `@RolesAllowed` (claim `groups` de SmallRye JWT). La propiedad se verifica en la capa de servicio; las discrepancias devuelven **403**.

Todos los endpoints de mutación verifican que el gato sigue activo (no `Deleted`) vía `cat-service` antes de escribir estado. Las transiciones terminales (`Rejected`, `Completed`) están exentas — deben poder ejecutarse siempre para permitir limpieza.

**Kafka topics:**
- `adoption-form-submitted` (saliente) — datos del formulario de cribado para análisis
- `adoption-form-analysed` (entrante) — decisión del análisis (ACCEPTED / REJECTED)

**Flujo de ingreso** (añadido 2026-04-28, vive bajo el paquete `intake/` junto al agregado de adopción):

| Método  | Ruta                                  | Rol            | Notas                                                                |
|---------|---------------------------------------|----------------|----------------------------------------------------------------------|
| `POST`  | `/intake-requests`                    | `User`         | El usuario pide a una organización que acoja a un gato              |
| `GET`   | `/intake-requests/mine`               | `User`         | Mis solicitudes de ingreso pendientes/aprobadas/rechazadas           |
| `GET`   | `/intake-requests/organization`       | `Organization` | Solicitudes de ingreso dirigidas a mi organización                   |
| `GET`   | `/intake-requests/organization/stats` | `Organization` | Conteos por estado `{pending, approved, rejected}`                   |
| `PATCH` | `/intake-requests/{id}/approve`       | `Organization` | Aprueba el ingreso (solo Pending → Approved)                         |
| `PATCH` | `/intake-requests/{id}/reject`        | `Organization` | Rechaza con motivo; la respuesta incluye organizaciones alternativas en la misma región |

---

### chat-service — puerto 8089

Conversaciones entre un adoptante y una organización, abiertas tras la aprobación de un ingreso. Solo REST en v1 (WebSocket próximamente). Cada conversación está vinculada a una solicitud de ingreso.

**Endpoints:**

| Método  | Ruta                          | Rol              | Notas                                                |
|---------|-------------------------------|------------------|------------------------------------------------------|
| `GET`   | `/chats/mine`                 | `User`           | Mis conversaciones                                   |
| `GET`   | `/chats/organization`         | `Organization`   | Conversaciones de mi organización                    |
| `GET`   | `/chats/{id}/messages`        | Cualquiera (participante) | Historial de mensajes                       |
| `POST`  | `/chats/{id}/messages`        | Cualquiera (participante) | Enviar mensaje                              |
| `POST`  | `/chats/{id}/block`           | `Organization`   | Bloquear usuario en esta organización (idempotente)  |
| `DELETE`| `/chats/{id}/block`           | `Organization`   | Desbloquear (idempotente)                            |

**Internos (servicio-a-servicio, `X-Internal-Token`)**:
- `POST /chats/internal/conversations` — abrir conversación `(intakeRequestId, userId, organizationId)`
- `POST /chats/internal/retention/run` — purgar mensajes y conversaciones inactivos más de 1 año
- `DELETE /chats/internal/users/{userId}` — anonimizar todos los mensajes de un usuario (RGPD Art. 17)

---

### schedule-service — puerto 8090

Planificador centralizado. Sin endpoints públicos, sin base de datos, sin Kafka. Dispara jobs de retención de datos y purga de borrado sobre los otros servicios vía llamadas HTTP internas (`X-Internal-Token`), desacoplando la lógica cron de los servicios de negocio.

| Cron | Destino | Qué dispara |
|------|---------|-------------|
| diario 02:00 | `user-service` | Purga de borrado — anonimizar usuarios cuyo periodo de gracia de 30 días ha expirado |
| diario 02:15 | `user-service` | Eliminar cuentas `Inactive` con token de activación expirado |
| diario 02:30 | `adoption-service` | Eliminar solicitudes rechazadas con más de 1 año; anonimizar PII en formularios completados con más de 5 años |
| diario 04:00 | `chat-service` | Eliminar conversaciones (y sus mensajes) inactivas más de 1 año |
| domingo 03:00 | `auth-service` | Eliminar refresh tokens expirados o revocados |

**Solo `/q/health/live` es accesible desde la red** — el resto de rutas no existen.

---

## Arquitectura

### Despliegue en DMZ

Tres redes aisladas. Solo el gateway es accesible desde internet.

```
                        ┌─────────────────────────────────────────────┐
  Internet              │  RED PÚBLICA                                │
  ──────────►  :443 ──► │  gateway-service :8080                      │
                        └──────────────┬──────────────────────────────┘
                                       │  Peticiones validadas con JWT
                        ┌──────────────▼──────────────────────────────┐
                        │  RED PRIVADA                                │
                        │                                             │
                        │  user-service         :8081  gRPC :9090     │
                        │  auth-service         :8082  gRPC :9091     │
                        │  cat-service          :8084                 │
                        │  storage-service      :8083                 │
                        │  notification-service :8085                 │
                        │  adoption-service     :8086                 │
                        │  form-analysis-service :8087                │
                        │  organization-service :8088                 │
                        │  chat-service         :8089                 │
                        │  schedule-service     :8090                 │
                        └──────────────┬──────────────────────────────┘
                                       │
                        ┌──────────────▼──────────────────────────────┐
                        │  RED DE DATOS                               │
                        │                                             │
                        │  PostgreSQL  :5432                          │
                        │  Kafka       :9092                          │
                        │  MinIO       :9000 / :9001                  │
                        └─────────────────────────────────────────────┘
```

Implementado con Docker networks (dev/staging) o Kubernetes NetworkPolicy (producción).

### Mapa de puertos

| Servicio               | HTTP  | gRPC  |
|------------------------|-------|-------|
| gateway-service        | 8080  | —     |
| user-service           | 8081  | 9090  |
| auth-service           | 8082  | 9091  |
| cat-service            | 8084  | —     |
| storage-service        | 8083  | —     |
| notification-service   | 8085  | —     |
| adoption-service       | 8086  | —     |
| form-analysis-service  | 8087  | —     |
| organization-service   | 8088  | —     |
| chat-service           | 8089  | —     |
| schedule-service       | 8090  | —     |
| PostgreSQL             | 5432  | —     |
| MinIO API              | 9000  | —     |
| MinIO Consola          | 9001  | —     |
| Kafka                  | 9092  | —     |
| Zookeeper              | 2181  | —     |
| MailHog SMTP           | 1025  | —     |
| MailHog UI             | 8025  | —     |

---

## Stack tecnológico

| Capa        | Tecnología                                                          |
|-------------|---------------------------------------------------------------------|
| Framework   | Quarkus 3.34.3                                                      |
| Lenguaje    | Java 21                                                             |
| Base de datos | PostgreSQL 16                                                     |
| ORM         | Hibernate Reactive + Panache                                        |
| REST        | Quarkus REST (RESTEasy Reactive)                                    |
| gRPC        | Quarkus gRPC                                                        |
| Mensajería  | Apache Kafka + SmallRye Reactive Messaging                          |
| Auth        | SmallRye JWT (par de claves RSA-2048)                               |
| Almacenamiento | Quarkiverse Amazon S3 — MinIO (dev) / Cloudflare R2 (prod)      |
| Email       | Quarkus Mailer — MailHog (dev) / SMTP (prod)                       |
| Contenedores | Jib (sin Dockerfile necesario)                                     |
| Reactivo    | Mutiny (`Uni<T>` / `Multi<T>`)                                      |

---

## Desarrollo

### Requisitos previos

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Infraestructura

Copia la plantilla de entorno y rellena los valores reales antes de arrancar:

```bash
cp .env.example .env
# editar .env — todos los valores son obligatorios, sin fallbacks en docker-compose
```

Luego arranca el stack:

```bash
docker compose up -d
```

Arranca PostgreSQL, MinIO, Kafka, Zookeeper y MailHog. Los esquemas se crean automáticamente por `init.sql` en la primera ejecución. El fichero `.env` está en el `.gitignore`; nunca lo commitees.

> **Bucket MinIO**: `minio/minio:latest` no crea buckets automáticamente desde `MINIO_DEFAULT_BUCKETS` (eso es una característica de `bitnami/minio`). `storage-service` crea el bucket automáticamente vía `BucketInitializer` al arrancar — no es necesaria ninguna acción manual.

### Arrancar servicios

Usa `dev.sh` en la raíz del repositorio para arrancar servicios en modo dev de Quarkus. Cada servicio corre con `-am` para que Maven compile las dependencias del reactor sin un `mvn install` previo.

> **Nota sobre credenciales de storage-service**: el modo dev de Quarkus usa el directorio del módulo como directorio de trabajo, por lo que el `.env` raíz no se carga automáticamente. Un symlink resuelve esto — ejecútalo una vez tras clonar:
> ```bash
> ln -sf ../.env storage-service/.env
> ```
> El symlink está en el `.gitignore`.

```bash
# Arrancar todos los servicios
./dev.sh

# Arrancar gateway + servicios específicos
./dev.sh user-service,auth-service
```

`dev.sh` valida los nombres de servicio, nunca arranca `gateway-service` dos veces, captura todos los PIDs y envía SIGTERM a todos los procesos al pulsar Ctrl+C.

Para arrancar un servicio individual manualmente:

```bash
mvn compile quarkus:dev -pl <servicio> -am
```

### Claves de seguridad

JWT requiere un par de claves RSA-2048. Los ficheros de clave (`*.pem`) están excluidos del control de versiones.

```bash
# Generar clave privada (PKCS8, requerida por SmallRye JWT)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out auth-service/src/main/resources/privateKey.pem

# Extraer clave pública
openssl rsa -pubout \
  -in  auth-service/src/main/resources/privateKey.pem \
  -out auth-service/src/main/resources/publicKey.pem

# Distribuir la clave pública a los servicios verificadores
for svc in user-service cat-service gateway-service adoption-service organization-service chat-service; do
  cp auth-service/src/main/resources/publicKey.pem $svc/src/main/resources/publicKey.pem
done
```

| Fichero          | Servicios                                                                                       | Perfil    |
|------------------|-------------------------------------------------------------------------------------------------|-----------|
| `privateKey.pem` | `auth-service`                                                                                  | dev / test |
| `publicKey.pem`  | `auth-service`, `user-service`, `cat-service`, `gateway-service`, `adoption-service`, `organization-service`, `chat-service` | dev / test |

**En producción** las claves se leen del sistema de ficheros, no del classpath. Móntelas como Docker Secrets o Kubernetes Secrets en `/run/secrets/` (o sobrescribe con `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION`).

**Configuración JWT:** issuer `https://www.kitti.es`, TTL del access token 900 s, TTL del refresh token 7 días. El claim `groups` porta el rol del usuario (`User`, `Organization`, `Admin`).

En Windows, ejecuta los comandos anteriores en Git Bash o WSL. Para instalar OpenSSL vía winget: `winget install ShiningLight.OpenSSL`.

### Clave de cifrado de número de identificación

`adoption-service` almacena valores DNI/NIE cifrados con AES-256-GCM (LOPDGDD art. 9 / C-1). La clave debe ser un secreto de 32 bytes codificado en Base64 inyectado vía `KITTIES_ID_NUMBER_KEY`. Nunca se almacena en la base de datos ni en el código fuente.

```bash
# Generar una clave de 256 bits (hazlo una vez y guárdala de forma segura)
openssl rand -base64 32
```

En **desarrollo** el servicio usa una clave hardcodeada solo para dev — no es necesaria ninguna acción. En **producción** la clave debe suministrarse como Docker Secret o variable de entorno:

```bash
# Docker Secret (recomendado)
echo "$(openssl rand -base64 32)" | docker secret create kitties_id_number_key -

# O vía variable de entorno en .env
KITTIES_ID_NUMBER_KEY=<output de openssl rand -base64 32>
```

> **Rotación de clave**: para rotar la clave debes re-cifrar todas las filas `adoption_forms.id_number` existentes antes de desplegar la nueva clave. No hay migración automática — coordínalo con una ventana de mantenimiento.

### Secreto interno gRPC

`auth-service` y `user-service` se comunican por gRPC protegido con un secreto compartido inyectado como header `x-internal-token`. Establece `GRPC_INTERNAL_SECRET` en tu `.env` (requerido en producción; por defecto `kitties-dev-secret` en dev).

---

## Despliegue

El despliegue en producción usa **Docker Compose** con los 9 servicios pre-construidos y publicados en Docker Hub por el pipeline CI/CD.

### Requisitos previos

- Docker + Docker Compose v2
- Un servidor Linux con los puertos 80 y 443 abiertos
- Cuenta en Docker Hub (para el push de imágenes; puede reemplazarse por cualquier registro)
- Secrets del repositorio GitHub: `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`

### 1. Generar claves JWT

```bash
mkdir -p secrets
openssl genrsa -out secrets/private.pem 4096
openssl rsa -in secrets/private.pem -pubout -out secrets/public.pem
```

El directorio `secrets/` está en el gitignore para ficheros `*.pem` — nunca commitees claves privadas.

### 2. Configurar el entorno

```bash
cp .env.example .env
# Rellenar DB_PASSWORD, MINIO_ROOT_PASSWORD, credenciales SMTP, CORS_ORIGIN, etc.
```

Consulta [Variables de entorno](#variables-de-entorno) para la referencia completa.

### 3. Bootstrapping SSL (solo primer despliegue)

Nginx requiere que el certificado exista antes de poder arrancar el bloque HTTPS. Ejecuta Certbot en modo standalone una vez antes de levantar el stack completo:

```bash
docker compose -f docker-compose.prod.yml up -d postgres minio zookeeper kafka
docker run --rm -p 80:80 \
  -v kitties-prod_certbot_certs:/etc/letsencrypt \
  -v kitties-prod_certbot_www:/var/www/certbot \
  certbot/certbot certonly --standalone \
  -d www.kitti.es --email ciscoadiz@gmail.com --agree-tos --no-eff-email
```

Las renovaciones posteriores las gestiona automáticamente el servicio `certbot` (cada 12 h).

### 4. Arrancar el stack de producción

```bash
docker compose -f docker-compose.prod.yml up -d
```

Esto arranca PostgreSQL 16, MinIO, Zookeeper, Kafka, los 11 servicios de aplicación, Nginx (puertos 80/443) y el demonio de renovación Certbot. El tráfico entra por Nginx en el **puerto 443** (HTTPS); HTTP redirige a HTTPS automáticamente.

### Pipeline CI/CD

El workflow de GitHub Actions en `.github/workflows/ci-cd.yml` se ejecuta en cada push a `main`:

1. **Matriz de tests** — ejecuta `mvn test` en paralelo para los 11 servicios
2. **Build & push** — construye imágenes Docker y las publica en Docker Hub como `<DOCKERHUB_USERNAME>/kitties-<servicio>:latest`

Los pull requests solo disparan la matriz de tests (sin push de imágenes).

---

## Tests

Los tests unitarios usan Mockito puro (`@ExtendWith(MockitoExtension.class)`), sin contenedores. Los tests de integración usan `@QuarkusTest` + RestAssured con perfiles `%test.*` en la configuración principal.

| Servicio               | Tests | Notas                                                                              |
|------------------------|------:|------------------------------------------------------------------------------------|
| adoption-service       | 142   | RBAC + ownership; flujo intake; guardia gato eliminado; purga retención; anonimización GDPR |
| user-service           | 58    | Borrado GDPR; activación; cambio de contraseña; VOs de dominio                     |
| cat-service            | 50    | CRUD completo + ciclo de vida de imágenes; reglas de negocio deleteCat vía CatWriteService |
| either-mon             | 57    | Either, Try, Validation, ConstraintViolationMapper                                 |
| organization-service   | 52    | Límites de miembros por plan; `@TestSecurity` RBAC; `@InternalOnly` by-region      |
| chat-service           | 44    | Conversaciones, mensajes, ban, creación interna; purga de retención               |
| auth-service           | 28    | Ciclo de vida de tokens; rotación de refresh; logout idempotente; checks con ArgumentCaptor |
| form-analysis-service  | 33    | Motor de reglas (27 casos); consumidor Kafka; ReviewRequired; JSON inválido DLQ    |
| gateway-service        | 29    | WireMock proxy; timeout 504; rate limiter; filtro JWT                              |
| storage-service        | 17    | Validación MIME; magic bytes; límite de tamaño; propagación de fallo del proveedor |
| schedule-service       | 7     | Cableado de Jobs (WireMock); agotamiento de reintentos                             |
| notification-service   | 6     | Error SMTP hacia DLQ; JSON inválido hacia DLQ                                      |

**Total: 523 tests** · Ratio Tests/CC global: **0,48**

La métrica Tests/CC mide cuántos tests existen en relación a la complejidad ciclomática. Ver `test-coverage-report-2026-05-15.md` para el análisis de brecha completo.

**Tests end-to-end** se ejecutan contra el stack completo en vivo (todos los servicios + infra Docker):

| Suite              | Tests | Cobertura |
|--------------------|-------|-----------|
| `StorageE2E`       | 6     | Upload JPEG, servir público, 401, 400 tipo inválido, eliminar, rate limit 429 |
| `SecurityE2E`      | 2     | Rechazo de magic bytes (400), X-Content-Type-Options nosniff en respuestas del gateway |

```bash
# Ejecutar suite e2e (requiere stack completo corriendo)
mvn test -Pe2e -pl e2e-tests

# Arrancar stack completo y ejecutar e2e automáticamente
./dev.sh --e2e
```

**Aislamiento del rate limit en tests e2e**: los tests que golpean endpoints con rate limit deben enviar un header `X-Forwarded-For` único por ejecución de test (ej. `"test-" + System.currentTimeMillis()`) para evitar contaminación de bucket entre ejecuciones consecutivas dentro de la ventana de 60 segundos.

```bash
mvn verify -pl <servicio>
```

---

## Patrones conocidos

### Gestión de sesión reactiva

Hibernate Reactive requiere que todas las operaciones de BD corran en el hilo del event loop de Vert.x.

```java
@WithSession      // lecturas
public Uni<T> find() { ... }

@WithTransaction  // escrituras (en bean *WriteService dedicado — ver DIP más abajo)
public Uni<T> save() { ... }
```

### Principio de Inversión de Dependencias (DIP)

Las llamadas estáticas `Panache.withTransaction()` / `Panache.withSession()` inline en la capa de servicio violan el DIP y no son testables con `@InjectMocks` (requieren el contexto completo de Quarkus). La solución es delegar las escrituras a un bean dedicado:

```java
// MAL — Panache.withTransaction() inline en el servicio de dominio
@Incoming("topic") public Uni<Void> onEvent(String msg) { return Panache.withTransaction(() -> ...); }

// BIEN — delegar a un bean @WithTransaction
@Incoming("topic") public Uni<Void> onEvent(String msg) { return writeService.persist(...); }

@ApplicationScoped class CatWriteService {
    @WithTransaction public Uni<Void> persist(...) { ... }
}
```

Los beans `@WithTransaction` son bypassados por Mockito (`@InjectMocks`), lo que permite happy paths unitarios sin infraestructura.

### Patrón Repository

Todos los servicios usan `PanacheRepository` (no Active Record).

### Sin relaciones JPA

Las entidades no usan `@OneToMany` / `@ManyToOne`. Los joins entre entidades se resuelven explícitamente en la capa de servicio.

### Integridad referencial cross-servicio

No hay claves foráneas entre bases de datos de servicios. La integridad referencial se refuerza en la capa de aplicación en ambas direcciones:

- **Antes de eliminar un gato** — `cat-service` llama a `GET /adoptions/internal/cats/{id}/active` (HTTP interno). Si existe alguna solicitud de adopción no terminal, el borrado se bloquea con **409**.
- **Antes de mutar una adopción** — `adoption-service` llama a `GET /cats/{id}` en cada escritura de cambio de estado. Si el gato está `Deleted` (404), la operación falla con **409**.

Las dos guardias juntas cierran el invariante: un gato no puede borrarse mientras haya adopciones en curso, y las adopciones en curso no pueden avanzar una vez que el gato ha desaparecido. Las transiciones terminales (`Rejected`, `Completed`) saltan la comprobación del gato — deben poder completarse siempre.

### Autenticación interna servicio-a-servicio (`@InternalOnly`)

Algunos endpoints solo deben ser accesibles por otros servicios dentro de la red privada — nunca por usuarios ni a través del gateway. El patrón `@InternalOnly` gestiona esto sin JWT.

**Cómo funciona:** una anotación JAX-RS `@NameBinding` enlaza un `ContainerRequestFilter` exclusivamente a los recursos o métodos marcados con `@InternalOnly`. El filtro comprueba el header `X-Internal-Token` contra el secreto compartido `kitties.internal.secret` y aborta con 401 si no coincide.

```
petición entrante
      │
      ▼
InternalTokenFilter.filter()        ← solo se ejecuta en métodos/clases @InternalOnly
  comparar X-Internal-Token con kitties.internal.secret
  ✗ → 401 Unauthorized
  ✓ → continuar al resource
```

**Secreto compartido:** mismo valor en todos los servicios. Por defecto en dev: `kitties-dev-secret`. En producción: inyectar vía variable de entorno `KITTIES_INTERNAL_SECRET` / Docker Secret.

**Lado llamante** (MicroProfile REST Client):
```java
@RegisterRestClient(configKey = "foo-service")
@Path("/foo/internal")
public interface FooInternalClient {

    @POST
    @Path("/alguna-accion")
    Uni<Response> disparar(@HeaderParam("X-Internal-Token") String token);
}
```
Inyectar `@ConfigProperty(name = "kitties.internal.secret") String internalSecret` en el llamante y pasarlo al método.

**Lado servidor:**
```java
@Path("/foo/internal")
@InternalOnly          // ← toda la clase queda protegida; también puede aplicarse por método
public class FooInternalResource { ... }
```

**Regla:** el gateway nunca debe hacer proxy de rutas que coincidan con `/*/internal/*`. Estos endpoints solo son accesibles desde la red privada de contenedores.

---

### Value Objects

Los conceptos de dominio con restricciones de formato (`Email`, `ActivationToken`) se implementan como clases `final` inmutables con constructor privado y un factory estático `of()`.

```java
public final class Email {
    private final String value;
    private Email(String value) { this.value = value; }

    public static Email of(String raw) {
        if (raw == null || !raw.contains("@"))
            throw new IllegalArgumentException("Email inválido");
        return new Email(raw.toLowerCase());
    }

    public String value() { return value; }
}
```

---

## either-mon — Librería de manejo de errores

`either-mon` (`es.kitti.mon`) es el único módulo Maven compartido. Contiene tipos funcionales puros sin lógica de negocio. Todos los servicios dependen de él.

### Either\<L, R\>

Representa un resultado que puede ser un error (`Left`) o un valor (`Right`). Los errores de dominio se vuelven explícitos en el tipo de retorno: si un método devuelve `Uni<Either<DomainError, CatResponse>>`, el compilador obliga a tratar ambos casos.

Úsalo en métodos de servicio que pueden fallar por razones de negocio esperadas (no encontrado, acceso denegado, conflicto). Los errores inesperados (infraestructura, bugs) siguen propagándose como fallos del `Uni` y los captura el `GlobalExceptionMapper`.

```java
// Construcción
Either<DomainError, Cat> ok  = Either.right(cat);
Either<DomainError, Cat> err = Either.left(new NotFoundError("CAT_NOT_FOUND"));

// Para operaciones de escritura sin valor de retorno útil: Unit en lugar de Void/null
Either<DomainError, Unit> ok = Either.<DomainError>unit();  // Right(Unit.Instance)

// Inspección
result.isRight()   // true si es Right
result.isLeft()    // true si es Left

// Extraer con fallback
Cat cat = result.getOrElse(null);

// Transformar el Right (Left se propaga sin tocar)
Either<DomainError, CatResponse> response = result.map(cat -> mapper.toResponse(cat));

// Encadenar operaciones que también pueden fallar
Either<DomainError, String> name = result.flatMap(cat ->
        cat.name == null ? Either.left(new BadRequestError("NAME_MISSING")) : Either.right(cat.name));

// Colapsar ambas ramas al mismo tipo
Response httpResponse = result.fold(
        err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
        cat -> Response.ok(cat).build()
);
```

**Patrón completo service → resource:**

```java
// Service (@WithSession para lecturas; escrituras delegan a *WriteService con @WithTransaction)
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
    return catService.findById(id, Long.parseLong(jwt.getSubject()))
            .onItem().transform(either -> either.fold(
                    err -> Response.status(err.httpStatus()).entity(ErrorResponse.of(err)).build(),
                    cat -> Response.ok(cat).build()
            ));
}
```

### Tipos DomainError

| Tipo | HTTP | Cuándo |
|---|---|---|
| `NotFoundError(String code)` | 404 | Entidad no encontrada |
| `ForbiddenError(String code)` | 403 | Acceso denegado por permisos de dominio |
| `ConflictError(String code)` | 409 | Estado inválido: duplicado, límite alcanzado, adopción activa… |
| `UnauthorizedError(String code)` | 401 | Token inválido o credenciales incorrectas |
| `BadRequestError(String code)` | 400 | Contenido inválido que no encaja en validación de campos |
| `ValidationError(List<FieldViolation>)` | 422 | Resultado de `Validation` — ver más abajo |

El campo `code` es un identificador machine-readable para que el cliente pueda localizar el mensaje en el idioma del usuario. Convención: `ENTIDAD_MOTIVO` en mayúsculas, p.ej. `CAT_NOT_FOUND`, `EMAIL_ALREADY_EXISTS`.

### Validation\<T\>

Acumula todas las violaciones de validación en lugar de parar en la primera. Diseñado para validar la entrada HTTP antes de que llegue al servicio.

**Diferencia clave con Either:** `Either` cortocircuita. `Validation` evalúa todos los campos y devuelve todas las violaciones juntas.

```java
// En un DTO de petición
public record CatCreateRequest(String name, Integer age, String city) {
    public Validation<CatCreateRequest> validate() {
        return Validation.valid(this)
                .and(Name.of("name", name))
                .and(CatAge.of(age))
                .and(City.of(city));
    }
}

// En un resource
@POST
public Uni<Response> create(CatCreateRequest request) {
    return request.validate().match(
            err   -> Uni.createFrom().item(Response.status(422).entity(ErrorResponse.of(err)).build()),
            valid -> catService.create(valid, callerId).onItem().transform(cat -> Response.status(201).entity(cat).build())
    );
}
```

Operadores: `.required()`, `.requiredString()`, `.optional()`, `.and()`, `.zip()`. Terminales: `.match()`, `.toEither()`, `.fromBusiness()`.

### Try\<T\>

Captura excepciones de código síncrono y las convierte en `Either`. Útil en resources para parsear parámetros de query string:

```java
Try.attempt(() -> LocalDateTime.parse(param))
   .<DomainError>toEither(e -> new BadRequestError("INVALID_DATE_FORMAT"))
   .fold(
       err  -> Uni.createFrom().item(Response.status(err.httpStatus())...),
       date -> service.setLegalHold(userId, date)...
   );
```

### Value Objects

Garantizan que un campo es válido por construcción: si tienes una instancia de `Name`, el valor cumple todas las reglas de negocio del nombre.

```java
public final class Name {
    private final String value;
    private Name(String value) { this.value = value; }

    public static Validation<Name> of(String field, String raw) {
        if (raw == null || raw.isBlank()) return Validation.invalidOne(field, "REQUIRED");
        if (raw.length() > 100)           return Validation.invalidOne(field, "INVALID_SIZE");
        return Validation.valid(new Name(raw.trim()));
    }

    public String value() { return value; }
}
```

Reglas: `final class`, constructor privado, `of()` devuelve `Validation<VO>` (nunca lanza excepciones).

### ErrorResponse

```json
{ "status": 404, "code": "CAT_NOT_FOUND", "timestamp": "2026-05-14T10:30:00" }
{ "status": 422, "code": "VALIDATION_FAILED", "violations": [{"field":"name","code":"REQUIRED"}], "timestamp": "..." }
```

Factories: `ErrorResponse.of(DomainError)` para errores de dominio, `ErrorResponse.internalError()` para 500s no gestionados.

---

## Variables de entorno

Copia `.env.example` a `.env` y rellena todos los valores. Las variables marcadas como **requeridas** no tienen fallback en docker-compose ni en el perfil `%prod` de Quarkus.

**Infraestructura (docker-compose)**

| Variable                | Requerida | Descripción                      |
|-------------------------|-----------|----------------------------------|
| `POSTGRES_USER`         | ✅        | Usuario PostgreSQL                |
| `POSTGRES_PASSWORD`     | ✅        | Contraseña PostgreSQL             |
| `POSTGRES_DB`           | ✅        | Nombre de la base de datos        |
| `MINIO_ROOT_USER`       | ✅        | Access key de MinIO               |
| `MINIO_ROOT_PASSWORD`   | ✅        | Secret key de MinIO (mín. 16 chars) |
| `MINIO_DEFAULT_BUCKETS` | ✅        | Nombre del bucket MinIO           |

**Servicios (application.properties)**

| Variable                    | Por defecto en dev       | Descripción                              |
|-----------------------------|--------------------------|------------------------------------------|
| `DB_USER`                   | —                        | Usuario PostgreSQL (inyectado en runtime)|
| `DB_PASSWORD`               | —                        | Contraseña PostgreSQL                    |
| `DB_HOST`                   | `localhost`              | Host PostgreSQL                          |
| `DB_PORT`                   | `5432`                   | Puerto PostgreSQL                        |
| `DB_NAME`                   | `kitties`                | Base de datos PostgreSQL                 |
| `GRPC_INTERNAL_SECRET`      | `kitties-dev-secret`     | Secreto compartido para canal gRPC auth↔user; **requerido en prod** |
| `STORAGE_SERVICE_URL`       | `http://localhost:8084`  | URL del storage service                  |
| `USER_SERVICE_HOST`         | `localhost`              | Host del user service (gRPC)             |
| `KAFKA_HOST`                | `localhost`              | Host del broker Kafka                    |
| `KAFKA_PORT`                | `9092`                   | Puerto del broker Kafka                  |
| `MAIL_HOST`                 | `localhost`              | Host SMTP                                |
| `MAIL_PORT`                 | `1025`                   | Puerto SMTP                              |
| `FRONTEND_URL`              | `http://localhost:5173`  | URL base del frontend (usado en emails de activación) |

**Solo producción**

| Variable                    | Ruta de montaje por defecto         | Descripción                            |
|-----------------------------|-------------------------------------|----------------------------------------|
| `JWT_PRIVATE_KEY_LOCATION`  | `/run/secrets/privateKey.pem`       | Ruta de la clave privada RSA (auth-service) |
| `JWT_PUBLIC_KEY_LOCATION`   | `/run/secrets/publicKey.pem`        | Ruta de la clave pública RSA (resto de servicios) |
| `KITTIES_ID_NUMBER_KEY`     | —                                   | Clave AES-256-GCM para cifrado DNI/NIE (Base64, 32 bytes). **Requerida en prod.** Generar con `openssl rand -base64 32`. |

**Herramientas de desarrollo:**

| Herramienta   | URL                        |
|---------------|----------------------------|
| MinIO Console | http://localhost:9001       |
| MailHog       | http://localhost:8025       |
| Kafka UI      | http://localhost:8008       |

---

## Roadmap

> **Modelo de negocio**: Kitties es B2B2C — las protectoras son el cliente de pago, los adoptantes son el usuario final. El dashboard y las herramientas de gestión para protectoras son el producto principal; el portal de adopción es el canal que aporta valor a las protectoras.

---

### Prioridad 1 — Fundación (prerequisito para todo)

- [x] Todos los servicios implementados con flujo de adopción completo
- [x] Tests de integración y unitarios para todos los servicios (523 total, ratio Tests/CC 0,48)
- [x] **Mejora de cobertura de tests** — dos iteraciones: cerradas todas las brechas de cobertura cero en lógica de negocio; Tests/CC ≥ 0,37 en todos los servicios (subido desde 0,13 en schedule-service). Ver `test-coverage-report-2026-05-15.md`.
- [x] **DIP — `Panache.withTransaction` inline eliminado** — todas las operaciones de escritura viven en beans `*WriteService` dedicados con `@WithTransaction`. Las llamadas estáticas de Panache en capas de servicio/resource están prohibidas; es un invariante arquitectónico aplicado.
- [x] Value Objects (`Email`, `ActivationToken`)
- [x] Roles de usuario (`User`, `Organization`, `Admin`)
- [x] Auditoría de seguridad completada (11 vulnerabilidades encontradas y corregidas, puntuación 5.5 → 8.5/10)
- [x] JaCoCo configurado en todos los módulos
- [x] Cobertura de instrucción del gateway-service al 100%
- [x] **CI/CD con GitHub Actions** — matriz de tests en todos los servicios en cada push; push de imágenes a Docker Hub al mergear a `main`
- [x] **Flyway** — migraciones SQL versionadas por servicio
- [x] **Docker Compose de producción** — 10 servicios + Nginx (TLS) + renovación automática con Certbot
- [x] **Observabilidad** — trazas OpenTelemetry + métricas Micrometer en cada servicio. Grafana Alloy recopila y reenvía a Grafana Cloud

### Prioridad 2 — Dashboard para protectoras (núcleo del negocio)

Las protectoras desconfían de plataformas que las automatizan y las sacan del control. La propuesta de valor es darles mejores herramientas para gestionar su propio trabajo, no reemplazarlo.

- [x] Dashboard de gestión de protectoras — vista del pipeline de adopción, estadísticas de inventario de gatos, historial de casos por animal, estadísticas del pipeline de ingreso
- [ ] Multi-usuario por protectora — el admin puede invitar voluntarios con roles limitados
- [ ] Analíticas de protectora — tasas de adopción, tiempo medio hasta la adopción, motivos de rechazo
- [ ] Seguimiento post-adopción — la protectora solicita actualizaciones (fotos, estado de salud) a los adoptantes
- [ ] Valoraciones post-adopción — el adoptante valora la protectora y viceversa
- [ ] **payment-service** — Stripe Connect (marketplace entre protectoras y adoptantes), Stripe Subscriptions (facturación recurrente de patrocinio)
- [ ] Precios extensibles (tier freemium para protectoras pequeñas, tiers de pago para volumen y analíticas)

### Prioridad 3 — Inteligencia

- [ ] **form-analysis-service** — análisis del formulario de cribado basado en LLM. La arquitectura (async Kafka + llamada externa) ya está en su lugar; el único cambio necesario es el motor.

### Prioridad 4 — Comunicación

- [ ] **MVP de notificaciones** — extender `notification-service` para cubrir todas las transiciones de estado de adopción
- [x] **chat-service** (base REST) — puerto 8089. Modelo de conversación, endpoints REST, endpoint interno para abrir conversaciones, bloqueo de usuarios en el chat
- [ ] **chat-service** transporte en tiempo real — WebSocket sobre la superficie REST existente
- [ ] **Abrir chat automáticamente al aprobar ingreso** — conectar `IntakeRequestService.approve(...)` en adoption-service para llamar a `POST /chats/internal/conversations`
- [ ] **Ban global de usuario** (`UserStatus.Banned`) — diferido hasta el primer caso real de abuso

### Prioridad 5 — Crecimiento de adoptantes y autofinanciación

- [ ] **Patrocinio de gatos senior** — pago mensual recurrente para patrocinar los costes de una protectora
- [ ] **Comisión de gestión en adopciones** — pequeña comisión de plataforma cobrada al adoptante al confirmar la adopción
- [ ] **Kits de inicio para nuevos adoptantes** — bundle de productos curado ofrecido en el checkout
- [ ] Valoraciones post-adopción
- [ ] **kitties-cli** — binario nativo con Quarkus + Picocli

### Seguridad

- [x] Protección IDOR en `GET /adoptions/{id}` (propiedad reforzada)
- [x] RBAC vía claim `groups` del JWT + `@RolesAllowed` en todos los endpoints de adopción
- [x] Canal gRPC interno protegido con interceptores de secreto compartido
- [x] Listener Kafka `EXTERNAL` restringido a `127.0.0.1`
- [x] Bean Validation en todos los DTOs de entrada
- [x] Rate limiting en endpoints de login, refresh y upload
- [x] Token de activación movido de query param a POST body
- [x] Credenciales eliminadas de docker-compose (`.env` + `.env.example`)
- [x] Claves JWT externalizadas en el perfil `%prod`
- [x] Validación de magic bytes MIME en la subida — rechaza ficheros cuyo contenido no coincide con el Content-Type declarado
- [x] `X-Content-Type-Options: nosniff` inyectado en todas las respuestas del gateway
- [x] **DNI/NIE cifrado en reposo** — `adoption_forms.id_number` almacenado como texto cifrado AES-256-GCM
- [ ] Corrección del bucket compartido en `IpRateLimiter` entre endpoints
- [ ] Rate limiting distribuido con Redis
- [ ] Expiración del token de activación
- [ ] Log de auditoría para acciones sensibles
- [ ] HTTPS entre servicios en producción
- [ ] Endpoint JWKS para rotación de claves sin downtime
- [ ] Imágenes Docker corriendo como non-root
- [ ] OWASP Dependency Check + Trivy en CI

### Deuda técnica

**Arquitectura**

- [ ] **HTTPS entre servicios** en producción. El gateway termina TLS; el tráfico interno entre servicios es HTTP plano dentro de la red privada.
- [ ] **Interfaces de repositorio como puertos (DIP)** — las operaciones de escritura ya están extraídas a beans `*WriteService` con `@WithTransaction`; quedan las abstracciones de repositorio (interfaces inyectadas como puertos). Próximo candidato: `CatRepository`, `AdoptionRequestRepository`.
- [ ] **`AdoptionService` God Object** — 381 líneas mezclando dominio, eventos Kafka y exportación de datos. Tres correcciones concretas antes de producción:
  1. Extraer `@Incoming("adoption-form-analysed") onFormAnalysed()` a un bean dedicado `AdoptionEventHandler` (SRP).
  2. Reemplazar la instancia local `new ObjectMapper()` dentro de `onFormAnalysed` por `@Inject ObjectMapper` — la instancia local no lleva la configuración global de Jackson (módulos registrados, etc.).
  3. Corregir el fire-and-forget de persist en torno a la línea 179: `subscribe().with(v -> {}, e -> {})` silencia los fallos de persistencia. Como mínimo loguear el error; idealmente encadenar el persist reactivamente.
- [ ] **Value Objects** para los DTOs de request restantes — `cat-service` ya usa el patrón declarativo `validate()` con VOs. Extender a `adoption-service`, `organization-service`, `chat-service` y `user-service` de forma incremental al tocar esos DTOs.
- [ ] **Ficheros `.proto` duplicados por servicio** — decisión intencionada (2026-04-27): se intentó un módulo compartido `kitties-proto` pero falló por problemas de codegen gRPC de Quarkus al importar protos desde un JAR. Replantear solo si hay ≥ 3 protos compartidos y la duplicación duele activamente.
- [ ] **`findAlternatives` incluye la organización rechazante** — `IntakeRequestService.findAlternatives` filtra por `o.id() == excludeOrgId`, pero `o.id()` es el id de la entidad `Organization` mientras que `excludeOrgId` es el JWT sub del usuario de organización. La causa raíz es que `organizationId` no es consistentemente entity-id vs user-sub en `Cat`, `AdoptionRequest` e `IntakeRequest`. La corrección requiere alinear la convención de IDs en todo el sistema. (`feat/fix-intake-alternatives-exclude`)
- [ ] **Eventos de desactivación de usuario/organización** — `UserService.deactivateUser` solo establece `status = Inactive`; no se emite evento Kafka. Las solicitudes activas quedan huérfanas. Patrón acordado: `user-service` emite `user-deactivated`, `organization-service` emite `organization-deactivated`; `adoption-service` (y otros) suscriben y cancelan las entidades relacionadas. (`feat/user-deactivated-event`, `feat/org-deactivated-event`)
- [ ] **Ingreso dentro de adoption-service** — colocado ahí como atajo de v1. Extraer a `intake-service` si el dominio crece significativamente; mantener los paquetes `intake/` y `adoption/` estrictamente separados mientras tanto.
- [ ] **Extracción de OrganizationMember** — beans ya separados; la extracción completa a microservicio requiere una saga Kafka para `Organization.create()`. Diferido hasta que el acoplamiento duela.

**Paginación**

Ningún endpoint de colección tiene paginación. Con datos reales esto agotará memoria y añadirá latencia. Orden de prioridad:

- [ ] `GET /cats` — búsqueda pública, crecimiento sin límite.
- [ ] `GET /adoptions/organization` — historial completo de una protectora.
- [ ] `GET /chats/{id}/messages` — historial de conversación.
- [ ] `GET /intake-requests/organization` — todos los intakes de una org.

Enfoque planificado: query params `page` + `size`, `PageResponse<T>` con metadatos (`total`, `totalPages`). Considerar extraer `PageRequest` (VO con clamping de size) y `PageResponse<T>` a un módulo `page-mon` junto a `either-mon` — actualmente `PageResponse<T>` está duplicado solo en cat-service.

**Calidad de tests**

- [ ] **Gate Tests/CC en CI** — check mínimo de JaCoCo en la matriz de GitHub Actions para que el ratio global 0,48 no pueda regresar. Ver `test-coverage-report-2026-05-15.md` para los baselines por servicio.
- [ ] **`AdoptionService` — ampliar tests** — CC=46, 23 tests (ratio 0,50). Sin cubrir: `verifyCatActive` con CircuitBreaker abierto, `findAlternatives` con fallo de lookup en org-service.
- [ ] **`IpRateLimiter` — ampliar tests** — 1 test para CC≈15. Faltan: bucket compartido entre endpoints, reset de ventana deslizante, clave por email en login.

---

## Privacidad y protección de datos

Ver [PRIVACY.md](PRIVACY.md) para la auditoría completa de conformidad con RGPD / LOPDGDD: inventario de datos, brechas identificadas y plan de acción priorizado.

---

## Licencia

Repositorio privado. Todos los derechos reservados.
