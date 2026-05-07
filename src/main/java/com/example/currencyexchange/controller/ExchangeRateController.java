package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.model.ExchangeRate;
import com.example.currencyexchange.service.ExchangeRateAutoUpdateService;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import com.example.currencyexchange.util.IconUtil;
import com.example.currencyexchange.util.ModalDialogUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SearchableComboBox;
import org.controlsfx.validation.Severity;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ExchangeRateController {
    private static final Currency ALL_CURRENCIES = new Currency("", "Все");
    private static final DateTimeFormatter CARD_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int DATE_PAGE_SIZE = 5;

    @FXML
    private VBox rateDateGroupsContainer;
    @FXML
    private Button loadMoreButton;

    @FXML
    private SearchableComboBox<Currency> filterCurrencyBox;
    @FXML
    private DatePicker filterDatePicker;

    private final ObservableList<ExchangeRate> data = FXCollections.observableArrayList();
    private final DecimalFormat rateFormat = new DecimalFormat("#,##0.0000");
    private ExchangeRate selectedRate;
    private int visibleDateCount = DATE_PAGE_SIZE;

    @FXML
    public void initialize() {
        filterCurrencyBox.getItems().setAll(loadFilterCurrencies());
        filterCurrencyBox.getSelectionModel().selectFirst();
        filterCurrencyBox.valueProperty().addListener((obs, oldVal, newVal) -> resetAndApplyFilters());
        filterDatePicker.valueProperty().addListener((obs, oldVal, newVal) -> resetAndApplyFilters());
        filterDatePicker.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                filterDatePicker.setValue(null);
            }
        });

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
            AlertUtil.success("Курсы валют", "Курс успешно добавлен.");
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось добавить курс: " + e.getMessage());
        }
    }

    @FXML
    private void updateRate() {
        ExchangeRate selected = selectedRate;
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите курс в карточке даты для изменения.");
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
            AlertUtil.success("Курсы валют", "Курс успешно изменен.");
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось изменить курс: " + e.getMessage());
        }
    }

    @FXML
    private void deleteRate() {
        ExchangeRate selected = selectedRate;
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите курс в карточке даты для удаления.");
            return;
        }

        String sql = "DELETE FROM exchange_rates WHERE rate_id=?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, selected.getId());
            statement.executeUpdate();
            selectedRate = null;
            refreshTable();
            AlertUtil.success("Курсы валют", "Курс успешно удален.");
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось удалить курс: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        applyFilters();
    }

    @FXML
    private void loadMoreRates() {
        visibleDateCount += DATE_PAGE_SIZE;
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
                AlertUtil.success("Курсы BNM", "Курсы за " + date + " импортированы: " + changed);
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

    private void resetAndApplyFilters() {
        visibleDateCount = DATE_PAGE_SIZE;
        selectedRate = null;
        applyFilters();
    }

    private void loadRates(String currencyCode, LocalDate rateDate) {
        data.clear();
        boolean hasCurrency = ValidationService.isNotBlank(currencyCode);
        boolean hasDate = rateDate != null;
        int totalDateCount = hasDate ? 1 : countRateDates(currencyCode);
        updateLoadMoreButton(hasDate, totalDateCount);

        StringBuilder sql = new StringBuilder();
        if (hasDate) {
            sql.append("SELECT r.rate_id, r.currency_code, c.currency_name, r.rate_date, r.buy_rate_mdl, r.sell_rate_mdl ")
                    .append("FROM exchange_rates r JOIN currencies c ON c.currency_code = r.currency_code WHERE r.rate_date=?");
            if (hasCurrency) {
                sql.append(" AND r.currency_code=?");
            }
        } else {
            sql.append("WITH selected_dates AS ( ")
                    .append("SELECT DISTINCT r.rate_date FROM exchange_rates r WHERE 1=1");
            if (hasCurrency) {
                sql.append(" AND r.currency_code=?");
            }
            sql.append(" ORDER BY r.rate_date DESC LIMIT ? ")
                    .append(") ")
                    .append("SELECT r.rate_id, r.currency_code, c.currency_name, r.rate_date, r.buy_rate_mdl, r.sell_rate_mdl ")
                    .append("FROM exchange_rates r ")
                    .append("JOIN selected_dates sd ON sd.rate_date = r.rate_date ")
                    .append("JOIN currencies c ON c.currency_code = r.currency_code");
            if (hasCurrency) {
                sql.append(" WHERE r.currency_code=?");
            }
        }
        sql.append(" ORDER BY r.rate_date DESC, c.currency_name, r.currency_code");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (hasDate) {
                statement.setDate(index++, Date.valueOf(rateDate));
                if (hasCurrency) {
                    statement.setString(index, currencyCode);
                }
            } else {
                if (hasCurrency) {
                    statement.setString(index++, currencyCode);
                }
                statement.setInt(index++, visibleDateCount);
                if (hasCurrency) {
                    statement.setString(index, currencyCode);
                }
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
            if (selectedRate != null) {
                int selectedId = selectedRate.getId();
                selectedRate = data.stream()
                        .filter(rate -> rate.getId() == selectedId)
                        .findFirst()
                        .orElse(null);
            }
            renderRateDateGroups();
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить курсы: " + e.getMessage());
        }
    }

    private int countRateDates(String currencyCode) {
        boolean hasCurrency = ValidationService.isNotBlank(currencyCode);
        String sql = "SELECT COUNT(DISTINCT rate_date) FROM exchange_rates" + (hasCurrency ? " WHERE currency_code=?" : "");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (hasCurrency) {
                statement.setString(1, currencyCode);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось посчитать даты курсов: " + e.getMessage());
            return 0;
        }
    }

    private void updateLoadMoreButton(boolean hasDateFilter, int totalDateCount) {
        boolean canLoadMore = !hasDateFilter && totalDateCount > visibleDateCount;
        loadMoreButton.setVisible(canLoadMore);
        loadMoreButton.setManaged(canLoadMore);
    }

    private void renderRateDateGroups() {
        rateDateGroupsContainer.getChildren().clear();

        if (data.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(28));
            empty.getStyleClass().add("empty-state-box");
            Label icon = new Label();
            IconUtil.setIconOnly(icon, "fas-exchange-alt");
            icon.getStyleClass().add("empty-state-icon");
            Label text = new Label("Курсы по выбранным фильтрам не найдены.");
            text.getStyleClass().add("empty-state-label");
            empty.getChildren().addAll(icon, text);
            rateDateGroupsContainer.getChildren().add(empty);
            return;
        }

        Map<LocalDate, List<ExchangeRate>> grouped = new LinkedHashMap<>();
        for (ExchangeRate rate : data) {
            grouped.computeIfAbsent(rate.getRateDate(), date -> new ArrayList<>()).add(rate);
        }
        grouped.forEach((date, rates) -> rateDateGroupsContainer.getChildren().add(createDateGroupCard(date, rates)));
    }

    private VBox createDateGroupCard(LocalDate date, List<ExchangeRate> rates) {
        VBox card = new VBox(10);
        card.getStyleClass().add("exchange-rate-date-card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label dateLabel = new Label(CARD_DATE_FORMAT.format(date));
        dateLabel.getStyleClass().add("exchange-rate-date-title");
        Label countLabel = new Label(rates.size() + " " + pluralizeRates(rates.size()));
        countLabel.getStyleClass().add("exchange-rate-count");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(dateLabel, countLabel, spacer);

        VBox rows = new VBox(6);
        rows.getStyleClass().add("exchange-rate-rows");
        for (ExchangeRate rate : rates) {
            rows.getChildren().add(createRateRow(rate));
        }

        card.getChildren().addAll(header, rows);
        return card;
    }

    private HBox createRateRow(ExchangeRate rate) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("exchange-rate-row");
        if (selectedRate != null && selectedRate.getId() == rate.getId()) {
            row.getStyleClass().add("selected");
        }

        Label currency = new Label(rate.getCurrencyCode() + " · " + rate.getCurrencyName());
        currency.getStyleClass().add("exchange-rate-currency");
        currency.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(currency, Priority.ALWAYS);

        Label buy = createRateValue("Покупка", rate.getBuyRateMdl(), "rate-buy-value");
        Label sell = createRateValue("Продажа", rate.getSellRateMdl(), "rate-sell-value");

        Button editButton = new Button();
        IconUtil.setIconOnly(editButton, "fas-pen");
        editButton.getStyleClass().addAll("icon-action-button", "ghost-button");
        editButton.setOnAction(event -> {
            selectRate(rate);
            updateRate();
            event.consume();
        });

        Button deleteButton = new Button();
        IconUtil.setIconOnly(deleteButton, "fas-trash");
        deleteButton.getStyleClass().addAll("icon-action-button", "danger-ghost-button");
        deleteButton.setOnAction(event -> {
            selectRate(rate);
            deleteRate();
            event.consume();
        });

        row.getChildren().addAll(currency, buy, sell, editButton, deleteButton);
        row.setOnMouseClicked(event -> selectRate(rate));
        return row;
    }

    private Label createRateValue(String title, double value, String styleClass) {
        Label label = new Label(title + ": " + rateFormat.format(value));
        label.getStyleClass().addAll("exchange-rate-value", styleClass);
        return label;
    }

    private void selectRate(ExchangeRate rate) {
        selectedRate = rate;
        renderRateDateGroups();
    }

    private String pluralizeRates(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "курсов";
        }
        if (mod10 == 1) {
            return "курс";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "курса";
        }
        return "курсов";
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
        ModalDialogUtil.configureFormDialog(
                dialog,
                rate == null ? "Добавить курс" : "Изменить курс",
                "fas-exchange-alt"
        );

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

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

        Optional<ButtonType> result = ModalDialogUtil.showAndWait(dialog);
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





