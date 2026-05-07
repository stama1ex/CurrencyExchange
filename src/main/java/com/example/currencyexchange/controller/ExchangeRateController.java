package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.ExchangeRate;
import com.example.currencyexchange.service.ExchangeRateAutoUpdateService;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ExchangeRateController {
    private static final Currency ALL_CURRENCIES = new Currency("", "Все валюты");

    @FXML
    private TableView<ExchangeRate> rateTable;
    @FXML
    private TableColumn<ExchangeRate, Integer> idColumn;
    @FXML
    private TableColumn<ExchangeRate, String> currencyIdColumn;
    @FXML
    private TableColumn<ExchangeRate, java.time.LocalDate> dateColumn;
    @FXML
    private TableColumn<ExchangeRate, Double> buyRateColumn;
    @FXML
    private TableColumn<ExchangeRate, Double> sellRateColumn;

    @FXML
    private SearchableComboBox<Currency> filterCurrencyBox;
    @FXML
    private DatePicker filterDatePicker;

    private final ObservableList<ExchangeRate> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setVisible(false);
        currencyIdColumn.setCellValueFactory(new PropertyValueFactory<>("currencyName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("rateDate"));
        buyRateColumn.setCellValueFactory(new PropertyValueFactory<>("buyRateMdl"));
        sellRateColumn.setCellValueFactory(new PropertyValueFactory<>("sellRateMdl"));

        filterCurrencyBox.getItems().setAll(loadFilterCurrencies());
        filterCurrencyBox.getSelectionModel().selectFirst();
        filterCurrencyBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        filterDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        filterDatePicker.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                filterDatePicker.setValue(null);
            }
        });

        rateTable.setItems(data);
        refreshTable();
    }

    @FXML
    private void addRate() {
        Optional<RateForm> formResult = showRateDialog(null);
        if (formResult.isEmpty()) {
            return;
        }
        RateForm form = formResult.get();

        try {
            String currencyCode = form.currencyCode();
            if (!ValidationService.isNotBlank(currencyCode)) {
                AlertUtil.warning("Валидация", "Выберите валюту.");
                return;
            }
            if (!ValidationService.isValidDate(form.rateDate())) {
                AlertUtil.warning("Валидация", "Выберите дату курса.");
                return;
            }
            double buyRate = parsePositive(form.buyRate(), "Курс покупки должен быть > 0");
            double sellRate = parsePositive(form.sellRate(), "Курс продажи должен быть > 0");

            String sql = "INSERT INTO exchange_rates(currency_code, rate_date, buy_rate_mdl, sell_rate_mdl) VALUES (?, ?, ?, ?)";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currencyCode);
                statement.setDate(2, Date.valueOf(form.rateDate()));
                statement.setDouble(3, buyRate);
                statement.setDouble(4, sellRate);
                statement.executeUpdate();
            }
            refreshTable();
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось добавить курс: " + e.getMessage());
        }
    }

    @FXML
    private void updateRate() {
        ExchangeRate selected = rateTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для изменения.");
            return;
        }

        Optional<RateForm> formResult = showRateDialog(selected);
        if (formResult.isEmpty()) {
            return;
        }
        RateForm form = formResult.get();

        try {
            String currencyCode = form.currencyCode();
            if (!ValidationService.isNotBlank(currencyCode) || currencyCode.length() != 3) {
                AlertUtil.warning("Валидация", "Код валюты должен содержать 3 символа.");
                return;
            }
            if (!ValidationService.isValidDate(form.rateDate())) {
                AlertUtil.warning("Валидация", "Выберите дату курса.");
                return;
            }
            double buyRate = parsePositive(form.buyRate(), "Курс покупки должен быть > 0");
            double sellRate = parsePositive(form.sellRate(), "Курс продажи должен быть > 0");

            String sql = "UPDATE exchange_rates SET currency_code=?, rate_date=?, buy_rate_mdl=?, sell_rate_mdl=? WHERE rate_id=?";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currencyCode);
                statement.setDate(2, Date.valueOf(form.rateDate()));
                statement.setDouble(3, buyRate);
                statement.setDouble(4, sellRate);
                statement.setInt(5, selected.getId());
                statement.executeUpdate();
            }
            refreshTable();
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось изменить курс: " + e.getMessage());
        }
    }

    @FXML
    private void deleteRate() {
        ExchangeRate selected = rateTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для удаления.");
            return;
        }

        String sql = "DELETE FROM exchange_rates WHERE rate_id=?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, selected.getId());
            statement.executeUpdate();
            refreshTable();
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось удалить курс: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        applyFilters();
    }

    @FXML
    private void importBnmRates() {
        LocalDate date = ExchangeRateAutoUpdateService.todayInAppZone();
        CompletableFuture.supplyAsync(() -> {
            try {
                return ExchangeRateAutoUpdateService.updateRatesForDate(date);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }).thenAccept(changed -> Platform.runLater(() -> {
            refreshTable();
            if (changed > 0) {
                AlertUtil.info("Курсы BNM", "Курсы за " + date + " импортированы: " + changed);
            } else {
                AlertUtil.warning("Курсы BNM", "Для валют из базы нет новых курсов за " + date + ".");
            }
        })).exceptionally(error -> {
            Platform.runLater(() -> AlertUtil.error(
                    "Курсы BNM",
                    "Не удалось импортировать курсы: " + rootMessage(error)
            ));
            return null;
        });
    }

    private void applyFilters() {
        Currency selectedCurrency = filterCurrencyBox.getValue();
        String currencyCode = selectedCurrency == null ? "" : selectedCurrency.getCode();
        loadRates(currencyCode, filterDatePicker.getValue());
    }

    private void loadRates(String currencyCode, LocalDate rateDate) {
        data.clear();
        StringBuilder sql = new StringBuilder(
                "SELECT r.rate_id, r.currency_code, c.currency_name, r.rate_date, r.buy_rate_mdl, r.sell_rate_mdl " +
                        "FROM exchange_rates r JOIN currencies c ON c.currency_code = r.currency_code WHERE 1=1");
        boolean hasCurrency = ValidationService.isNotBlank(currencyCode);
        boolean hasDate = rateDate != null;
        if (hasCurrency) {
            sql.append(" AND r.currency_code=?");
        }
        if (hasDate) {
            sql.append(" AND r.rate_date=?");
        }
        sql.append(" ORDER BY r.rate_date DESC, r.rate_id DESC");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (hasCurrency) {
                statement.setString(index++, currencyCode);
            }
            if (hasDate) {
                statement.setDate(index, Date.valueOf(rateDate));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    data.add(new ExchangeRate(
                            resultSet.getInt("rate_id"),
                            resultSet.getString("currency_code"),
                            resultSet.getString("currency_name"),
                            resultSet.getDate("rate_date").toLocalDate(),
                            resultSet.getDouble("buy_rate_mdl"),
                            resultSet.getDouble("sell_rate_mdl")
                    ));
                }
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить курсы: " + e.getMessage());
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
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

    private Optional<RateForm> showRateDialog(ExchangeRate rate) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(rate == null ? "Добавить курс" : "Изменить курс");
        dialog.getDialogPane().getStyleClass().add("form-dialog");

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        SearchableComboBox<Currency> currencyInput = new SearchableComboBox<>();
        currencyInput.getItems().setAll(loadCurrencies());
        DatePicker dateInput = new DatePicker(rate == null ? LocalDate.now() : rate.getRateDate());
        TextField buyRateInput = new TextField(rate == null ? "" : String.valueOf(rate.getBuyRateMdl()));
        TextField sellRateInput = new TextField(rate == null ? "" : String.valueOf(rate.getSellRateMdl()));

        if (rate != null) {
            currencyInput.getSelectionModel().select(
                    currencyInput.getItems().stream()
                            .filter(currency -> currency.getCode().equals(rate.getCurrencyCode()))
                            .findFirst()
                            .orElse(null)
            );
        }

        grid.add(new Label("Валюта:"), 0, 0);
        grid.add(currencyInput, 1, 0);
        grid.add(new Label("Дата курса:"), 0, 1);
        grid.add(dateInput, 1, 1);
        grid.add(new Label("Курс покупки:"), 0, 2);
        grid.add(buyRateInput, 1, 2);
        grid.add(new Label("Курс продажи:"), 0, 3);
        grid.add(sellRateInput, 1, 3);

        dialog.getDialogPane().setContent(grid);
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(null);
        validationSupport.setErrorDecorationEnabled(false);
        validationSupport.registerValidator(currencyInput, Validator.createEmptyValidator("Выберите валюту."));
        validationSupport.registerValidator(dateInput, Validator.createPredicateValidator(
                (LocalDate value) -> value != null,
                "Выберите дату курса.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(buyRateInput, Validator.createPredicateValidator(
                this::isPositiveNumber,
                "Курс покупки должен быть числом больше 0.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(sellRateInput, Validator.createPredicateValidator(
                this::isPositiveNumber,
                "Курс продажи должен быть числом больше 0.",
                Severity.ERROR
        ));
        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.disableProperty().bind(validationSupport.invalidProperty());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        Currency selectedCurrency = currencyInput.getValue();

        return Optional.of(new RateForm(
                selectedCurrency == null ? "" : selectedCurrency.getCode(),
                dateInput.getValue(),
                buyRateInput.getText().trim(),
                sellRateInput.getText().trim()
        ));
    }

    private List<Currency> loadCurrencies() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT currency_code, currency_name FROM currencies ORDER BY currency_name");
             ResultSet resultSet = statement.executeQuery()) {
            List<Currency> currencies = new java.util.ArrayList<>();
            while (resultSet.next()) {
                currencies.add(new Currency(
                        resultSet.getString("currency_code"),
                        resultSet.getString("currency_name")
                ));
            }
            return currencies;
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить валюты: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private List<Currency> loadFilterCurrencies() {
        List<Currency> currencies = new java.util.ArrayList<>();
        currencies.add(ALL_CURRENCIES);
        currencies.addAll(loadCurrencies());
        return currencies;
    }

    private record RateForm(String currencyCode, LocalDate rateDate, String buyRate, String sellRate) {
    }
}





