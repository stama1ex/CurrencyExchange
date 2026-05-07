package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.model.CashDesk;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.ExchangeOperation;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.SearchableComboBox;
import org.controlsfx.validation.Severity;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExchangeOperationController {
    private static final String BASE_CURRENCY_CODE = "MDL";

    @FXML
    private TableView<ExchangeOperation> operationTable;
    @FXML
    private TableColumn<ExchangeOperation, Integer> idColumn;
    @FXML
    private TableColumn<ExchangeOperation, Integer> cashDeskIdColumn;
    @FXML
    private TableColumn<ExchangeOperation, String> currencyFromColumn;
    @FXML
    private TableColumn<ExchangeOperation, String> currencyToColumn;
    @FXML
    private TableColumn<ExchangeOperation, LocalDate> dateColumn;
    @FXML
    private TableColumn<ExchangeOperation, Double> amountFromColumn;
    @FXML
    private TableColumn<ExchangeOperation, Double> rateColumn;
    @FXML
    private TableColumn<ExchangeOperation, Double> amountToColumn;

    @FXML
    private DatePicker filterDatePicker;

    private final ObservableList<ExchangeOperation> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setVisible(false);
        cashDeskIdColumn.setCellValueFactory(new PropertyValueFactory<>("cashDeskName"));
        currencyFromColumn.setCellValueFactory(new PropertyValueFactory<>("currencyFromName"));
        currencyToColumn.setCellValueFactory(new PropertyValueFactory<>("currencyToName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("operationDate"));
        amountFromColumn.setCellValueFactory(new PropertyValueFactory<>("amountFrom"));
        rateColumn.setCellValueFactory(new PropertyValueFactory<>("rate"));
        amountToColumn.setCellValueFactory(new PropertyValueFactory<>("amountTo"));

        filterDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter());
        filterDatePicker.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                filterDatePicker.setValue(null);
            }
        });

        operationTable.setItems(data);
        refreshTable();
    }

    @FXML
    private void addOperation() {
        Optional<OperationForm> formResult = showOperationDialog(null);
        if (formResult.isEmpty()) {
            return;
        }
        OperationForm form = formResult.get();

        try {
            if (form.cashDesk() == null || form.currencyFrom() == null || form.currencyTo() == null) {
                AlertUtil.warning("Валидация", "Выберите кассу и валюты.");
                return;
            }
            int cashDeskId = form.cashDesk().getId();
            String currencyFrom = form.currencyFrom().getCode();
            String currencyTo = form.currencyTo().getCode();
            if (currencyFrom.equals(currencyTo)) {
                AlertUtil.warning("Валидация", "Валюты обмена должны отличаться.");
                return;
            }
            if (!ValidationService.isValidDate(form.operationDate())) {
                AlertUtil.warning("Валидация", "Выберите дату операции.");
                return;
            }

            double amountFrom = parsePositive(form.amountFrom(), "Сумма исходной валюты должна быть > 0.");
            double rate = parsePositive(form.rate(), "Курс должен быть > 0.");
            double amountTo = parsePositive(form.amountTo(), "Сумма целевой валюты должна быть > 0.");

            String sql = "INSERT INTO exchange_operations(cash_desk_id, operation_date, currency_from, currency_to, amount_from, rate, amount_to) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setInt(1, cashDeskId);
                        statement.setDate(2, Date.valueOf(form.operationDate()));
                        statement.setString(3, currencyFrom);
                        statement.setString(4, currencyTo);
                        statement.setDouble(5, amountFrom);
                        statement.setDouble(6, rate);
                        statement.setDouble(7, amountTo);
                        statement.executeUpdate();
                    }

                    updateExchangeOperationBalances(connection, cashDeskId, currencyFrom, currencyTo, amountFrom, amountTo);
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
            AlertUtil.error("Ошибка БД", "Не удалось добавить операцию: " + e.getMessage());
        }
    }

    @FXML
    private void updateOperation() {
        ExchangeOperation selected = operationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для изменения.");
            return;
        }

        Optional<OperationForm> formResult = showOperationDialog(selected);
        if (formResult.isEmpty()) {
            return;
        }
        OperationForm form = formResult.get();

        try {
            if (form.cashDesk() == null || form.currencyFrom() == null || form.currencyTo() == null) {
                AlertUtil.warning("Валидация", "Выберите кассу и валюты.");
                return;
            }
            int cashDeskId = form.cashDesk().getId();
            String currencyFrom = form.currencyFrom().getCode();
            String currencyTo = form.currencyTo().getCode();
            if (currencyFrom.equals(currencyTo)) {
                AlertUtil.warning("Валидация", "Валюты обмена должны отличаться.");
                return;
            }
            if (!ValidationService.isValidDate(form.operationDate())) {
                AlertUtil.warning("Валидация", "Выберите дату операции.");
                return;
            }
            double amountFrom = parsePositive(form.amountFrom(), "Сумма исходной валюты должна быть > 0.");
            double rate = parsePositive(form.rate(), "Курс должен быть > 0.");
            double amountTo = parsePositive(form.amountTo(), "Сумма целевой валюты должна быть > 0.");

            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    revertExchangeOperationBalances(connection, selected.getCashDeskId(), selected.getCurrencyFrom(),
                            selected.getCurrencyTo(), selected.getAmountFrom(), selected.getAmountTo());

                    updateExchangeOperationBalances(connection, cashDeskId, currencyFrom, currencyTo, amountFrom, amountTo);

                    String sql = "UPDATE exchange_operations SET cash_desk_id=?, operation_date=?, currency_from=?, currency_to=?, amount_from=?, rate=?, amount_to=? WHERE operation_id=?";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setInt(1, cashDeskId);
                        statement.setDate(2, Date.valueOf(form.operationDate()));
                        statement.setString(3, currencyFrom);
                        statement.setString(4, currencyTo);
                        statement.setDouble(5, amountFrom);
                        statement.setDouble(6, rate);
                        statement.setDouble(7, amountTo);
                        statement.setInt(8, selected.getId());
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
            AlertUtil.error("Ошибка БД", "Не удалось изменить операцию: " + e.getMessage());
        }
    }

    @FXML
    private void deleteOperation() {
        ExchangeOperation selected = operationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для удаления.");
            return;
        }

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                revertExchangeOperationBalances(connection, selected.getCashDeskId(), selected.getCurrencyFrom(),
                        selected.getCurrencyTo(), selected.getAmountFrom(), selected.getAmountTo());

                String sql = "DELETE FROM exchange_operations WHERE operation_id=?";
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
            AlertUtil.error("Ошибка БД", "Не удалось удалить операцию: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        data.clear();
        String sql = "SELECT eo.operation_id, eo.cash_desk_id, cd.cash_desk_name, eo.operation_date, eo.currency_from, cf.currency_name AS currency_from_name, " +
                "eo.currency_to, ct.currency_name AS currency_to_name, eo.amount_from, eo.rate, eo.amount_to " +
                "FROM exchange_operations eo " +
                "JOIN cash_desks cd ON cd.cash_desk_id = eo.cash_desk_id " +
                "JOIN currencies cf ON cf.currency_code = eo.currency_from " +
                "JOIN currencies ct ON ct.currency_code = eo.currency_to " +
                "ORDER BY eo.operation_date DESC, eo.operation_id DESC";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                data.add(new ExchangeOperation(
                        resultSet.getInt("operation_id"),
                        resultSet.getInt("cash_desk_id"),
                        resultSet.getString("cash_desk_name"),
                        resultSet.getDate("operation_date").toLocalDate(),
                        resultSet.getString("currency_from"),
                        resultSet.getString("currency_from_name"),
                        resultSet.getString("currency_to"),
                        resultSet.getString("currency_to_name"),
                        resultSet.getDouble("amount_from"),
                        resultSet.getDouble("rate"),
                        resultSet.getDouble("amount_to")
                ));
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить операции: " + e.getMessage());
        }
    }

    @FXML
    private void applyFilter() {
        data.clear();
        LocalDate selectedDate = filterDatePicker.getValue();
        if (selectedDate == null) {
            refreshTable();
            return;
        }

        String sql =
                "SELECT eo.operation_id, eo.cash_desk_id, cd.cash_desk_name, eo.operation_date, eo.currency_from, cf.currency_name AS currency_from_name, " +
                        "eo.currency_to, ct.currency_name AS currency_to_name, eo.amount_from, eo.rate, eo.amount_to " +
                        "FROM exchange_operations eo " +
                        "JOIN cash_desks cd ON cd.cash_desk_id = eo.cash_desk_id " +
                        "JOIN currencies cf ON cf.currency_code = eo.currency_from " +
                        "JOIN currencies ct ON ct.currency_code = eo.currency_to WHERE eo.operation_date=? ORDER BY eo.operation_date DESC, eo.operation_id DESC";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(selectedDate));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    data.add(new ExchangeOperation(
                            resultSet.getInt("operation_id"),
                            resultSet.getInt("cash_desk_id"),
                                resultSet.getString("cash_desk_name"),
                            resultSet.getDate("operation_date").toLocalDate(),
                            resultSet.getString("currency_from"),
                                resultSet.getString("currency_from_name"),
                            resultSet.getString("currency_to"),
                                resultSet.getString("currency_to_name"),
                            resultSet.getDouble("amount_from"),
                            resultSet.getDouble("rate"),
                            resultSet.getDouble("amount_to")
                    ));
                }
            }
        } catch (NumberFormatException e) {
            AlertUtil.warning("Валидация", "Некорректные данные фильтра.");
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

    private void updateExchangeOperationBalances(Connection connection, int cashDeskId, String currencyFrom, 
                                                 String currencyTo, double amountFrom, double amountTo) throws SQLException {
        String fromColumn = getBalanceColumnName(currencyFrom);
        String toColumn = getBalanceColumnName(currencyTo);
        
        // Вычитаем amountFrom из balance_currencyFrom и добавляем amountTo к balance_currencyTo
        String sql = "UPDATE cash_desks SET " + fromColumn + " = " + fromColumn + " - ?, " + 
                     toColumn + " = " + toColumn + " + ? WHERE cash_desk_id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, amountFrom);
            statement.setDouble(2, amountTo);
            statement.setInt(3, cashDeskId);
            statement.executeUpdate();
        }
    }

    private void revertExchangeOperationBalances(Connection connection, int cashDeskId, String currencyFrom, 
                                                 String currencyTo, double amountFrom, double amountTo) throws SQLException {
        String fromColumn = getBalanceColumnName(currencyFrom);
        String toColumn = getBalanceColumnName(currencyTo);
        
        // Инвертируем: добавляем amountFrom обратно к balance_currencyFrom и вычитаем amountTo из balance_currencyTo
        String sql = "UPDATE cash_desks SET " + fromColumn + " = " + fromColumn + " + ?, " + 
                     toColumn + " = " + toColumn + " - ? WHERE cash_desk_id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, amountFrom);
            statement.setDouble(2, amountTo);
            statement.setInt(3, cashDeskId);
            statement.executeUpdate();
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

    private boolean isPositiveNumber(String value) {
        try {
            return ValidationService.isPositive(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void recalculateAmountTo(TextField amountFromInput, TextField rateInput, TextField amountToInput) {
        try {
            double amountFrom = Double.parseDouble(amountFromInput.getText().trim());
            double rate = Double.parseDouble(rateInput.getText().trim());
            if (!ValidationService.isPositive(amountFrom) || !ValidationService.isPositive(rate)) {
                amountToInput.clear();
                return;
            }

            BigDecimal result = BigDecimal.valueOf(amountFrom)
                    .multiply(BigDecimal.valueOf(rate))
                    .setScale(2, RoundingMode.HALF_UP);
            amountToInput.setText(result.stripTrailingZeros().toPlainString());
        } catch (NumberFormatException e) {
            amountToInput.clear();
        }
    }

    private void applyLatestRate(SearchableComboBox<Currency> fromInput,
                                 SearchableComboBox<Currency> toInput,
                                 TextField rateInput) {
        Currency fromCurrency = fromInput.getValue();
        Currency toCurrency = toInput.getValue();
        if (fromCurrency == null || toCurrency == null) {
            rateInput.clear();
            return;
        }

        String fromCode = normalizeCurrencyCode(fromCurrency.getCode());
        String toCode = normalizeCurrencyCode(toCurrency.getCode());
        if (fromCode.equals(toCode)) {
            rateInput.clear();
            return;
        }

        try {
            double latestRate = loadLatestCrossRate(fromCode, toCode);
            rateInput.setText(formatNumber(latestRate, 6));
        } catch (SQLException e) {
            rateInput.clear();
            AlertUtil.error("Курс валют", "Не удалось загрузить последний курс: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            rateInput.clear();
            AlertUtil.warning("Курс валют", e.getMessage());
        }
    }

    private double loadLatestCrossRate(String fromCode, String toCode) throws SQLException {
        if (BASE_CURRENCY_CODE.equals(fromCode)) {
            LatestRate toRate = loadLatestRate(toCode);
            return 1 / toRate.sellRateMdl();
        }
        if (BASE_CURRENCY_CODE.equals(toCode)) {
            LatestRate fromRate = loadLatestRate(fromCode);
            return fromRate.buyRateMdl();
        }

        LatestRate fromRate = loadLatestRate(fromCode);
        LatestRate toRate = loadLatestRate(toCode);
        return fromRate.buyRateMdl() / toRate.sellRateMdl();
    }

    private LatestRate loadLatestRate(String currencyCode) throws SQLException {
        String normalizedCode = normalizeCurrencyCode(currencyCode);
        if (BASE_CURRENCY_CODE.equals(normalizedCode)) {
            return new LatestRate(1, 1);
        }

        String sql = "SELECT buy_rate_mdl, sell_rate_mdl FROM exchange_rates WHERE currency_code=? ORDER BY rate_date DESC, rate_id DESC LIMIT 1";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalizedCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalArgumentException("Для валюты " + normalizedCode + " нет сохраненного курса.");
                }

                double buyRate = resultSet.getDouble("buy_rate_mdl");
                double sellRate = resultSet.getDouble("sell_rate_mdl");
                if (!ValidationService.isPositive(buyRate) || !ValidationService.isPositive(sellRate)) {
                    throw new IllegalArgumentException("Последний курс для " + normalizedCode + " должен быть больше 0.");
                }
                return new LatestRate(buyRate, sellRate);
            }
        }
    }

    private String normalizeCurrencyCode(String currencyCode) {
        return currencyCode == null ? "" : currencyCode.trim().toUpperCase();
    }

    private String formatNumber(double value, int scale) {
        return BigDecimal.valueOf(value)
                .setScale(scale, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private Optional<OperationForm> showOperationDialog(ExchangeOperation operation) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(operation == null ? "Добавить операцию" : "Изменить операцию");
        dialog.getDialogPane().getStyleClass().add("form-dialog");

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        SearchableComboBox<CashDesk> cashDeskInput = new SearchableComboBox<>();
        cashDeskInput.getItems().setAll(loadCashDesks());
        DatePicker dateInput = new DatePicker(operation == null ? LocalDate.now() : operation.getOperationDate());
        SearchableComboBox<Currency> fromInput = new SearchableComboBox<>();
        fromInput.getItems().setAll(loadCurrencies());
        SearchableComboBox<Currency> toInput = new SearchableComboBox<>();
        toInput.getItems().setAll(loadCurrencies());
        TextField amountFromInput = new TextField(operation == null ? "" : String.valueOf(operation.getAmountFrom()));
        TextField rateInput = new TextField(operation == null ? "" : String.valueOf(operation.getRate()));
        TextField amountToInput = new TextField(operation == null ? "" : String.valueOf(operation.getAmountTo()));
        amountToInput.setEditable(false);
        amountToInput.setFocusTraversable(false);
        CheckBox latestRateInput = new CheckBox("Использовать последний курс");

        Runnable updateLatestRate = () -> {
            if (latestRateInput.isSelected()) {
                applyLatestRate(fromInput, toInput, rateInput);
            }
        };
        Runnable updateAmountTo = () -> recalculateAmountTo(amountFromInput, rateInput, amountToInput);

        latestRateInput.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            rateInput.setEditable(!isSelected);
            rateInput.setFocusTraversable(!isSelected);
            if (isSelected) {
                applyLatestRate(fromInput, toInput, rateInput);
            }
            updateAmountTo.run();
        });
        fromInput.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateLatestRate.run();
            updateAmountTo.run();
        });
        toInput.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateLatestRate.run();
            updateAmountTo.run();
        });
        amountFromInput.textProperty().addListener((obs, oldVal, newVal) -> updateAmountTo.run());
        rateInput.textProperty().addListener((obs, oldVal, newVal) -> updateAmountTo.run());

        if (operation != null) {
            cashDeskInput.getSelectionModel().select(cashDeskInput.getItems().stream()
                    .filter(desk -> desk.getId() == operation.getCashDeskId())
                    .findFirst().orElse(null));
            fromInput.getSelectionModel().select(fromInput.getItems().stream()
                    .filter(currency -> currency.getCode().equals(operation.getCurrencyFrom()))
                    .findFirst().orElse(null));
            toInput.getSelectionModel().select(toInput.getItems().stream()
                    .filter(currency -> currency.getCode().equals(operation.getCurrencyTo()))
                    .findFirst().orElse(null));
        }

        grid.add(new Label("Касса:"), 0, 0);
        grid.add(cashDeskInput, 1, 0);
        grid.add(new Label("Дата:"), 0, 1);
        grid.add(dateInput, 1, 1);
        grid.add(new Label("Из валюты:"), 0, 2);
        grid.add(fromInput, 1, 2);
        grid.add(new Label("В валюту:"), 0, 3);
        grid.add(toInput, 1, 3);
        grid.add(new Label("Сумма из:"), 0, 4);
        grid.add(amountFromInput, 1, 4);
        grid.add(new Label("Режим курса:"), 0, 5);
        grid.add(latestRateInput, 1, 5);
        grid.add(new Label("Курс:"), 0, 6);
        grid.add(rateInput, 1, 6);
        grid.add(new Label("Сумма в:"), 0, 7);
        grid.add(amountToInput, 1, 7);

        dialog.getDialogPane().setContent(grid);
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(null);
        validationSupport.setErrorDecorationEnabled(false);
        validationSupport.registerValidator(cashDeskInput, Validator.createEmptyValidator("Выберите кассу."));
        validationSupport.registerValidator(dateInput, Validator.createPredicateValidator(
                (LocalDate value) -> value != null,
                "Выберите дату операции.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(fromInput, Validator.createEmptyValidator("Выберите исходную валюту."));
        validationSupport.registerValidator(toInput, Validator.createEmptyValidator("Выберите целевую валюту."));
        validationSupport.registerValidator(amountFromInput, Validator.createPredicateValidator(
                this::isPositiveNumber,
                "Сумма исходной валюты должна быть числом больше 0.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(rateInput, Validator.createPredicateValidator(
                this::isPositiveNumber,
                "Курс должен быть числом больше 0.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(amountToInput, Validator.createPredicateValidator(
                this::isPositiveNumber,
                "Сумма целевой валюты должна быть числом больше 0.",
                Severity.ERROR
        ));
        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.disableProperty().bind(validationSupport.invalidProperty());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        return Optional.of(new OperationForm(
                cashDeskInput.getValue(),
                dateInput.getValue(),
                fromInput.getValue(),
                toInput.getValue(),
                amountFromInput.getText().trim(),
                rateInput.getText().trim(),
                amountToInput.getText().trim()
        ));
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

    private record LatestRate(double buyRateMdl, double sellRateMdl) {
    }

    private record OperationForm(
            CashDesk cashDesk,
            LocalDate operationDate,
            Currency currencyFrom,
            Currency currencyTo,
            String amountFrom,
            String rate,
            String amountTo
    ) {
    }
}




