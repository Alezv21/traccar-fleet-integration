package org.example.fleet.delivery.service;

import org.example.fleet.delivery.model.PendingNotification;
import org.example.fleet.delivery.model.PushNotification;
import org.example.fleet.delivery.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;

public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(
        NotificationRepository repository
    ) {

        this.repository = repository;
    }


    public List<PendingNotification> claimPending()
        throws Exception {

        return repository.claimPending(20);
    }


    public PushNotification crearPush(
        PendingNotification notification
    ) {

        String titulo =
            switch (
                notification.hito()
            ) {

                case "RECIBIDO" ->
                    "Pedido recibido";

                case "EN_CAMINO" ->
                    "Tu pedido esta en camino";

                case "CERCA" ->
                    "Tu pedido esta cerca";

                case "ENTREGADO" ->
                    "Pedido entregado";

                default ->
                    "Actualizacion del pedido";
            };


        String mensaje =
            switch (
                notification.hito()
            ) {

                case "RECIBIDO" ->
                    "Recibimos tu pedido y ya fue asignado.";

                case "EN_CAMINO" ->
                    "El repartidor ya se encuentra en camino.";

                case "CERCA" ->
                    "El repartidor esta muy cerca de tu destino.";

                case "ENTREGADO" ->
                    "Tu pedido fue entregado correctamente.";

                default ->
                    "Tu pedido cambio de estado.";
            };


        return new PushNotification(

            notification.pedidoId()
                + ":"
                + notification.hito(),

            notification.pedidoId(),

            notification.hito(),

            titulo,

            mensaje,

            notification.clienteNombre(),

            notification.clienteMsisdn(),

            notification.clienteFcmId(),

            Instant.now().toString()
        );
    }
}