package com.example.currencyexchange.service;

import com.example.currencyexchange.enums.IncassationStatus;
import com.example.currencyexchange.enums.IncassationType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CashDeskBalanceService {

    public void applyExchangeOperation(Connection connection,
                                       int cashDeskId,
                                       String currencyFrom,
                                       String currencyTo,
                                       double amountFrom,
                                       double amountTo) throws SQLException {
        adjustCashDeskBalance(connection, cashDeskId, currencyFrom, amountFrom);
        adjustCashDeskBalance(connection, cashDeskId, currencyTo, -amountTo);
    }

    public void revertExchangeOperation(Connection connection,
                                        int cashDeskId,
                                        String currencyFrom,
                                        String currencyTo,
                                        double amountFrom,
                                        double amountTo) throws SQLException {
        adjustCashDeskBalance(connection, cashDeskId, currencyFrom, -amountFrom);
        adjustCashDeskBalance(connection, cashDeskId, currencyTo, amountTo);
    }

    public void applyIncassationIfCompleted(Connection connection,
                                            int cashDeskId,
                                            String currencyCode,
                                            double amount,
                                            IncassationType type,
                                            IncassationStatus status) throws SQLException {
        if (status == IncassationStatus.COMPLETED) {
            adjustIncassationBalance(connection, cashDeskId, currencyCode, amount, type);
        }
    }

    public void revertIncassationIfCompleted(Connection connection,
                                             int cashDeskId,
                                             String currencyCode,
                                             double amount,
                                             IncassationType type,
                                             IncassationStatus status) throws SQLException {
        if (status == IncassationStatus.COMPLETED) {
            revertIncassationBalance(connection, cashDeskId, currencyCode, amount, type);
        }
    }

    private void adjustIncassationBalance(Connection connection,
                                          int cashDeskId,
                                          String currencyCode,
                                          double amount,
                                          IncassationType type) throws SQLException {
        double delta = type == IncassationType.TOP_UP ? amount : -amount;
        adjustCashDeskBalance(connection, cashDeskId, currencyCode, delta);
    }

    private void revertIncassationBalance(Connection connection,
                                          int cashDeskId,
                                          String currencyCode,
                                          double amount,
                                          IncassationType type) throws SQLException {
        double delta = type == IncassationType.TOP_UP ? -amount : amount;
        adjustCashDeskBalance(connection, cashDeskId, currencyCode, delta);
    }

    private void adjustCashDeskBalance(Connection connection,
                                       int cashDeskId,
                                       String currencyCode,
                                       double delta) throws SQLException {
        String selectSql = "SELECT balance FROM cash_desk_balances WHERE cash_desk_id=? AND currency_code=? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setInt(1, cashDeskId);
            statement.setString(2, currencyCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    if (delta < 0) {
                        throwInsufficientBalance(currencyCode, 0, -delta);
                    }
                    insertCashDeskBalance(connection, cashDeskId, currencyCode, delta);
                    return;
                }

                double currentBalance = resultSet.getDouble("balance");
                double newBalance = currentBalance + delta;
                if (newBalance < 0) {
                    throwInsufficientBalance(currencyCode, currentBalance, -delta);
                }
                updateCashDeskBalance(connection, cashDeskId, currencyCode, newBalance);
            }
        }
    }

    private void insertCashDeskBalance(Connection connection,
                                       int cashDeskId,
                                       String currencyCode,
                                       double balance) throws SQLException {
        String sql = "INSERT INTO cash_desk_balances(cash_desk_id, currency_code, balance) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, cashDeskId);
            statement.setString(2, currencyCode);
            statement.setDouble(3, balance);
            statement.executeUpdate();
        }
    }

    private void updateCashDeskBalance(Connection connection,
                                       int cashDeskId,
                                       String currencyCode,
                                       double balance) throws SQLException {
        String sql = "UPDATE cash_desk_balances SET balance=? WHERE cash_desk_id=? AND currency_code=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, balance);
            statement.setInt(2, cashDeskId);
            statement.setString(3, currencyCode);
            statement.executeUpdate();
        }
    }

    private void throwInsufficientBalance(String currencyCode, double currentBalance, double requiredAmount) {
        throw new IllegalArgumentException(
                "Недостаточно " + currencyCode + " в кассе. Доступно: "
                        + formatNumber(currentBalance)
                        + ", нужно списать: "
                        + formatNumber(requiredAmount)
                        + "."
        );
    }

    private String formatNumber(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
