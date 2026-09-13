package org.example.fleet.delivery.model;

public record TrackingResponse(

    String id,

    String estado,

    String repartidorDeviceId,

    String clienteNombre,

    String direccionTexto,

    double latDestino,

    double lonDestino,

    int radioLlegadaM,

    Double ultimaLatitud,

    Double ultimaLongitud,

    Double velocidadKmh,

    Double distanciaDestinoM,

    String timestampPosicion

) {
}
