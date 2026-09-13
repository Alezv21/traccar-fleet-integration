package org.example.fleet.delivery.model;

public record CrearPedidoRequest(

    String id,

    String clienteNombre,

    String clienteMsisdn,

    String clienteFcmId,

    String direccionTexto,

    double latDestino,

    double lonDestino,

    Integer radioLlegadaM,

    String repartidorDeviceId

) {
}
