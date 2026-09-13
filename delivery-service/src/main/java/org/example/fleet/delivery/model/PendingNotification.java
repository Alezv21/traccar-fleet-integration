package org.example.fleet.delivery.model;

public record PendingNotification(

    long eventId,

    String pedidoId,

    String hito,

    String clienteNombre,

    String clienteMsisdn,

    String clienteFcmId

) {
}