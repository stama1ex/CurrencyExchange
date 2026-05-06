package com.example.currencyexchange.service;

import com.example.currencyexchange.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис синхронизации и расчета баланса касс.
 * Рассчитывает баланс кассы на основе завершенных операций (инкассаций и обменов).
 */
public class BalanceSyncService {
    
    /**
     * Рассчитывает баланс кассы для конкретной валюты
     * @param cashDeskId ID кассы
     * @param currencyCode Код валюты
     * @return Баланс (положительный для пополнений, отрицательный для инкассаций)
     */
    public double calculateBalance(int cashDeskId, String currencyCode) {
        double balance = 0.0;
        
        // Рассчитываем баланс из инкассаций (только выполненные: "Выполнена")
        String incassationSql = 
            "SELECT operation_type, SUM(amount) as total FROM incassations " +
            "WHERE cash_desk_id = ? AND currency_code = ? AND status = 'Выполнена' " +
            "GROUP BY operation_type";
        
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(incassationSql)) {
            
            statement.setInt(1, cashDeskId);
            statement.setString(2, currencyCode.toUpperCase());
            
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String operationType = resultSet.getString("operation_type");
                    double amount = resultSet.getDouble("total");
                    
                    // "Пополнение" добавляет, "Инкассация" вычитает
                    if ("Пополнение".equals(operationType)) {
                        balance += amount;
                    } else if ("Инкассация".equals(operationType)) {
                        balance -= amount;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при расчете баланса из инкассаций: " + e.getMessage());
        }
        
        return balance;
    }
    
    /**
     * Рассчитывает баланс для всех валют кассы
     * @param cashDeskId ID кассы
     * @return Map валют и их балансов
     */
    public Map<String, Double> calculateAllBalances(int cashDeskId) {
        Map<String, Double> balances = new HashMap<>();
        
        // Получаем все валюты с инкассациями для этой кассы
        String currencySql = 
            "SELECT DISTINCT currency_code FROM incassations WHERE cash_desk_id = ?";
        
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(currencySql)) {
            
            statement.setInt(1, cashDeskId);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String currencyCode = resultSet.getString("currency_code");
                    double balance = calculateBalance(cashDeskId, currencyCode);
                    balances.put(currencyCode, balance);
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при получении валют: " + e.getMessage());
        }
        
        return balances;
    }
    
    /**
     * Проверяет, есть ли какие-либо изменения в операциях для кассы
     * (используется для определения необходимости обновления)
     */
    public boolean hasOperations(int cashDeskId) {
        String sql = "SELECT COUNT(*) as count FROM incassations WHERE cash_desk_id = ? AND status = 'Выполнена'";
        
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            
            statement.setInt(1, cashDeskId);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("count") > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Ошибка при проверке операций: " + e.getMessage());
        }
        
        return false;
    }
}

