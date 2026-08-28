package org.example.fleet.mediator.translate;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.fleet.model.VehiclePosition;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Translator from Traccar position JSON to canonical VehiclePosition record.
 * EIP Pattern: Message Translator — converts from Traccar's wire format to internal canonical model.
 */
public class TraccarPositionTranslator {

    public VehiclePosition translate(JsonNode traccarJson) throws Exception {
        JsonNode position = traccarJson.get("position");
        String deviceId = position.get("deviceId").asText();
        double latitude = position.get("latitude").asDouble();
        double longitude = position.get("longitude").asDouble();
        double speedKnots = position.get("speed").asDouble(0);
        double speedKmh = speedKnots * 1.852; // conversion: knots to km/h
        double course = position.get("course").asDouble(0);
        boolean valid = position.get("valid").asBoolean(true);
        long fixTimeMs = position.get("fixTime").asLong();
        String timestamp =  fixTimeMs > 0 ? Instant.ofEpochMilli(fixTimeMs).toString() :
                position.get("fixTime").asText();

        // Passthrough attributes (battery, ignition, motion, etc.)
        Map<String, Object> attributes = new HashMap<>();
        JsonNode attrsNode = position.get("attributes");
        if (attrsNode != null && attrsNode.isObject()) {
            attrsNode.fields().forEachRemaining(entry ->
                attributes.put(entry.getKey(), entry.getValue().asText())
            );
        }

        return new VehiclePosition(
            "1.0",
            UUID.randomUUID().toString(),
            deviceId,
            timestamp,
            latitude,
            longitude,
            speedKmh,
            course,
            valid,
            attributes
        );
    }
}
