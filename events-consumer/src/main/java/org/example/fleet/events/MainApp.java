package org.example.fleet.events;

import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {
    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    public static void main(String[] args) throws Exception {
        log.info("Starting Events Consumer: subscribing to vehicle.events topic");
        Main main = new Main();
        main.configure()
            .withBasePackageScan("org.example.fleet.events.routes");
        main.run(args);
    }
}
