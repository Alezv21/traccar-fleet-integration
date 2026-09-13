package org.example.fleet.delivery.model;

import java.util.Map;

public record OrderEventMessage(

    String eventId,

    String pedidoId,

    String tipo,

    String timestamp,

    Map<String, Object> payload

) {
}