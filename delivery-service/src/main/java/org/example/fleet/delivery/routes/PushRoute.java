package org.example.fleet.delivery.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import org.example.fleet.delivery.config.DatabaseConfig;
import org.example.fleet.delivery.model.PendingNotification;
import org.example.fleet.delivery.model.PushNotification;
import org.example.fleet.delivery.repository.NotificationRepository;
import org.example.fleet.delivery.service.NotificationService;

public class PushRoute extends RouteBuilder {

    private final NotificationService notificationService;
    private final String pushUrl;

    public PushRoute() {

        NotificationRepository repository =
            new NotificationRepository(
                DatabaseConfig.createDataSource()
            );

        this.notificationService =
            new NotificationService(repository);

        this.pushUrl =
            System.getenv().getOrDefault(
                "PUSH_URL",
                "http://wiremock:8080/push"
            );
    }

    @Override
    public void configure() throws Exception {

        from(
            "timer:push-dispatch"
                + "?delay=3000"
                + "&period=2000"
        )

            .routeId("push-dispatch")

            .process(exchange -> {

                exchange
                    .getMessage()
                    .setBody(
                        notificationService.claimPending()
                    );
            })

            /*
             * Splitter EIP:
             * cada notificación se procesa
             * como un mensaje independiente.
             */
            .split(body())

                .process(exchange -> {

                    PendingNotification pending =
                        exchange
                            .getMessage()
                            .getBody(
                                PendingNotification.class
                            );

                    PushNotification push =
                        notificationService.crearPush(
                            pending
                        );

                    exchange
                        .getMessage()
                        .setHeader(
                            "pedidoId",
                            pending.pedidoId()
                        );

                    exchange
                        .getMessage()
                        .setHeader(
                            "hito",
                            pending.hito()
                        );

                    exchange
                        .getMessage()
                        .setBody(push);
                })

                .marshal()
                    .json(JsonLibrary.Jackson)

                .setHeader(
                    Exchange.HTTP_METHOD,
                    constant("POST")
                )

                .setHeader(
                    Exchange.CONTENT_TYPE,
                    constant("application/json")
                )

                .to(
                    pushUrl
                        + "?bridgeEndpoint=true"
                        + "&throwExceptionOnFailure=false"
                )

                .log(
                    "[PUSH] Pedido=${header.pedidoId} "
                        + "Hito=${header.hito} "
                        + "HTTP=${header.CamelHttpResponseCode}"
                )

            .end();
    }
}