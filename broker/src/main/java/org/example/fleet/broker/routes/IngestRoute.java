package org.example.fleet.broker.routes;

import org.apache.camel.builder.RouteBuilder;
import org.example.fleet.broker.translate.PayloadClassifier;
import org.example.fleet.broker.translate.TraccarEventTranslator;
import org.example.fleet.broker.translate.TraccarPositionTranslator;

/**
 * Ingest route: HTTP gateway that receives Traccar position and event forwards,
 * classifies them, translates to canonical models, and publishes to Artemis AMQP.
 *
 * EIP Patterns:
 * - Messaging Gateway: HTTP endpoint accepts external (Traccar) data without knowledge of internal architecture.
 * - Content-Based Router: .choice() classifies incoming payloads into position vs. event branches.
 * - Message Translator: TraccarPositionTranslator/TraccarEventTranslator convert Traccar JSON to Canonical Data Model.
 * - Publish-Subscribe Channel: .to("amqp:topic:...") publishes to durable AMQP topics.
 */
public class IngestRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // EIP: Content-Based Router — classify incoming Traccar payloads
        from("amqp:queue:ingest")
            .id("traccar-ingest-gateway")
            .log("[Broker] Received Traccar forward")
            .unmarshal().json()
            // Classify and set a header to route later
            .bean(PayloadClassifier.class, "classify")
            // EIP: Content-Based Router — route based on payload type header
            .choice()
                .when().jsonpath("$.event", true)  //header("X-Payload-Type").isEqualTo("EVENT")
                    .log("[Broker] Classified as EVENT")
                    .bean(TraccarEventTranslator.class, "translate")
                    .marshal().json()
                    // EIP: Publish-Subscribe Channel — fan out to durable subscribers
                    .to("amqp:topic:vehicle.events")
                    .log("[Broker] Published event to vehicle.events")
                .when().jsonpath("$.position")  //header("X-Payload-Type").isEqualTo("POSITION")
                    .log("[Broker] Classified as POSITION")
                    .bean(TraccarPositionTranslator.class, "translate")
                    .marshal().json()
                    .to("amqp:topic:vehicle.positions")
                    .log("[Broker] Published position to vehicle.positions")
                .otherwise()
                    .log("[Broker] WARNING: Unclassified payload, dropping")
            .end();
    }
}
