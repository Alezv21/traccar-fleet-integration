package org.example.fleet.broker.translate;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.fleet.model.VehicleEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Translator from Traccar event JSON to canonical VehicleEvent record.
 *
 * EIP Pattern:
 * Message Translator - converts Traccar's event format into
 * the internal canonical model.
 */
public class TraccarEventTranslator {

    public VehicleEvent translate(JsonNode traccarJson) throws Exception {

        JsonNode event = traccarJson.get("event");

        if (event == null || event.isNull()) {
            throw new IllegalArgumentException(
                "El payload de Traccar no contiene event"
            );
        }

        String deviceId = null;

        JsonNode device = traccarJson.get("device");

        if (device != null
                && device.hasNonNull("uniqueId")
                && !device.get("uniqueId").asText().isBlank()) {

            deviceId = device.get("uniqueId").asText();

        } else if (event.hasNonNull("deviceId")) {

            deviceId = event.get("deviceId").asText();
        }

        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException(
                "No se pudo determinar el deviceId del evento"
            );
        }

        String eventType = event.path("type").asText();

        JsonNode eventTimeNode = event.get("eventTime");

        String timestamp;

        if (eventTimeNode == null || eventTimeNode.isNull()) {

            timestamp = Instant.now().toString();

        } else if (eventTimeNode.isNumber()) {

            timestamp = Instant
                .ofEpochMilli(eventTimeNode.asLong())
                .toString();

        } else {

            timestamp = eventTimeNode.asText();
        }

        String positionId = null;

        if (event.hasNonNull("positionId")) {
            positionId = event.get("positionId").asText();
        }

        String geofenceId = null;

        if (event.hasNonNull("geofenceId")) {
            geofenceId = event.get("geofenceId").asText();
        }

        Map<String, Object> attributes = new HashMap<>();

        JsonNode attrsNode = event.get("attributes");

        if (attrsNode != null && attrsNode.isObject()) {

            attrsNode.fields().forEachRemaining(entry ->
                attributes.put(
                    entry.getKey(),
                    entry.getValue().asText()
                )
            );
        }

        if (event.hasNonNull("deviceId")) {
            attributes.put(
                "traccarDeviceId",
                event.get("deviceId").asText()
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