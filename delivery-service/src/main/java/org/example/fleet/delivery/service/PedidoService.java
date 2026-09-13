package org.example.fleet.delivery.service;

import org.example.fleet.delivery.model.CrearPedidoRequest;
import org.example.fleet.delivery.model.PedidoCreadoResponse;
import org.example.fleet.delivery.model.TrackingResponse;
import org.example.fleet.delivery.repository.PedidoRepository;

import java.util.Optional;

public class PedidoService {

    private final PedidoRepository repository;

    public PedidoService(
        PedidoRepository repository
    ) {

        this.repository = repository;
    }

    public PedidoCreadoResponse crearPedido(
        CrearPedidoRequest request
    ) throws Exception {

        validarRequest(request);

        if (
            !repository.existeRepartidor(
                request.repartidorDeviceId()
            )
        ) {

            throw new IllegalArgumentException(
                "Repartidor desconocido: "
                    + request.repartidorDeviceId()
            );
        }

        int radioLlegada =
            request.radioLlegadaM() == null
                ? 150
                : request.radioLlegadaM();

        if (radioLlegada <= 0) {

            throw new IllegalArgumentException(
                "radioLlegadaM debe ser mayor a cero"
            );
        }

        return repository.crearPedido(
            request,
            radioLlegada
        );
    }

    public Optional<TrackingResponse> obtenerTracking(
        String pedidoId
    ) throws Exception {

        if (
            pedidoId == null
            || pedidoId.isBlank()
        ) {

            throw new IllegalArgumentException(
                "El id del pedido es obligatorio"
            );
        }

        return repository.obtenerTracking(
            pedidoId
        );
    }

    private void validarRequest(
        CrearPedidoRequest request
    ) {

        if (request == null) {
            throw new IllegalArgumentException(
                "El cuerpo de la solicitud es obligatorio"
            );
        }

        validarTexto(
            request.id(),
            "id"
        );

        validarTexto(
            request.clienteNombre(),
            "clienteNombre"
        );

        validarTexto(
            request.clienteMsisdn(),
            "clienteMsisdn"
        );

        validarTexto(
            request.repartidorDeviceId(),
            "repartidorDeviceId"
        );

        if (
            request.latDestino() < -90
            || request.latDestino() > 90
        ) {

            throw new IllegalArgumentException(
                "latDestino inválida"
            );
        }

        if (
            request.lonDestino() < -180
            || request.lonDestino() > 180
        ) {

            throw new IllegalArgumentException(
                "lonDestino inválida"
            );
        }
    }

    private void validarTexto(
        String value,
        String field
    ) {

        if (
            value == null
            || value.isBlank()
        ) {

            throw new IllegalArgumentException(
                "El campo "
                    + field
                    + " es obligatorio"
            );
        }
    }
}