package org.example.fleet.positions.routes;

import org.apache.camel.builder.RouteBuilder;
import org.example.fleet.model.VehiclePosition;

/**
 * Consume vehicle position updates from Artemis pub-sub channel.
 *
 * EIP Patterns:
 * - Durable Subscriber: subscribes to vehicle.positions topic with named subscription.
 * - Message Filter: discards positions with valid=false.
 */
public class PositionsConsumerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // EIP: Durable Subscriber — maintains subscription even if consumer is offline
        from("amqp:topic:vehicle.positions" +
             "?subscriptionDurable=true" +
             "&durableSubscriptionName=positions-consumer" +
             "&clientId=positions-consumer")
            .id("positions-consumer")
            .unmarshal().json(VehiclePosition.class)
            // EIP: Message Filter — discard invalid positions
            .filter(body().method("valid"))
            .log("[PositionsConsumer] Device: ${body.deviceId}, " +
                 "Lat: ${body.latitude}, Lon: ${body.longitude}, " +
                 "Speed: ${body.speedKmh} km/h, Timestamp: ${body.timestamp}");
    }
}
