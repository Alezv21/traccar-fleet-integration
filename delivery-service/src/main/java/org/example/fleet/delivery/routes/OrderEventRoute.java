package org.example.fleet.delivery.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import org.example.fleet.delivery.config.DatabaseConfig;
import org.example.fleet.delivery.model.OrderEventMessage;
import org.example.fleet.delivery.repository.PedidoRepository;

public class OrderEventRoute extends RouteBuilder {

    private final PedidoRepository repository;

    public OrderEventRoute() {

        this.repository =
            new PedidoRepository(
                DatabaseConfig.createDataSource()
            );
    }

    @Override
    public void configure()
        throws Exception {

        /*
         * Canal asincronico de eventos de pedido.
         */
        from(
            "amqp:queue:delivery.order.events"
        )

            .routeId(
                "order-event-validation"
            )

            .unmarshal()
                .json(
                    JsonLibrary.Jackson,
                    OrderEventMessage.class
                )

            .process(exchange -> {

                OrderEventMessage event =
                    exchange
                        .getMessage()
                        .getBody(
                            OrderEventMessage.class
                        );

                boolean existe =
                    event != null
                    && event.pedidoId() != null
                    && repository.existePedido(
                        event.pedidoId()
                    );

                exchange
                    .getMessage()
                    .setHeader(
                        "pedidoExiste",
                        existe
                    );
            })

            /*
             * Content Based Router.
             */
            .choice()

                .when(
                    header(
                        "pedidoExiste"
                    ).isEqualTo(false)
                )

                    .log(
                        "[DLQ] Evento para pedido inexistente: "
                        + "${body.pedidoId}"
                    )

                    .setHeader(
                        "errorType",
                        constant(
                            "PEDIDO_INEXISTENTE"
                        )
                    )

                    .marshal()
                        .json(
                            JsonLibrary.Jackson
                        )

                    /*
                     * Dead Letter Channel.
                     */
                    .to(
                        "amqp:queue:delivery.errors"
                    )

                .otherwise()

                    .log(
                        "[EVENT] Pedido=${body.pedidoId} "
                        + "Tipo=${body.tipo} validado correctamente"
                    )

            .end();
    }
}