package org.example.fleet.delivery;

import org.apache.camel.main.Main;

import org.example.fleet.delivery.routes.DeliveryRoute;
import org.example.fleet.delivery.routes.OrderEventRoute;
import org.example.fleet.delivery.routes.PushRoute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {

    private static final Logger LOG =
        LoggerFactory.getLogger(
            MainApp.class
        );

    public static void main(
        String[] args
    ) throws Exception {

        LOG.info(
            "Starting Delivery Service: "
                + "REST + GPS + PostgreSQL + PUSH + DLQ"
        );

        Main main =
            new Main();

        main.configure()
            .addRoutesBuilder(
                new DeliveryRoute()
            );

        main.configure()
            .addRoutesBuilder(
                new PushRoute()
            );

        main.configure()
            .addRoutesBuilder(
                new OrderEventRoute()
            );

        main.run(args);
    }
}