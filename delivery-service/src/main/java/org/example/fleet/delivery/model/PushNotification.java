package org.example.fleet.delivery.model;

public record PushNotification(

    String notificationId,

    String pedidoId,

    String hito,

    String titulo,

    String mensaje,

    String cliente,

    String msisdn,

    String fcmId,

    String timestamp

) {
}