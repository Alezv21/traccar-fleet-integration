package org.example.fleet.delivery.model;

public record PedidoCreadoResponse(

    String id,

    String estado,

    String repartidorDeviceId,

    String mensaje

) {
}
