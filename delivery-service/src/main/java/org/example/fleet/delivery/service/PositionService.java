package org.example.fleet.delivery.service;

import org.example.fleet.delivery.model.ProcesamientoPosicionResultado;
import org.example.fleet.delivery.repository.PedidoRepository;
import org.example.fleet.model.VehiclePosition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PositionService {

    private static final Logger LOG =
        LoggerFactory.getLogger(PositionService.class);

    private final PedidoRepository repository;

    public PositionService(
        PedidoRepository repository
    ) {

        this.repository = repository;
    }

    public void procesar(
        VehiclePosition position
    ) throws Exception {

        if (position == null) {
            return;
        }

        if (!position.valid()) {

            LOG.warn(
                "[Delivery] Posicion invalida ignorada. Device={}",
                position.deviceId()
            );

            return;
        }

        if (
            position.messageId() == null
            || position.messageId().isBlank()
        ) {

            throw new IllegalArgumentException(
                "VehiclePosition sin messageId"
            );
        }

        ProcesamientoPosicionResultado resultado =
            repository.procesarPosicion(position);

        switch (resultado.resultado()) {

            case "DUPLICADO" ->

                LOG.info(
                    "[Delivery] Mensaje duplicado ignorado. messageId={}",
                    position.messageId()
                );

            case "SIN_PEDIDO" ->

                LOG.info(
                    "[Delivery] Device {} no posee pedido activo",
                    position.deviceId()
                );

            case "PROCESADO" ->

                LOG.info(
                    "[Delivery] Pedido={} Device={} Estado={}->{} Distancia={}m",
                    resultado.pedidoId(),
                    position.deviceId(),
                    resultado.estadoAnterior(),
                    resultado.estadoActual(),
                    String.format(
                        "%.2f",
                        resultado.distanciaDestinoM()
                    )
                );

            default ->

                LOG.warn(
                    "[Delivery] Resultado desconocido: {}",
                    resultado.resultado()
                );
        }
    }
}