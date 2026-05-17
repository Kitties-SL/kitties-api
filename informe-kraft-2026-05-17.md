# Informe: Migración a Kafka KRaft

**Fecha:** 2026-05-17  
**Contexto:** Evaluación de viabilidad para migrar el stack de Kafka en Kitties de ZooKeeper a KRaft  
**Estado actual:** `confluentinc/cp-kafka:7.5.0` + `confluentinc/cp-zookeeper:7.5.0`

---

## Resumen ejecutivo

KRaft no es solo "una mejora" — **ZooKeeper ya no existe en Kafka 4.x**. Desde marzo de 2025, Kafka 4.0 lo eliminó por completo. KRaft es la nueva realidad obligatoria para cualquier clúster moderno.

La migración en Kitties tiene riesgo prácticamente nulo: **cero cambios en código Java**, un único fichero Docker Compose a refactorizar, y el beneficio inmediato de eliminar un contenedor innecesario del stack.

---

## 1. Cronología de KRaft

| Hito | Versión | Fecha |
|------|---------|-------|
| KRaft disponible (experimental) | Kafka 2.8 | abr 2021 |
| KRaft marcado production-ready para nuevos clústeres | Kafka 3.3.1 | oct 2022 |
| Migración ZooKeeper→KRaft in-place marcada production-ready | Kafka 3.7 | 2024 |
| **ZooKeeper eliminado del codebase** | **Kafka 4.0** | **mar 2025** |
| Versión actual estable | Kafka 4.2.0 | dic 2025 |

KRaft lleva **más de tres años en producción** en miles de empresas. No es tecnología experimental.

---

## 2. Qué mejora KRaft respecto a ZooKeeper

### Rendimiento

- **8× mejora de throughput** en escenarios con alta carga de metadatos (benchmarks en producción, reportados por OSO Engineers).
- **Failover del controller casi instantáneo** (milisegundos vs. varios segundos con ZooKeeper).
- **Soporte para millones de particiones** — ZooKeeper se degradaba notablemente a partir de ~200k particiones por la latencia de escritura en znodes.

### Operacional

- **Reducción del 30-40% del footprint de infraestructura** — desaparece el clúster ZooKeeper separado.
- **Un sistema de consenso menos** que monitorizar, escalar y securizar.
- Kafka gestiona sus propios metadatos internamente vía el protocolo Raft, sin dependencias externas.

### Simplicidad de despliegue

- En modo single-node (dev/staging), un solo contenedor hace de broker y controller.
- El modelo mental es más limpio: todo Kafka es Kafka, no "Kafka + ZooKeeper + coordinación entre ambos".

---

## 3. Compatibilidad con el stack de Kitties

> **KRaft es completamente transparente para los clientes Java.**

El cambio afecta únicamente a la capa de gestión de metadatos del clúster (quién es el controller, dónde están los líderes de partición). El protocolo productor/consumidor no cambia.

| Capa | Impacto |
|------|---------|
| `quarkus-smallrye-reactive-messaging-kafka` | **Ninguno** — usa la API estándar de Kafka |
| `@Incoming` / `@Outgoing` | **Ninguno** |
| `application.properties` (`bootstrap.servers`, topics, grupos) | **Ninguno** |
| Código Java de todos los servicios | **Cero cambios** |
| Tests de integración (`@QuarkusTest`) | **Ninguno** — Quarkus levanta Kafka embebido |

**Quarkus 3.34.3 + SmallRye Reactive Messaging es 100% compatible con KRaft.** La librería cliente de Kafka (usada internamente por SmallRye) soporta KRaft desde la versión 2.x. Kafka 4.x requiere cliente ≥ 2.1, y Quarkus 3.x lleva una versión muy superior.

---

## 4. Opciones de imagen Docker

### Opción A — Confluent Platform 8.0 (mantener cp-kafka, actualizar tag)

- **Pros:** conocido, sin cambio de vendor, `kafka-ui` de Confluent bien integrado.
- **Contras:** imagen más pesada (~800 MB), dependencia de Confluent para actualizaciones, CP 8.0 aún relativamente nueva.

```yaml
# docker-compose.yml — eliminar el servicio zookeeper por completo
kafka:
  image: confluentinc/cp-kafka:8.0.0
  ports:
    - "127.0.0.1:9092:9092"
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: INTERNAL://kafka:29092,EXTERNAL://localhost:9092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    KAFKA_INTER_BROKER_LISTENER_NAME: INTERNAL
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"
```

### Opción B — `apache/kafka` oficial ⭐ Recomendada

- **Pros:** imagen oficial de la ASF (~400 MB), sin vendor lock-in, alineada con la versión de referencia, actualizaciones en paralelo con los releases de Kafka.
- **Contras:** configuración de variables ligeramente diferente a la de Confluent (hay que actualizar el compose).

```yaml
kafka:
  image: apache/kafka:4.0.0
  ports:
    - "127.0.0.1:9092:9092"
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,EXTERNAL://localhost:9092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"
```

### Opción C — `bitnami/kafka` (config más ergonómica)

- **Pros:** convenciones de configuración más limpias (`KAFKA_CFG_*`), bien documentada, bitnami tiene buena reputación en imágenes de desarrollo.
- **Contras:** otro vendor (VMware/Broadcom), a veces va ligeramente por detrás de los releases oficiales.

```yaml
kafka:
  image: bitnami/kafka:4.0.0
  ports:
    - "127.0.0.1:9092:9092"
  environment:
    KAFKA_CFG_NODE_ID: 0
    KAFKA_CFG_PROCESS_ROLES: controller,broker
    KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 0@kafka:9093
    KAFKA_CFG_LISTENERS: PLAINTEXT://:29092,EXTERNAL://:9092,CONTROLLER://:9093
    KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,EXTERNAL://localhost:9092
    KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT
    KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
```

---

## 5. Configuración concreta por entorno

### 5.1 Producción (`docker-compose.prod.yml`) ⭐

El entorno de producción tiene cuatro particularidades respecto a dev que obligan a una config diferente.

**Eliminar por completo el servicio `zookeeper`** (líneas 73–89 del archivo actual).

**Nuevo bloque `kafka`:**

```yaml
kafka:
  image: apache/kafka:4.0.0
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    KAFKA_HEAP_OPTS: "-Xmx256m -Xms128m"
    KAFKA_LOG_RETENTION_HOURS: "12"
    KAFKA_LOG_SEGMENT_BYTES: "10485760"
  volumes:
    - kafka_data:/var/lib/kafka/data
  healthcheck:
    test: ["CMD-SHELL", "/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:29092"]
    interval: 15s
    timeout: 35s
    retries: 5
  networks:
    - kitties-net
  deploy:
    resources:
      limits:
        memory: 384M
        cpus: '0.75'
```

**Añadir `kafka_data` a la sección `volumes`:**

```yaml
volumes:
  postgres_data:
  minio_data:
  certbot_certs:
  certbot_www:
  kafka_data:      # ← nuevo
```

**Diferencias clave respecto al bloque de dev:**

| Aspecto | Prod | Dev |
|---------|------|-----|
| Listeners | Solo `PLAINTEXT` + `CONTROLLER` (red interna) | Además `EXTERNAL` (exposición local a `:9092`) |
| Healthcheck | `/opt/kafka/bin/kafka-broker-api-versions.sh` | Ídem (sin `cub`, que es exclusivo de Confluent) |
| `CLUSTER_ID` | **No se fija** — Kafka lo genera en el primer arranque y lo persiste en el volumen | Se fija a un valor estático para sobrevivir `down && up` sin volumen |
| Volumen de datos | `kafka_data:/var/lib/kafka/data` — **crítico** para persistir el Cluster ID y los topics | Opcional en dev |

> **Por qué no fijar `CLUSTER_ID` en prod:** Kafka almacena el ID generado en el volumen persistente. Fijarlo manualmente en el compose supone un secreto más a gestionar y un riesgo si alguna vez se borra el volumen y se levanta con el mismo ID — el broker rechazaría unirse al quórum. El flujo correcto es: primer `docker compose up` genera el ID, el volumen lo persiste, todos los reinicios siguientes lo leen del disco.

**Ahorro de recursos al eliminar ZooKeeper:**

| Servicio | Memoria | CPU |
|---------|---------|-----|
| `zookeeper` (eliminado) | -160M | -0.25 |
| `kafka` (sin cambios) | 384M | 0.75 |
| **Total anterior** | **544M** | **1.0** |
| **Total nuevo** | **384M** | **0.75** |

---

### 5.2 Desarrollo (`docker-compose.yml`)

**Eliminar el servicio `zookeeper` y su `depends_on` en `kafka`.**

**Nuevo bloque `kafka`** (mantiene el listener `EXTERNAL` para acceso desde el host y el `CLUSTER_ID` fijo):

```yaml
kafka:
  image: apache/kafka:4.0.0
  ports:
    - "127.0.0.1:9092:9092"
  environment:
    KAFKA_NODE_ID: 1
    KAFKA_PROCESS_ROLES: broker,controller
    KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
    KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,EXTERNAL://localhost:9092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT
    KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
    KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    CLUSTER_ID: "MkU3OEVBNTcwNTJENDM2Qk"
```

**Actualizar `kafka-ui`** (eliminar la referencia a ZooKeeper):

```yaml
kafka-ui:
  environment:
    KAFKA_CLUSTERS_0_NAME: local
    KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092
    # Eliminar: KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
```

> **`CLUSTER_ID` fijo en dev:** sin volumen persistente, cada `docker compose down && up` generaría un nuevo ID y los offsets acumulados en memory quedarían desincronizados. El valor `MkU3OEVBNTcwNTJENDM2Qk` es un UUID base64 de 22 chars estándar (sacado de la doc oficial de Confluent; cualquier valor válido sirve).

---

## 6. Alcance del cambio

| Fichero | Cambio |
|---------|--------|
| `docker-compose.prod.yml` | Eliminar `zookeeper`, refactorizar `kafka`, añadir `kafka_data` en `volumes` |
| `docker-compose.yml` | Eliminar `zookeeper`, refactorizar `kafka`, actualizar `kafka-ui` |
| `docker-compose.local.yml` | Sin Kafka — sin cambios |
| Cualquier fichero Java | **Sin cambios** |
| `application.properties` | **Sin cambios** |

---

## 7. Evaluación de riesgo

| Riesgo | Probabilidad | Mitigación |
|--------|-------------|------------|
| Incompatibilidad cliente SmallRye | Muy baja | API estándar Kafka; testado con Quarkus 3.x |
| Topics perdidos al recrear el contenedor | Baja | `CLUSTER_ID` fijo + mismo volumen Docker |
| Problemas en e2e tests | Muy baja | Quarkus usa Testcontainers con imagen propia; independiente |
| Regresión en prod compose | Baja | Misma configuración que dev; cambio atómico y reversible |

---

## 8. Encaje en el roadmap actual

- El cambio pertenece a la **rama `fix/docker-compose-prod`** prevista (ya en el backlog).
- Es el momento idóneo: antes de que el stack llegue a producción real, la deuda de ZooKeeper se liquida limpiamente.
- Dado que no hay cambios Java, el PR es un diff pequeño y revisable en minutos.

---

## Decisión pendiente

La única pregunta real es **qué imagen usar**:

- **Opción A (cp-kafka:8.0)** si se quiere mantener el mismo vendor y minimizar fricción con kafka-ui.
- **Opción B (apache/kafka:4.0)** si se prefiere desacoplarse de Confluent y usar la imagen de referencia. ⭐ Recomendada.
- **Opción C (bitnami/kafka:4.0)** si se valora más la ergonomía de configuración.

Las tres son válidas. El código Java no distingue entre ellas.

---

## Fuentes

- [Apache Kafka 4.0.0 Release Announcement](https://kafka.apache.org/blog/2025/03/18/apache-kafka-4.0.0-release-announcement/)
- [KIP-833: Mark KRaft as Production Ready](https://cwiki.apache.org/confluence/display/KAFKA/KIP-833:+Mark+KRaft+as+Production+Ready)
- [Kafka 4.0: KRaft Simplifies Architecture — InfoQ](https://www.infoq.com/news/2025/04/kafka-4-kraft-architecture/)
- [Apache Kafka's KRaft Protocol: 8x Performance — OSO](https://oso.sh/blog/apache-kafkas-kraft-protocol-how-to-eliminate-zookeeper-and-boost-performance-by-8x/)
- [Kafka 4.0 KRaft Migration Guide 2026 — byteiota](https://byteiota.com/kafka-4-zookeeper-kraft-migration-guide-2/)
- [Kafka 4 + KRaft + Docker Compose — Medium](https://medium.com/@kinneko-de/kafka-4-kraft-docker-compose-874d8f1ffd9b)
- [ZooKeeper to KRaft Migration — Conduktor](https://www.conduktor.io/glossary/zookeeper-to-kraft-migration)
- [Apache Kafka 4.0.0 Released — SoftwareMill](https://softwaremill.com/apache-kafka-4-0-0-released-kraft-queues-better-rebalance-performance/)