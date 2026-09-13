///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21
//DEPS org.apache.activemq:artemis-jakarta-client:2.31.2
//DEPS jakarta.jms:jakarta.jms-api:3.1.0

import jakarta.jms.JMSContext;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

import java.time.Instant;

public class PositionTool {

    private static final String BROKER_URL =
        "tcp://localhost:61616";

    private static final String USER =
        "admin";

    private static final String PASSWORD =
        "admin123";


    public static void main(
        String... args
    ) {

        if (args.length < 4) {

            System.out.println(
                "Uso:"
            );

            System.out.println(
                "jbang scripts/PositionTool.java "
                    + "<messageId> <deviceId> <lat> <lon> [speed]"
            );

            return;
        }


        String messageId =
            args[0];

        String deviceId =
            args[1];

        double latitude =
            Double.parseDouble(
                args[2]
            );

        double longitude =
            Double.parseDouble(
                args[3]
            );

        double speed =
            args.length >= 5
                ? Double.parseDouble(
                    args[4]
                )
                : 10.0;


        String json = """
            {
              "schemaVersion": "1.0",
              "messageId": "%s",
              "deviceId": "%s",
              "timestamp": "%s",
              "latitude": %s,
              "longitude": %s,
              "speedKmh": %s,
              "course": 45.0,
              "valid": true,
              "attributes": {
                "origen": "JBang-Demo"
              }
            }
            """.formatted(
                messageId,
                deviceId,
                Instant.now(),
                latitude,
                longitude,
                speed
            );


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

            context
                .createProducer()
                .send(
                    context.createTopic(
                        "vehicle.positions"
                    ),
                    json
                );
        }


        System.out.println(
            "[OK] VehiclePosition enviado"
        );

        System.out.println(
            "messageId: " + messageId
        );

        System.out.println(
            "deviceId: " + deviceId
        );

        System.out.println(
            json
        );
    }
}