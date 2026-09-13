package org.example.fleet.delivery.routes;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import org.example.fleet.delivery.config.DatabaseConfig;
import org.example.fleet.delivery.model.CrearPedidoRequest;
import org.example.fleet.delivery.model.PedidoCreadoResponse;
import org.example.fleet.delivery.model.TrackingResponse;
import org.example.fleet.delivery.repository.PedidoRepository;
import org.example.fleet.delivery.service.PedidoService;
import org.example.fleet.delivery.service.PositionService;
import org.example.fleet.model.VehiclePosition;

import java.util.Map;
import java.util.Optional;

public class DeliveryRoute
    extends RouteBuilder {

    private final PedidoService pedidoService;

    private final PositionService positionService;

    public DeliveryRoute() {

        PedidoRepository repository =
            new PedidoRepository(
                DatabaseConfig.createDataSource()
            );

        this.pedidoService =
            new PedidoService(repository);

        this.positionService =
            new PositionService(repository);
    }

    @Override
    public void configure()
        throws Exception {

        restConfiguration()
            .component("platform-http");


        // ====================================================
        // REST
        // ====================================================

        rest("/api/pedidos")

            .post()
                .consumes("application/json")
                .produces("application/json")
                .to("direct:crear-pedido");


        rest("/api/pedidos/{id}/tracking")

            .get()
                .produces("application/json")
                .to("direct:tracking-pedido");


        // ====================================================
        // CREAR PEDIDO
        // ====================================================

        from("direct:crear-pedido")

            .routeId("crear-pedido")

            .log(
                "[Delivery] Solicitud de nuevo pedido recibida"
            )

            .unmarshal()
                .json(
                    JsonLibrary.Jackson,
                    CrearPedidoRequest.class
                )

            .process(exchange -> {

                try {

                    CrearPedidoRequest request =
                        exchange
                            .getMessage()
                            .getBody(
                                CrearPedidoRequest.class
                            );

                    PedidoCreadoResponse response =
                        pedidoService.crearPedido(
                            request
                        );

                    exchange
                        .getMessage()
                        .setBody(response);

                    exchange
                        .getMessage()
                        .setHeader(
                            Exchange.HTTP_RESPONSE_CODE,
                            202
                        );

                } catch (
                    IllegalArgumentException e
                ) {

                    exchange
                        .getMessage()
                        .setBody(
                            Map.of(
                                "error",
                                e.getMessage()
                            )
                        );

                    exchange
                        .getMessage()
                        .setHeader(
                            Exchange.HTTP_RESPONSE_CODE,
                            400
                        );

                } catch (Exception e) {

                    log.error(
                        "Error creando pedido",
                        e
                    );

                    exchange
                        .getMessage()
                        .setBody(
                            Map.of(
                                "error",
                                "Error interno procesando el pedido"
                            )
                        );

                    exchange
                        .getMessage()
                        .setHeader(
                            Exchange.HTTP_RESPONSE_CODE,
                            500
                        );
                }

                exchange
                    .getMessage()
                    .setHeader(
                        Exchange.CONTENT_TYPE,
                        "application/json"
                    );
            })

            .marshal()
                .json(
                    JsonLibrary.Jackson
                );


        // ====================================================
        // TRACKING
        // ====================================================

        from("direct:tracking-pedido")

            .routeId("tracking-pedido")

            .process(exchange -> {

                String pedidoId =
                    exchange
                        .getMessage()
                        .getHeader(
                            "id",
                            String.class
                        );

                try {

                    Optional<TrackingResponse> tracking =
                        pedidoService.obtenerTracking(
                            pedidoId
                        );

                    if (tracking.isEmpty()) {

                        exchange
                            .getMessage()
                            .setBody(
                                Map.of(
                                    "error",
                                    "Pedido no encontrado",
                                    "id",
                                    pedidoId
                                )
                            );

                        exchange
                            .getMessage()
                            .setHeader(
                                Exchange.HTTP_RESPONSE_CODE,
                                404
                            );

                    } else {

                        exchange
                            .getMessage()
                            .setBody(
                                tracking.get()
                            );

                        exchange
                            .getMessage()
                            .setHeader(
                                Exchange.HTTP_RESPONSE_CODE,
                                200
                            );
                    }

                } catch (Exception e) {

                    log.error(
                        "Error obteniendo tracking",
                        e
                    );

                    exchange
                        .getMessage()
                        .setBody(
                            Map.of(
                                "error",
                                "Error interno consultando el pedido"
                            )
                        );

                    exchange
                        .getMessage()
                        .setHeader(
                            Exchange.HTTP_RESPONSE_CODE,
                            500
                        );
                }

                exchange
                    .getMessage()
                    .setHeader(
                        Exchange.CONTENT_TYPE,
                        "application/json"
                    );
            })

            .marshal()
                .json(
                    JsonLibrary.Jackson
                );


        // ====================================================
        // GPS / EVENT DRIVEN
        //
        // Publish-Subscribe Channel
        // Durable Subscriber
        // Correlation Identifier
        // Idempotent Receiver
        // ====================================================

        from(
            "amqp:topic:vehicle.positions"
            + "?clientId=delivery-service"
            + "&subscriptionDurable=true"
            + "&durableSubscriptionName=delivery-service-positions"
        )

            .routeId(
                "delivery-position-consumer"
            )

            .log(
                "[Delivery] VehiclePosition recibido"
            )

            .unmarshal()
                .json(
                    JsonLibrary.Jackson,
                    VehiclePosition.class
                )

            .process(exchange -> {

                VehiclePosition position =
                    exchange
                        .getMessage()
                        .getBody(
                            VehiclePosition.class
                        );

                positionService.procesar(
                    position
                );
            });
    }
}