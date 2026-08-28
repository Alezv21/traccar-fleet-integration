package org.example.fleet.mediator.routes;

import org.apache.camel.builder.RouteBuilder;
import org.example.fleet.mediator.translate.TraccarEventTranslator;
import org.example.fleet.mediator.translate.TraccarPositionTranslator;

/**
 * Ingest route: AMQP gateway that receives Traccar position and event forwards,
 * classifies them, translates to canonical models, and publishes to Artemis AMQP.
 *
 * EIP Patterns:
 * - Messaging Gateway: AMQP endpoint accepts external (Traccar) data without knowledge of internal architecture.
 * - Content-Based Router: .choice() classifies incoming payloads into position vs. event branches.
 * - Message Translator: TraccarPositionTranslator/TraccarEventTranslator convert Traccar JSON to Canonical Data Model.
 * - Publish-Subscribe Channel: .to("amqp:topic:...") publishes to durable AMQP topics.
 */
public class IngestRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // EIP: Content-Based Router — classify incoming Traccar payloads
        from("amqp:topic:traccar.*")
                .id("traccar-ingest-mediator")
                .log("[Mediator] Received Traccar forward")
                .unmarshal().json()

                .pipeline()
                    // Save Body
                    .setProperty("originalBody", body())
                    .filter().jsonpath("$.event", true)  //header("X-Payload-Type").isEqualTo("EVENT")
                        .log("[Mediator] Classified as EVENT")
                        .bean(TraccarEventTranslator.class, "translate")
                        .marshal().json()
                        // EIP: Publish-Subscribe Channel — fan out to durable subscribers
                        .to("amqp:topic:vehicle.events")
                        .log("[Mediator] Published event to vehicle.events")
                    .end()

                    // Restore Body
                    .setBody(exchangeProperty("originalBody"))

                    .filter().jsonpath("$.position")  //header("X-Payload-Type").isEqualTo("POSITION")
                        .log("[Mediator] Classified as POSITION")
                        .bean(TraccarPositionTranslator.class, "translate")
                        .marshal().json()
                        .to("amqp:topic:vehicle.positions")
                        .log("[Mediator] Published position to vehicle.positions")
                    .end()

                    .log("[Mediator] Complete")
                .end();
    }
}
