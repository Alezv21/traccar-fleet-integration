package org.example.fleet.mediator;

import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) throws Exception {
        log.info("Starting Mediator: AMQP → Artemis AMQP Publisher");
        Main main = new Main();
        main.configure()
            .withBasePackageScan("org.example.fleet.mediator.routes");
        main.run(args);
    }
}
