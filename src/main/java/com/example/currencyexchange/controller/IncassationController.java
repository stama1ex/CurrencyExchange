package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.model.CashDesk;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.enums.IncassationStatus;
import com.example.currencyexchange.enums.IncassationType;
import com.example.currencyexchange.model.Incassation;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IncassationController {
    @FXML
    private TableView<Incassation> incassationTable;
    @FXML
    private TableColumn<Incassation, Integer> idColumn;
    @FXML
    private TableColumn<Incassation, Integer> cashDeskIdColumn;
    @FXML
    private TableColumn<Incassation, java.time.LocalDate> dateColumn;
    @FXML
    private TableColumn<Incassation, String> currencyCodeColumn;
    @FXML
    private TableColumn<Incassation, Double> amountColumn;
    @FXML
    private TableColumn<Incassation, String> typeColumn;
    @FXML
    private TableColumn<Incassation, String> statusColumn;

    @FXML
    private ComboBox<IncassationStatus> filterStatusBox;

    private final ObservableList<Incassation> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setVisible(false);
        cashDeskIdColumn.setCellValueFactory(new PropertyValueFactory<>("cashDeskName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("incassationDate"));
        currencyCodeColumn.setCellValueFactory(new PropertyValueFactory<>("currencyName"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("operationType"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        typeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                getStyleClass().removeAll("status-pill", "status-pill-success", "status-pill-warning");
                if (empty || type == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(type);
                getStyleClass().add("status-pill");
                if (IncassationType.TOP_UP.getDbValue().equals(type)) {
                    getStyleClass().add("status-pill-success");
                } else if (IncassationType.INCASSATION.getDbValue().equals(type)) {
                    getStyleClass().add("status-pill-warning");
                }
            }
        });
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-pill", "status-pill-success", "status-pill-danger", "status-pill-neutral");
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(status);
                getStyleClass().add("status-pill");
                if (IncassationStatus.COMPLETED.getDbValue().equals(status)) {
                    getStyleClass().add("status-pill-success");
                } else if (IncassationStatus.CANCELED.getDbValue().equals(status)) {
                    getStyleClass().add("status-pill-danger");
                } else if (IncassationStatus.CREATED.getDbValue().equals(status)) {
                    getStyleClass().add("status-pill-neutral");
                }
            }
        });

        filterStatusBox.getItems().add(null);
        filterStatusBox.getItems().addAll(IncassationStatus.values());
        filterStatusBox.valueProperty().addListener((obs, oldVal, newVal) -> filterByStatus());

        incassationTable.setItems(data);
        refreshTable();
    }

    @FXML
    private void addIncassation() {
        Optional<IncassationForm> formResult = showIncassationDialog(null);
        if (formResult.isEmpty()) {
            return;
        }
        IncassationForm form = formResult.get();

        try {
            if (!ValidationService.isValidDate(form.date())) {
                AlertUtil.warning("Валидация", "Выберите дату.");
                return;
            }
            if (form.cashDesk() == null || form.currency() == null) {
                AlertUtil.warning("Валидация", "Выберите кассу и валюту.");
                return;
            }
            int cashDeskId = form.cashDesk().getId();
            String currencyCode = form.currency().getCode();
            double amount = parsePositive(form.amount(), "Сумма должна быть > 0.");
            if (form.type() == null || form.status() == null) {
                AlertUtil.warning("Валидация", "Выберите тип и статус.");
                return;
            }

            String sql = "INSERT INTO incassations(cash_desk_id, incassation_date, currency_code, operation_type, amount, status) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setInt(1, cashDeskId);
                        statement.setDate(2, Date.valueOf(form.date()));
                        statement.setString(3, currencyCode);
                        statement.setString(4, form.type().getDbValue());
                        statement.setDouble(5, amount);
                        statement.setString(6, form.status().getDbValue());
                        statement.executeUpdate();
                    }

                    updateCashDeskBalanceIfCompleted(connection, cashDeskId, currencyCode, amount, form.type(), form.status());
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    rollbackQuietly(connection);
                    throw e;
                }
            }
            refreshTable();
            // Уведомляем об обновлении данных для синхронизации отчетов и баланса
            MainController.notifyReportUpdate();
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось добавить запись: " + e.getMessage());
        }
    }

    @FXML
    private void updateIncassation() {
        Incassation selected = incassationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для изменения.");
            return;
        }

        Optional<IncassationForm> formResult = showIncassationDialog(selected);
        if (formResult.isEmpty()) {
            return;
        }
        IncassationForm form = formResult.get();

        try {
            if (!ValidationService.isValidDate(form.date())) {
                AlertUtil.warning("Валидация", "Выберите дату.");
                return;
            }
            if (form.cashDesk() == null || form.currency() == null) {
                AlertUtil.warning("Валидация", "Выберите кассу и валюту.");
                return;
            }
            if (form.type() == null || form.status() == null) {
                AlertUtil.warning("Валидация", "Выберите тип и статус.");
                return;
            }
            int cashDeskId = form.cashDesk().getId();
            String currencyCode = form.currency().getCode();
            double amount = parsePositive(form.amount(), "Сумма должна быть > 0.");
            
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    revertCashDeskBalanceIfCompleted(connection, selected.getCashDeskId(), selected.getCurrencyCode(),
                            selected.getAmount(), IncassationType.fromDbValue(selected.getOperationType()), selected.getStatus());

                    updateCashDeskBalanceIfCompleted(connection, cashDeskId, currencyCode, amount, form.type(), form.status());

                    String sql = "UPDATE incassations SET cash_desk_id=?, incassation_date=?, currency_code=?, operation_type=?, amount=?, status=? WHERE incassation_id=?";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setInt(1, cashDeskId);
                        statement.setDate(2, Date.valueOf(form.date()));
                        statement.setString(3, currencyCode);
                        statement.setString(4, form.type().getDbValue());
                        statement.setDouble(5, amount);
                        statement.setString(6, form.status().getDbValue());
                        statement.setInt(7, selected.getId());
                        statement.executeUpdate();
                    }
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    rollbackQuietly(connection);
                    throw e;
                }
            }
            refreshTable();
            // Уведомляем об обновлении данных для синхронизации отчетов и баланса
            MainController.notifyReportUpdate();
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось изменить запись: " + e.getMessage());
        }
    }

    @FXML
    private void deleteIncassation() {
        Incassation selected = incassationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для удаления.");
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                revertCashDeskBalanceIfCompleted(connection, selected.getCashDeskId(), selected.getCurrencyCode(),
                        selected.getAmount(), IncassationType.fromDbValue(selected.getOperationType()), selected.getStatus());

                String sql = "DELETE FROM incassations WHERE incassation_id=?";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setInt(1, selected.getId());
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
            refreshTable();
            // Уведомляем об обновлении данных для синхронизации отчетов и баланса
            MainController.notifyReportUpdate();
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось удалить запись: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        data.clear();
        String sql = "SELECT i.incassation_id, i.cash_desk_id, cd.cash_desk_name, i.incassation_date, i.currency_code, c.currency_name, i.operation_type, i.amount, i.status " +
                "FROM incassations i JOIN cash_desks cd ON cd.cash_desk_id = i.cash_desk_id JOIN currencies c ON c.currency_code = i.currency_code ORDER BY i.incassation_id";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                data.add(new Incassation(
                        resultSet.getInt("incassation_id"),
                        resultSet.getInt("cash_desk_id"),
                        resultSet.getString("cash_desk_name"),
                        resultSet.getDate("incassation_date").toLocalDate(),
                        resultSet.getString("currency_code"),
                        resultSet.getString("currency_name"),
                        resultSet.getString("operation_type"),
                        resultSet.getDouble("amount"),
                        resultSet.getString("status")
                ));
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить инкассации: " + e.getMessage());
        }
    }

    @FXML
    private void filterByStatus() {
        if (filterStatusBox.getValue() == null) {
            refreshTable();
            return;
        }

        data.clear();
        String sql = "SELECT i.incassation_id, i.cash_desk_id, cd.cash_desk_name, i.incassation_date, i.currency_code, c.currency_name, i.operation_type, i.amount, i.status " +
                "FROM incassations i JOIN cash_desks cd ON cd.cash_desk_id = i.cash_desk_id JOIN currencies c ON c.currency_code = i.currency_code WHERE i.status=? ORDER BY i.incassation_id";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, filterStatusBox.getValue().getDbValue());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    data.add(new Incassation(
                            resultSet.getInt("incassation_id"),
                            resultSet.getInt("cash_desk_id"),
                            resultSet.getString("cash_desk_name"),
                            resultSet.getDate("incassation_date").toLocalDate(),
                            resultSet.getString("currency_code"),
                            resultSet.getString("currency_name"),
                            resultSet.getString("operation_type"),
                            resultSet.getDouble("amount"),
                            resultSet.getString("status")
                    ));
                }
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось применить фильтр: " + e.getMessage());
        }
    }

    private String getBalanceColumnName(String currencyCode) {
        return switch (currencyCode.toUpperCase()) {
            case "MDL" -> "balance_mdl";
            case "RON" -> "balance_ron";
            case "EUR" -> "balance_eur";
            case "USD" -> "balance_usd";
            default -> throw new IllegalArgumentException("Неизвестный код валюты: " + currencyCode);
        };
    }

    private void updateCashDeskBalance(Connection connection, int cashDeskId, String currencyCode, 
                                       double amount, IncassationType type) throws SQLException {
        String balanceColumn = getBalanceColumnName(currencyCode);
        String operator = type == IncassationType.TOP_UP ? "+" : "-";
        String sql = "UPDATE cash_desks SET " + balanceColumn + " = " + balanceColumn + " " + operator + " ? WHERE cash_desk_id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, amount);
            statement.setInt(2, cashDeskId);
            statement.executeUpdate();
        }
    }

    private void updateCashDeskBalanceIfCompleted(Connection connection, int cashDeskId, String currencyCode,
                                                  double amount, IncassationType type, IncassationStatus status) throws SQLException {
        if (status == IncassationStatus.COMPLETED) {
            updateCashDeskBalance(connection, cashDeskId, currencyCode, amount, type);
        }
    }

    private void revertCashDeskBalance(Connection connection, int cashDeskId, String currencyCode, 
                                       double amount, IncassationType type) throws SQLException {
        String balanceColumn = getBalanceColumnName(currencyCode);
        // Инвертируем операцию: если была Пополнение, то вычитаем; если была Инкассация, то добавляем
        String operator = type == IncassationType.TOP_UP ? "-" : "+";
        String sql = "UPDATE cash_desks SET " + balanceColumn + " = " + balanceColumn + " " + operator + " ? WHERE cash_desk_id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, amount);
            statement.setInt(2, cashDeskId);
            statement.executeUpdate();
        }
    }

    private void revertCashDeskBalanceIfCompleted(Connection connection, int cashDeskId, String currencyCode,
                                                  double amount, IncassationType type, String status) throws SQLException {
        if (IncassationStatus.COMPLETED.getDbValue().equals(status)) {
            revertCashDeskBalance(connection, cashDeskId, currencyCode, amount, type);
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Keep the original database error visible to the user.
        }
    }

    private double parsePositive(String value, String message) {
        try {
            double parsed = Double.parseDouble(value);
            if (!ValidationService.isPositive(parsed)) {
                throw new IllegalArgumentException(message);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private Optional<IncassationForm> showIncassationDialog(Incassation incassation) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(incassation == null ? "Добавить запись" : "Изменить запись");

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        ComboBox<CashDesk> cashDeskInput = new ComboBox<>();
        cashDeskInput.getItems().setAll(loadCashDesks());
        DatePicker dateInput = new DatePicker(incassation == null ? LocalDate.now() : incassation.getIncassationDate());
        ComboBox<Currency> currencyInput = new ComboBox<>();
        currencyInput.getItems().setAll(loadCurrencies());
        TextField amountInput = new TextField(incassation == null ? "" : String.valueOf(incassation.getAmount()));
        ComboBox<IncassationType> typeInput = new ComboBox<>();
        typeInput.getItems().setAll(IncassationType.values());
        ComboBox<IncassationStatus> statusInput = new ComboBox<>();
        statusInput.getItems().setAll(IncassationStatus.values());

        if (incassation != null) {
            cashDeskInput.getSelectionModel().select(cashDeskInput.getItems().stream()
                    .filter(desk -> desk.getId() == incassation.getCashDeskId())
                    .findFirst().orElse(null));
            currencyInput.getSelectionModel().select(currencyInput.getItems().stream()
                    .filter(currency -> currency.getCode().equals(incassation.getCurrencyCode()))
                    .findFirst().orElse(null));
            typeInput.setValue(IncassationType.fromDbValue(incassation.getOperationType()));
            statusInput.setValue(IncassationStatus.fromDbValue(incassation.getStatus()));
        }

        grid.add(new Label("Касса:"), 0, 0);
        grid.add(cashDeskInput, 1, 0);
        grid.add(new Label("Дата:"), 0, 1);
        grid.add(dateInput, 1, 1);
        grid.add(new Label("Валюта:"), 0, 2);
        grid.add(currencyInput, 1, 2);
        grid.add(new Label("Тип:"), 0, 3);
        grid.add(typeInput, 1, 3);
        grid.add(new Label("Сумма:"), 0, 4);
        grid.add(amountInput, 1, 4);
        grid.add(new Label("Статус:"), 0, 5);
        grid.add(statusInput, 1, 5);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        return Optional.of(new IncassationForm(
                cashDeskInput.getValue(),
                dateInput.getValue(),
                currencyInput.getValue(),
                typeInput.getValue(),
                amountInput.getText().trim(),
                statusInput.getValue()
        ));
    }

    private record IncassationForm(
            CashDesk cashDesk,
            LocalDate date,
            Currency currency,
            IncassationType type,
            String amount,
            IncassationStatus status
    ) {
    }

    private List<CashDesk> loadCashDesks() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT cash_desk_id, cash_desk_name, address, min_limit_mdl, max_limit_mdl, status FROM cash_desks ORDER BY cash_desk_name");
             ResultSet resultSet = statement.executeQuery()) {
            List<CashDesk> desks = new ArrayList<>();
            while (resultSet.next()) {
                desks.add(new CashDesk(
                        resultSet.getInt("cash_desk_id"),
                        resultSet.getString("cash_desk_name"),
                        resultSet.getString("address"),
                        resultSet.getDouble("min_limit_mdl"),
                        resultSet.getDouble("max_limit_mdl"),
                        resultSet.getString("status")
                ));
            }
            return desks;
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить кассы: " + e.getMessage());
            return List.of();
        }
    }

    private List<Currency> loadCurrencies() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT currency_code, currency_name FROM currencies ORDER BY currency_name");
             ResultSet resultSet = statement.executeQuery()) {
            List<Currency> currencies = new ArrayList<>();
            while (resultSet.next()) {
                currencies.add(new Currency(
                        resultSet.getString("currency_code"),
                        resultSet.getString("currency_name")
                ));
            }
            return currencies;
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить валюты: " + e.getMessage());
            return List.of();
        }
    }
}



