package org.example.fleet.events.routes;

import org.apache.camel.builder.RouteBuilder;
import org.example.fleet.model.VehicleEvent;

/**
 * Consume vehicle events from Artemis pub-sub channel.
 *
 * EIP Patterns:
 * - Durable Subscriber: subscribes to vehicle.events topic with named subscription.
 */
public class EventsConsumerRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // EIP: Durable Subscriber — maintains subscription even if consumer is offline
        from("amqp:topic:vehicle.events" +
             "?subscriptionDurable=true" +
             "&durableSubscriptionName=events-consumer" +
             "&clientId=events-consumer")
            .id("events-consumer")
            .unmarshal().json(VehicleEvent.class)
            .log("[EventsConsumer] Device: ${body.deviceId}, " +
                 "Event: ${body.eventType}, " +
                 "Timestamp: ${body.timestamp}");
    }
}
