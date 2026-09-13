package org.example.fleet.delivery.model;

public record ProcesamientoPosicionResultado(

    String resultado,

    String pedidoId,

    String estadoAnterior,

    String estadoActual,

    Double distanciaDestinoM

) {
}