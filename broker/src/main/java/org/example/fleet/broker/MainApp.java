package org.example.fleet.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) throws Exception {
        log.info("Starting Broker: HTTP Gateway → Artemis AMQP Publisher");
        Main main = new Main();
        main.configure()
            .withBasePackageScan("org.example.fleet.broker.routes");
        main.run(args);
    }
}
