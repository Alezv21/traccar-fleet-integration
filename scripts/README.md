# Integration Test Scripts

Test scripts for the Traccar → Camel → Artemis integration pipeline.

## Scripts

### `test-generator.py`
**Simulates continuous vehicle movement** by sending GPS positions to Traccar via the OsmAnd HTTP protocol.

Generates realistic waypoints (Asunción, Paraguay area) and sends periodic position updates with varying speed, ignition state, battery level, etc.

**Usage:**
```bash
# Default: demo-vehicle-1, localhost:5055, 1 update/sec, 40 km/h
python3 scripts/test-generator.py

# Custom device and speed
python3 scripts/test-generator.py --device-id my-vehicle --speed 60 --period 2
```

**Parameters:**
- `--device-id` — Device unique ID (must exist in Traccar)
- `--host` — Traccar OsmAnd server (default: `localhost:5055`)
- `--speed` — Device speed in km/h (default: 40)
- `--period` — Seconds between position updates (default: 1)

**Protocol:** OsmAnd HTTP GET (port 5055 in `traccar.xml`)

### `test-trips.py`
**Sends bulk position data** from a predefined trip (useful for replay/regression testing).

**Usage:**
```bash
# Run with defaults (device: demo-vehicle-1, password: tracar from .env)
python3 scripts/test-trips.py
```

### `test-integration.py`
**Sends device position data** from a predefined protocols.

**Usage:**
```bash
# Run with defaults (device: demo-vehicle-1, password: tracar from .env)
python3 scripts/test-integration.py -v
```

## Workflow

### Quick end-to-end test
```bash
# Terminal 1: Start all services
docker compose up -d
docker compose ps

# Terminal 2: Run integration test
cd scripts
python3 test-integration.py

# Terminal 3: Watch live logs
docker compose logs -f broker positions-consumer events-consumer
```

### Continuous position stream (for debugging)
```bash
# Terminal 2: Run position generator (runs forever until Ctrl+C)
cd scripts
python3 test-generator.py --device-id demo-vehicle-1 --speed 40 --period 1

# Terminal 3: Watch logs to see each position processed
docker compose logs -f broker
```
## Troubleshooting

### `test-integration.py` fails with "Connection refused"
- Check services are running: `docker compose ps`
- Verify Traccar is healthy: `curl http://localhost:8082/api/server`
- Check broker is up: `docker compose logs broker | tail -20`

### "404 Device not found" when sending positions
- Verify device exists in Traccar: http://localhost:8082 → Devices tab
- Ensure device `uniqueId` matches the `--device-id` parameter (case-sensitive)

### "401 Unauthorized" when creating device
- Traccar admin account not created yet — open http://localhost:8082 first
- Password in `.env` (ADMIN_PASSWORD) doesn't match what you registered

### Positions not appearing in broker logs
- Check OsmAnd protocol is enabled in `traccar.xml`: `<entry key='osmand.port'>5055</entry>`
- Verify Traccar forwarder is configured: `forward.url=http://broker:8080/traccar/ingest`
- Check broker logs: `docker compose logs broker | grep -i error`

### Broker receives position but doesn't classify/publish
- Check `PayloadClassifier` is detecting the message type: `docker compose logs broker | grep "Classified"`
- Verify AMQP connection to Artemis: `docker compose logs broker | grep amqp`
- Check Artemis is healthy: `curl -u admin:admin123 http://localhost:8161`

## Available Traccar Protocols (in `traccar.xml`)

The test scripts currently use **OsmAnd (port 5055)**, which is HTTP-based and easy to test from scripts.

Other protocols configured but require device-specific clients:
- **GPS103** (port 5001) — TK103-type trackers
- **TK103** (port 5002) — TK103 GPS trackers
- To test these, use their native protocol clients or TCP tools (nc, socat)

