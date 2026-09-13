///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS org.apache.activemq:artemis-jakarta-client:2.31.2
//DEPS jakarta.jms:jakarta.jms-api:3.1.0

import jakarta.jms.BytesMessage;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class OrderEventTool {

    private static final String BROKER_URL =
        "tcp://localhost:61616";

    private static final String USER =
        "admin";

    private static final String PASSWORD =
        "admin123";

    public static void main(String... args)
        throws Exception {

        if (args.length == 0) {

            System.out.println(
                "Uso:"
            );

            System.out.println(
                "  jbang scripts/OrderEventTool.java send"
            );

            System.out.println(
                "  jbang scripts/OrderEventTool.java receive"
            );

            return;
        }

        String command =
            args[0].toLowerCase();

        ActiveMQConnectionFactory factory =
            new ActiveMQConnectionFactory(
                BROKER_URL
            );

        try (
            JMSContext context =
                factory.createContext(
                    USER,
                    PASSWORD
                )
        ) {

            switch (command) {

                case "send" ->
                    send(context);

                case "receive" ->
                    receive(context);

                default ->
                    System.out.println(
                        "Comando invalido: "
                            + command
                    );
            }
        }
    }


    private static void send(
        JMSContext context
    ) {

        String eventId =
            "EVT-ERROR-"
                + System.currentTimeMillis();

        String json = """
            {
              "eventId": "%s",
              "pedidoId": "PED-NO-EXISTE",
              "tipo": "ACTUALIZACION",
              "timestamp": "%s",
              "payload": {
                "origen": "JBang"
              }
            }
            """.formatted(
                eventId,
                Instant.now()
            );


        context
            .createProducer()
            .send(
                context.createQueue(
                    "delivery.order.events"
                ),
                json
            );


        System.out.println(
            "[OK] Evento enviado"
        );

        System.out.println(
            "Queue: delivery.order.events"
        );

        System.out.println(
            "EventId: " + eventId
        );

        System.out.println(
            "Pedido: PED-NO-EXISTE"
        );

        System.out.println(
            json
        );
    }


    private static void receive(
        JMSContext context
    ) throws Exception {

        JMSConsumer consumer =
            context.createConsumer(
                context.createQueue(
                    "delivery.errors"
                )
            );

        System.out.println(
            "Esperando mensaje en delivery.errors..."
        );


        Message message =
            consumer.receive(5000);


        if (message == null) {

            System.out.println(
                "[ERROR] No se recibio ningun mensaje."
            );

            return;
        }


        System.out.println(
            "[OK] Mensaje encontrado en DLQ"
        );

        System.out.println(
            "JMSMessageID: "
                + message.getJMSMessageID()
        );


        if (
            message.propertyExists(
                "errorType"
            )
        ) {

            System.out.println(
                "errorType: "
                    + message.getStringProperty(
                        "errorType"
                    )
            );
        }


        if (
            message instanceof TextMessage text
        ) {

            System.out.println(
                text.getText()
            );

            return;
        }


        if (
            message instanceof BytesMessage bytes
        ) {

            long length =
                bytes.getBodyLength();

            byte[] data =
                new byte[
                    Math.toIntExact(length)
                ];

            bytes.readBytes(data);

            System.out.println(
                new String(
                    data,
                    StandardCharsets.UTF_8
                )
            );

            return;
        }


        try {

            System.out.println(
                message.getBody(
                    String.class
                )
            );

        } catch (Exception e) {

            System.out.println(
                "Tipo JMS recibido: "
                    + message
                        .getClass()
                        .getName()
            );
        }
    }
}