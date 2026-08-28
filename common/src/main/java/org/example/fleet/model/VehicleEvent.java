package org.example.fleet.model;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical Data Model for vehicle events.
 * Implements the EIP "Canonical Data Model" pattern — a single, well-known format
 * for events (deviceOnline, deviceOffline, ignitionOn, etc.) understood by all consumers.
 */
public record VehicleEvent(
    String schemaVersion,
    String messageId,
    String deviceId,
    String eventType,
    String timestamp,
    String positionId,
    String geofenceId,
    Map<String, Object> attributes
) {}
