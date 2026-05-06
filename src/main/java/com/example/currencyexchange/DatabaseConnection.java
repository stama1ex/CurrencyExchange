package com.example.currencyexchange;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = readSetting("exchange.db.url", "EXCHANGE_DB_URL",
            "jdbc:postgresql://localhost:5432/exchange_office");
    private static final String USER = readSetting("exchange.db.user", "EXCHANGE_DB_USER", "postgres");
    private static final String PASSWORD = readSetting("exchange.db.password", "EXCHANGE_DB_PASSWORD", "1234");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String readSetting(String propertyName, String environmentName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        }

        return defaultValue;
    }
}

