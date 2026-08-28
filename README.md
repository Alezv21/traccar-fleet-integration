# Traccar → Camel → Artemis (AMQP) Fleet Integration Example

A practical example demonstrating real-time vehicle fleet tracking data integration using Apache Camel as the messaging integrator, connecting Traccar (GPS tracking server) to ActiveMQ Artemis (message broker) via AMQP 1.0 protocol.

## Architecture Overview

```
Traccar (GPS data source)
    ↓ HTTP JSON (forward.url)
Camel Broker (Messaging Gateway + Content-Based Router + Message Translator)
    ↓ AMQP 1.0 Pub-Sub Channels
Artemis (Message Broker)
    ↓ AMQP Durable Subscriptions
Positions Consumer ← → Events Consumer
```

### EIP Patterns Used

| Component | Pattern | Description |
|-----------|---------|-------------|
| Broker | **Messaging Gateway** | HTTP endpoint abstracts Traccar from internal architecture |
| Broker | **Content-Based Router** | Classifies incoming payloads into position vs. event branches |
| Broker | **Message Translator** | Converts Traccar JSON to canonical internal models |
| Broker | **Canonical Data Model** | Shared `VehiclePosition` and `VehicleEvent` records |
| Broker | **Publish-Subscribe Channel** | AMQP topics (`vehicle.positions`, `vehicle.events`) |
| Consumers | **Durable Subscriber** | Maintain subscriptions even if consumer is offline |
| Consumers | **Message Filter** | Discard invalid positions (`valid=false`) |

## Prerequisites

- Docker and Docker Compose v5.0+
- Docker BuildKit enabled (default in Docker 24+)
- Gradle wrapper included (no separate Gradle installation required)
- Sufficient disk space for local cache sharing (`~/.gradle` and `~/.m2`)

## Quick Start

### 0. Local compilation (optional, before Docker)

If you want to compile locally first (helpful for development):

```bash
cd example/traccar-fleet-integration

# Using gradle from sdkman
sdk env 
gradle clean build -x test

```

Generated JARs appear in `*/build/libs/` for each module.

### 1. Set up environment variables

```bash
cd example/traccar-fleet-integration
cp .env.example .env
# Edit .env if you want custom Artemis credentials (default: admin/admin123)
```

### 2. Build and start all services

```bash
docker compose build
docker compose up -d
docker compose ps  # Verify all containers are healthy
```

**First build** may take 2-3 minutes (downloads Gradle dependencies, seeds local caches). Subsequent builds are faster due to BuildKit cache mounts and the host's `~/.gradle` / `~/.m2` caches.

### 3. Bootstrap Traccar (one-time setup)

**Note:** Traccar has no default user/password. The first access presents a self-registration form.

#### Step 3a: Create Traccar admin account

1. Open http://localhost:8082 in a browser
2. Complete the initial admin registration form (email, password, name)
3. Use the password from `ADMIN_PASSWORD` in your `.env` file (default: `tracar`)

```bash
# Load environment variables (ADMIN_PASSWORD from .env)
source .env

curl -X POST \
  http://localhost:8082/api/users \
  -H "Content-Type: application/json" \
  -u "admin:${ADMIN_PASSWORD}" \
  -d '{
    "name": "John Doe",
    "email": "john.doe@example.com",
    "password": "SecurePassword123"
  }'
```

#### Step 3b: Create a test device via REST API

```bash
# Load environment variables (ADMIN_PASSWORD from .env)
source .env

# Create device with unique ID "demo-vehicle-1"
curl -u admin:${ADMIN_PASSWORD} -X POST http://localhost:8082/api/devices \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Demo Vehicle 1",
    "uniqueId": "demo-vehicle-1"
  }'
```

**Expected response:** JSON with device details including `id` field.

**If 401 Unauthorized:** The admin account wasn't created yet, or the password in `.env` doesn't match what you registered in Step 3a.

### 4. Verify the message pipeline end-to-end

#### Watch broker logs

```bash
docker compose logs -f broker
```

#### Simulate a GPS position update via OsmAnd protocol

In a **new terminal**, send a simulated GPS ping:

```bash
curl "http://localhost:5055/?id=demo-vehicle-1&lat=-25.2967&lon=-57.6359&speed=12&bearing=45&altitude=80&ignition=true"
```

Expected sequence in broker logs:
1. `[Broker] Received Traccar forward: ...` (raw HTTP body)
2. `[Broker] Classified as POSITION`
3. `[Broker] Published position to vehicle.positions`

#### Watch positions consumer

In a **third terminal**:

```bash
docker compose logs -f positions-consumer
```

Expected log:
```
[PositionsConsumer] Device: demo-vehicle-1, Lat: -25.2967, Lon: -57.6359, Speed: 22.224 km/h, Timestamp: 2026-08-26T...
```

#### Watch events consumer

In a **fourth terminal**:

```bash
docker compose logs -f events-consumer
```

Expected logs (automatically triggered by device connection):
```
[EventsConsumer] Device: demo-vehicle-1, Event: deviceOnline, Timestamp: 2026-08-26T...
```

#### Inspect Artemis message broker

Open http://localhost:8161 in a browser (Artemis console).

- Username/password: admin/admin123 (from `.env`)
- Navigate to **Addresses** tab
- Inspect `vehicle.positions` and `vehicle.events` (multicast routing type)
- Messages show as JSON body text (human-readable, not binary)

### 5. Send more GPS data

Try additional GPS pings with different locations to simulate movement:

```bash
# Simulate movement to a second location
curl "http://localhost:5055/?id=demo-vehicle-1&lat=-25.2900&lon=-57.6500&speed=18&bearing=90&altitude=100&ignition=true"

# Simulate stop (speed=0)
curl "http://localhost:5055/?id=demo-vehicle-1&lat=-25.2900&lon=-57.6500&speed=0&bearing=90&altitude=100&ignition=true"
```

### 6. Tear down

```bash
docker compose down -v
# -v removes volumes (Traccar's H2 database is ephemeral, safe to remove)
```

## Project Structure

```
traccar-fleet-integration/
├── common/                           # Canonical data models (shared by all services)
│   ├── build.gradle
│   └── src/main/java/org/example/fleet/model/
│       ├── VehiclePosition.java      # EIP: Canonical Data Model
│       └── VehicleEvent.java         # EIP: Canonical Data Model
│
├── broker/                           # HTTP gateway + AMQP publisher
│   ├── build.gradle
│   ├── Dockerfile                    # Multistage Gradle build with cache mounts
│   └── src/main/java/org/example/fleet/broker/
│       ├── MainApp.java
│       ├── routes/
│       │   └── IngestRoute.java      # EIP: Gateway + CBR + Translator
│       └── translate/
│           ├── TraccarPositionTranslator.java
│           └── TraccarEventTranslator.java
│
├── positions-consumer/               # AMQP durable subscriber (positions)
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/org/example/fleet/positions/
│       ├── MainApp.java
│       └── routes/PositionsConsumerRoute.java   # EIP: Durable Subscriber + Filter
│
├── events-consumer/                  # AMQP durable subscriber (events)
│   ├── build.gradle
│   ├── Dockerfile
│   └── src/main/java/org/example/fleet/events/
│       ├── MainApp.java
│       └── routes/EventsConsumerRoute.java      # EIP: Durable Subscriber
│
├── traccar/
│   └── traccar.xml                   # Traccar config with forwarder settings
│
├── settings.gradle                   # Module inclusion
├── build.gradle                      # Shared subprojects config (Camel BOM 4.5.0, Java 21)
├── compose.yaml                      # All services + BuildKit cache mount config
└── README.md                         # This file
```

## Technology Stack

- **Apache Camel 4.5.0** — EIP-based integration framework
- **ActiveMQ Artemis** — AMQP message broker
- **Apache Artemis Jackarta 2.31.2** — AMQP wire protocol
- **Traccar 5.x** — Open-source GPS tracking server
- **Java 21** — Latest LTS release
- **Gradle 9.2.1** — Build automation with BuildKit cache support

## Key Design Decisions

### 1. HTTP JSON Bridge (not native AMQP in Traccar)

**Rationale**: Traccar's native `forward.type=amqp` targets RabbitMQ (AMQP 0-9-1), which is incompatible with Artemis's AMQP 1.0 acceptor. The Camel broker acts as the HTTP-to-AMQP-1.0 bridge, allowing:
- Simple, battle-tested Traccar JSON forwarding (no Traccar code changes)
- Clean integration point for the Camel messaging pipeline
- Educational demonstration of the Messaging Gateway pattern

### 2. Canonical Data Model

**Rationale**: Java 21 `record` types with JSON marshalling avoid:
- Tight coupling to Traccar's wire format
- Schema registry complexity (overkill for a course example)
- Binary serialization (keeps messages human-readable in the Artemis console during demos)

### 3. Pub-Sub Topics (not Point-to-Point Queues)

**Rationale**: Durable subscriptions allow independent consumers to subscribe to the same topics without the broker needing to know about them in advance. This design supports future extensibility:
- Adding a `stop-analyzer` subscriber to `vehicle.positions` requires **zero changes** to the broker, positions-consumer, or events-consumer.
- Adding a `protobuf-converter` subscriber requires only **adding a new module** and deploying it — no coupling to existing services.

### 4. Gradle Multi-Module with BuildKit Cache Mounts

**Rationale**: 
- Shares host's `~/.gradle` and `~/.m2` caches into Docker builds via `--mount=type=bind,from=<named-context>`
- First build benefits from already-downloaded Camel BOM, Artemis JMS client, Qpid JMS client (if present locally)
- Subsequent builds use BuildKit's persistent cache mount (`type=cache`), eliminating download latency
- No dependency on Maven as the "canonical" build system — all builds are Gradle

## Extending the Example (Phase 2)

The following components can be added without modifying the core (broker, consumers):

### Stop Analyzer (Content Enricher + Postgres)
- New subscriber on `vehicle.positions`
- Correlates positions against a Postgres itinerary/stops table
- Publishes `vehicle_status_log` records (ON_ROUTE, AT_STOP, OFF_ROUTE)

### Protobuf Converter (Message Translator)
- New subscriber on `vehicle.positions`
- Converts canonical JSON to Google Protobuf binary format
- Publishes to `vehicle.positions.protobuf` queue for downstream systems

Both additions are isolated from the core MVP and can be developed and tested independently.

## Troubleshooting

### Broker fails to start: "Connection refused: amqp://artemis:5672"

- Ensure Artemis is healthy: `docker compose ps artemis` and check the HEALTHCHECK status.
- Confirm the network: `docker network inspect traccar-fleet-integration_fleet-net` should list all containers.
- Check Artemis logs: `docker compose logs artemis | tail -20`.

### Traccar device not receiving GPS pings

- Verify device was created: Log into Traccar UI (http://localhost:8082), **Devices** tab, should show "Demo Vehicle 1".
- Check device unique ID matches the curl command (must be exactly `demo-vehicle-1`).
- Verify OsmAnd protocol is enabled: `docker compose exec traccar cat /opt/traccar/conf/traccar.xml | grep osmand.port` should show `<entry key='osmand.port'>5055</entry>`.

### Messages not appearing in Artemis console

- Check Broker logs for translation errors: `docker compose logs broker | grep ERROR`.
- Verify AMQP connection: `docker compose logs broker | grep "amqp://"` should show successful connections.
- Check the firewall: AMQP port 5672 must be accessible from broker to artemis (should be automatic on the compose network).

### Docker build fails: "COPY . ." context too large

- Run `.dockerignore` is properly configured to exclude build artifacts and `.gradle` directories.
- Confirm: `cat .dockerignore` should include `**/build/` and `**/.gradle/`.

## Sources & References

- [Apache Camel Documentation](https://camel.apache.org/)
- [Enterprise Integration Patterns (EIP)](https://www.enterpriseintegrationpatterns.com/)
- [ActiveMQ Artemis AMQP Support](https://activemq.apache.org/components/artemis/documentation/)
- [Traccar Forwarding Documentation](https://www.traccar.org/forward/)
- [Docker BuildKit Documentation](https://github.com/moby/buildkit)

## License

This example is provided as part of the UCOM IS22026 Messaging course.
