package org.example.fleet.model;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical Data Model for vehicle position updates.
 * Implements the EIP "Canonical Data Model" pattern — a single, well-known format
 * that all producers and consumers use to avoid tight coupling to Traccar's wire format.
 */
public record VehiclePosition(
    String schemaVersion,
    String messageId,
    String deviceId,
    String timestamp,
    double latitude,
    double longitude,
    double speedKmh,
    double course,
    boolean valid,
    Map<String, Object> attributes
) {}
