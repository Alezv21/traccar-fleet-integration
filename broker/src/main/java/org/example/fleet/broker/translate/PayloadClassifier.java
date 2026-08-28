package org.example.fleet.broker.translate;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.camel.Exchange;

/**
 * Classifies Traccar payloads into position vs. event based on JSON structure.
 * Used in the Content-Based Router pattern to route incoming messages.
 */
public class PayloadClassifier {

    /**
     * Classify the incoming payload and set a header for routing.
     * Returns the payload unchanged but adds an X-Payload-Type header.
     */
    public void classify(JsonNode node, Exchange exchange) {
        try {
            String payloadType = "UNKNOWN";

            if (node.has("event")) {
                payloadType = "EVENT";
            } else if (node.has("position")) {
                payloadType = "POSITION";
            }

            exchange.getIn().setHeader("X-Payload-Type", payloadType);
        } catch (Exception e) {
            exchange.getIn().setHeader("X-Payload-Type", "UNKNOWN");
        }
    }
}
