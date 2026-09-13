package org.example.fleet.delivery.config;

import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

public final class DatabaseConfig {

    private DatabaseConfig() {
    }

    public static DataSource createDataSource() {

        String jdbcUrl = env(
            "JDBC_URL",
            "jdbc:postgresql://postgres:5432/delivery"
        );

        String username = env(
            "POSTGRES_USER",
            "delivery"
        );

        String password = env(
            "POSTGRES_PASSWORD",
            "delivery"
        );

        PGSimpleDataSource dataSource =
            new PGSimpleDataSource();

        dataSource.setURL(jdbcUrl);
        dataSource.setUser(username);
        dataSource.setPassword(password);

        return dataSource;
    }

    private static String env(
        String name,
        String defaultValue
    ) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}