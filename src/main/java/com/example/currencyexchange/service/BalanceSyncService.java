package com.example.currencyexchange.service;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.enums.IncassationStatus;
import com.example.currencyexchange.enums.IncassationType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calculates cash desk balance movements from completed cash logistics and exchange operations.
 */
public class BalanceSyncService {
    private static final Logger LOGGER = Logger.getLogger(BalanceSyncService.class.getName());

    public double calculateBalance(int cashDeskId, String currencyCode) {
        String normalizedCurrency = normalizeCurrencyCode(currencyCode);
        if (normalizedCurrency.isEmpty()) {
            return 0.0;
        }

        return calculateIncassationDelta(cashDeskId, normalizedCurrency)
                + calculateExchangeOperationDelta(cashDeskId, normalizedCurrency);
    }

    public Map<String, Double> calculateAllBalances(int cashDeskId) {
        Map<String, Double> balances = new LinkedHashMap<>();
        String sql = "SELECT currency_code FROM currencies ORDER BY currency_code";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String currencyCode = resultSet.getString("currency_code");
                balances.put(currencyCode, calculateBalance(cashDeskId, currencyCode));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Unable to load currencies for balance calculation", e);
        }

        return balances;
    }

    public boolean hasOperations(int cashDeskId) {
        String sql = "SELECT EXISTS (" +
                "SELECT 1 FROM incassations WHERE cash_desk_id = ? AND status = ? " +
                "UNION ALL " +
                "SELECT 1 FROM exchange_operations WHERE cash_desk_id = ?" +
                ") AS has_operations";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, cashDeskId);
            statement.setString(2, IncassationStatus.COMPLETED.getDbValue());
            statement.setInt(3, cashDeskId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getBoolean("has_operations");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Unable to check cash desk operations", e);
            return false;
        }
    }

    private double calculateIncassationDelta(int cashDeskId, String currencyCode) {
        String sql = "SELECT COALESCE(SUM(CASE " +
                "WHEN operation_type = ? THEN amount " +
                "WHEN operation_type = ? THEN -amount " +
                "ELSE 0 END), 0) AS delta " +
                "FROM incassations " +
                "WHERE cash_desk_id = ? AND currency_code = ? AND status = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, IncassationType.TOP_UP.getDbValue());
            statement.setString(2, IncassationType.INCASSATION.getDbValue());
            statement.setInt(3, cashDeskId);
            statement.setString(4, currencyCode);
            statement.setString(5, IncassationStatus.COMPLETED.getDbValue());

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("delta") : 0.0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Unable to calculate incassation balance delta", e);
            return 0.0;
        }
    }

    private double calculateExchangeOperationDelta(int cashDeskId, String currencyCode) {
        String sql = "SELECT " +
                "COALESCE(SUM(CASE WHEN currency_to = ? THEN amount_to ELSE 0 END), 0) - " +
                "COALESCE(SUM(CASE WHEN currency_from = ? THEN amount_from ELSE 0 END), 0) AS delta " +
                "FROM exchange_operations " +
                "WHERE cash_desk_id = ? AND (currency_to = ? OR currency_from = ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currencyCode);
            statement.setString(2, currencyCode);
            statement.setInt(3, cashDeskId);
            statement.setString(4, currencyCode);
            statement.setString(5, currencyCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("delta") : 0.0;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Unable to calculate exchange operation balance delta", e);
            return 0.0;
        }
    }

    private String normalizeCurrencyCode(String currencyCode) {
        return currencyCode == null ? "" : currencyCode.trim().toUpperCase();
    }
}
