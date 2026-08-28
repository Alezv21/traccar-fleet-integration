package org.example.fleet.broker.translate;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.fleet.model.VehicleEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Translator from Traccar event JSON to canonical VehicleEvent record.
 * EIP Pattern: Message Translator — converts from Traccar's wire format to internal canonical model.
 */
public class TraccarEventTranslator {

    public VehicleEvent translate(JsonNode traccarJson) throws Exception {
        JsonNode event = traccarJson.get("event");

        String deviceId = event.get("deviceId").asText();
        String eventType = event.get("type").asText();
        long eventTimeMs = event.get("eventTime").asLong();
        String timestamp = eventTimeMs > 0 ? Instant.ofEpochMilli(eventTimeMs).toString() : event.get("eventTime").asText();

        String positionId = null;
        if (event.has("positionId") && !event.get("positionId").isNull()) {
            positionId = String.valueOf(event.get("positionId").asInt());
        }

        String geofenceId = null;
        if (event.has("geofenceId") && !event.get("geofenceId").isNull()) {
            geofenceId = String.valueOf(event.get("geofenceId").asInt());
        }

        // Passthrough attributes
        Map<String, Object> attributes = new HashMap<>();
        JsonNode attrsNode = event.get("attributes");
        if (attrsNode != null && attrsNode.isObject()) {
            attrsNode.fields().forEachRemaining(entry ->
                attributes.put(entry.getKey(), entry.getValue().asText())
            );
        }

        return new VehicleEvent(
            "1.0",
            UUID.randomUUID().toString(),
            deviceId,
            eventType,
            timestamp,
            positionId,
            geofenceId,
            attributes
        );
    }
}
