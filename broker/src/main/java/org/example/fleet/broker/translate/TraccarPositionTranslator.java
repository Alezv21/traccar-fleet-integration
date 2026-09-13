package org.example.fleet.broker.translate;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.fleet.model.VehiclePosition;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Translator from Traccar position JSON to canonical VehiclePosition record.
 *
 * EIP Pattern:
 * Message Translator - converts Traccar's wire format into
 * the internal canonical model used by the integration platform.
 */
public class TraccarPositionTranslator {

    public VehiclePosition translate(JsonNode traccarJson) throws Exception {

        JsonNode position = traccarJson.get("position");

        if (position == null || position.isNull()) {
            throw new IllegalArgumentException(
                "El payload de Traccar no contiene position"
            );
        }

        /*
         * IMPORTANTE:
         *
         * position.deviceId es el ID interno numérico de Traccar.
         *
         * Para nuestra arquitectura utilizamos device.uniqueId,
         * porque ese identificador permanece estable y será el mismo
         * almacenado en PostgreSQL como repartidor.device_id.
         */
        String deviceId = null;

        JsonNode device = traccarJson.get("device");

        if (device != null
                && device.hasNonNull("uniqueId")
                && !device.get("uniqueId").asText().isBlank()) {

            deviceId = device.get("uniqueId").asText();

        } else if (position.hasNonNull("deviceId")) {

            // Fallback por compatibilidad
            deviceId = position.get("deviceId").asText();
        }

        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException(
                "No se pudo determinar el deviceId del dispositivo"
            );
        }

        double latitude = position.get("latitude").asDouble();
        double longitude = position.get("longitude").asDouble();

        double speedKnots = position.path("speed").asDouble(0);
        double speedKmh = speedKnots * 1.852;

        double course = position.path("course").asDouble(0);

        boolean valid = position.path("valid").asBoolean(true);

        JsonNode fixTimeNode = position.get("fixTime");

        String timestamp;

        if (fixTimeNode == null || fixTimeNode.isNull()) {

            timestamp = Instant.now().toString();

        } else if (fixTimeNode.isNumber()) {

            timestamp = Instant
                .ofEpochMilli(fixTimeNode.asLong())
                .toString();

        } else {

            timestamp = fixTimeNode.asText();
        }

        Map<String, Object> attributes = new HashMap<>();

        JsonNode attrsNode = position.get("attributes");

        if (attrsNode != null && attrsNode.isObject()) {

            attrsNode.fields().forEachRemaining(entry ->
                attributes.put(
                    entry.getKey(),
                    entry.getValue().asText()
                )
            );
        }

        /*
         * Conservamos también el ID interno de Traccar
         * para trazabilidad.
         */
        if (position.hasNonNull("deviceId")) {
            attributes.put(
                "traccarDeviceId",
                position.get("deviceId").asText()
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