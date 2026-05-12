package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.enums.CashDeskStatus;
import com.example.currencyexchange.model.CashDesk;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import com.example.currencyexchange.util.DeleteConfirmationUtil;
import com.example.currencyexchange.util.IconUtil;
import com.example.currencyexchange.util.ModalDialogUtil;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CashDeskController {
    private static final double CASH_DESK_DIALOG_VIEWPORT_WIDTH = 680;
    private static final double CASH_DESK_DIALOG_MAX_VIEWPORT_HEIGHT = 560;

    private static final String ALL_FILTER = "Все";

    @FXML
    private TextField searchField;
    @FXML
    private Button clearSearchButton;
    @FXML
    private SearchableComboBox<String> filterStatusBox;
    @FXML
    private VBox cashDeskCardsContainer;
    @FXML
    private Label cashDesksTotalLabel;
    @FXML
    private Label selectedCashDeskLabel;
    @FXML
    private Label cashDeskMetaLabel;

    private final ObservableList<CashDesk> displayedCashDesks = FXCollections.observableArrayList();
    private final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");
    private CashDesk selectedCashDesk;

    @FXML
    public void initialize() {
        filterStatusBox.getItems().setAll(loadStatusFilterOptions());
        filterStatusBox.getSelectionModel().selectFirst();
        filterStatusBox.valueProperty().addListener((obs, oldVal, newVal) -> searchAndFilter());

        clearSearchButton.visibleProperty().bind(Bindings.isNotEmpty(searchField.textProperty()));
        clearSearchButton.managedProperty().bind(clearSearchButton.visibleProperty());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchAndFilter());

        refreshTable();
    }

    @FXML
    private void addCashDesk() {
        Optional<CashDeskForm> formResult = showCashDeskDialog(null);
        if (formResult.isEmpty()) {
            return;
        }
        CashDeskForm form = formResult.get();

        try {
            if (!validateCashDeskForm(form)) {
                return;
            }

            Map<String, ParsedBalance> balances = parseBalances(form.balances());

            String sql = "INSERT INTO cash_desks(cash_desk_name, address, phone, status) VALUES (?, ?, ?, ?)";
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    int cashDeskId;
                    try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, form.name());
                        statement.setString(2, form.address());
                        statement.setString(3, form.phone());
                        statement.setString(4, form.status().getDbValue());
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException("Не удалось получить ID новой кассы.");
                            }
                            cashDeskId = keys.getInt(1);
                        }
                    }
                    saveCashDeskBalances(connection, cashDeskId, balances);
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    rollbackQuietly(connection);
                    throw e;
                }
            }
            refreshTable();
            AlertUtil.success("Кассы", "Касса успешно добавлена.");
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось добавить кассу: " + e.getMessage());
        }
    }

    @FXML
    private void updateCashDesk() {
        CashDesk selected = selectedCashDesk;
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите кассу на карточке для изменения.");
            return;
        }

        Optional<CashDeskForm> formResult = showCashDeskDialog(selected);
        if (formResult.isEmpty()) {
            return;
        }
        CashDeskForm form = formResult.get();

        try {
            if (!validateCashDeskForm(form)) {
                return;
            }

            Map<String, ParsedBalance> balances = parseBalances(form.balances());

            String sql = "UPDATE cash_desks SET cash_desk_name=?, address=?, phone=?, status=? WHERE cash_desk_id=?";
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, form.name());
                        statement.setString(2, form.address());
                        statement.setString(3, form.phone());
                        statement.setString(4, form.status().getDbValue());
                        statement.setInt(5, selected.getId());
                        statement.executeUpdate();
                    }
                    saveCashDeskBalances(connection, selected.getId(), balances);
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    rollbackQuietly(connection);
                    throw e;
                }
            }
            refreshTable();
            AlertUtil.success("Кассы", "Касса успешно изменена.");
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось изменить кассу: " + e.getMessage());
        }
    }

    private void confirmDeleteCashDesk(CashDesk selected, Node owner) {
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите кассу на карточке для удаления.");
            return;
        }

        DeleteConfirmationUtil.show(
                owner,
                "Удалить кассу?",
                "#" + selected.getId() + " · " + selected.getName(),
                () -> deleteCashDesk(selected)
        );
    }

    private void deleteCashDesk(CashDesk selected) {
        String sql = "DELETE FROM cash_desks WHERE cash_desk_id=?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, selected.getId());
            statement.executeUpdate();
            selectedCashDesk = null;
            refreshTable();
            AlertUtil.success("Кассы", "Касса успешно удалена.");
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось удалить кассу: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        searchAndFilter();
    }

    @FXML
    private void searchAndFilter() {
        String searchText = searchField.getText().trim();
        String status = selectedStatusFilter();
        loadCashDesks(searchText, status);
        updateMeta(buildMetaText(searchText, status));
    }

    @FXML
    private void clearSearch() {
        searchField.clear();
    }

    private void loadCashDesks(String searchText, String status) {
        displayedCashDesks.clear();
        String search = "%" + searchText.toUpperCase() + "%";

        StringBuilder sql = new StringBuilder(
                "SELECT cash_desk_id, cash_desk_name, address, phone, status " +
                        "FROM cash_desks WHERE (UPPER(cash_desk_name) LIKE ? OR UPPER(address) LIKE ? OR UPPER(COALESCE(phone, '')) LIKE ?)");
        if (ValidationService.isNotBlank(status)) {
            sql.append(" AND status=?");
        }
        sql.append(" ORDER BY cash_desk_id");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, search);
            statement.setString(2, search);
            statement.setString(3, search);
            if (ValidationService.isNotBlank(status)) {
                statement.setString(4, status);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    CashDesk desk = new CashDesk(
                            resultSet.getInt("cash_desk_id"),
                            resultSet.getString("cash_desk_name"),
                            resultSet.getString("address"),
                            resultSet.getString("phone"),
                            resultSet.getString("status")
                    );
                    displayedCashDesks.add(desk);
                }
            }
            for (CashDesk desk : displayedCashDesks) {
                desk.setBalances(loadCashDeskBalances(connection, desk.getId()));
            }

            if (selectedCashDesk != null) {
                int selectedId = selectedCashDesk.getId();
                selectedCashDesk = displayedCashDesks.stream()
                        .filter(desk -> desk.getId() == selectedId)
                        .findFirst()
                        .orElse(null);
            }
            renderCashDeskCards();
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить кассы: " + e.getMessage());
        }
    }

    private void renderCashDeskCards() {
        cashDeskCardsContainer.getChildren().clear();

        if (displayedCashDesks.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(28));
            empty.getStyleClass().add("empty-state-box");
            Label icon = new Label("CD");
            icon.getStyleClass().add("empty-state-icon");
            Label text = new Label("По вашему запросу кассы не найдены.");
            text.getStyleClass().add("empty-state-label");
            empty.getChildren().addAll(icon, text);
            empty.setMaxWidth(Double.MAX_VALUE);
            cashDeskCardsContainer.getChildren().add(empty);
            updateSummary();
            return;
        }

        for (CashDesk desk : displayedCashDesks) {
            cashDeskCardsContainer.getChildren().add(createCashDeskCard(desk));
        }
        updateSummary();
    }

    private VBox createCashDeskCard(CashDesk desk) {
        VBox card = new VBox(14);
        card.getStyleClass().add("cash-desk-card");
        if (selectedCashDesk != null && selectedCashDesk.getId() == desk.getId()) {
            card.getStyleClass().add("selected");
        }
        card.setPadding(new Insets(18));
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label idBadge = new Label("Касса #" + desk.getId());
        idBadge.getStyleClass().add("cash-desk-id-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editButton = new Button("Изменить");
        IconUtil.setIcon(editButton, "fas-pen", 12);
        editButton.getStyleClass().addAll("secondary-button", "cash-desk-card-action");
        editButton.setOnAction(event -> {
            selectCashDesk(desk);
            updateCashDesk();
            event.consume();
        });
        Button deleteButton = new Button("Удалить");
        IconUtil.setIcon(deleteButton, "fas-trash", 12);
        deleteButton.getStyleClass().addAll("danger-button", "cash-desk-card-action");
        deleteButton.setOnAction(event -> {
            confirmDeleteCashDesk(desk, deleteButton);
            event.consume();
        });
        top.getChildren().addAll(idBadge, spacer, editButton, deleteButton);

        VBox body = new VBox(7);
        Label title = new Label(desk.getName());
        title.getStyleClass().add("currency-card-title");
        title.setWrapText(true);
        Label address = new Label(desk.getAddress());
        address.getStyleClass().add("cash-desk-address");
        address.setWrapText(true);
        Label phone = new Label("Телефон: " + formatOptional(desk.getPhone()));
        phone.getStyleClass().add("cash-desk-address");
        phone.setWrapText(true);
        body.getChildren().addAll(title, address, phone);

        FlowPane balances = new FlowPane(10, 10);
        balances.setMaxWidth(Double.MAX_VALUE);
        balances.getChildren().addAll(createBalanceBoxes(desk));

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        Label statusChip = new Label(formatStatus(desk.getStatus()));
        applyStatusStyle(statusChip, desk.getStatus());
        Label limitChip = new Label("Лимиты по валютам");
        limitChip.getStyleClass().add("soft-chip");
        footer.getChildren().addAll(statusChip, limitChip);

        card.getChildren().addAll(top, body, balances, footer);
        card.setOnMouseClicked(event -> selectCashDesk(desk));
        return card;
    }

    private List<VBox> createBalanceBoxes(CashDesk desk) {
        return desk.getBalances().stream()
                .map(this::createBalanceBox)
                .toList();
    }

    private VBox createBalanceBox(CashDesk.Balance balance) {
        VBox box = new VBox(4);
        box.getStyleClass().add("cash-desk-limit-box");
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setMaxWidth(Double.MAX_VALUE);

        Label titleLabel = new Label(balance.currencyCode());
        titleLabel.getStyleClass().add("cash-desk-limit-title");
        Label valueLabel = new Label(moneyFormat.format(balance.amount()) + " " + balance.currencyCode());
        valueLabel.getStyleClass().add("cash-desk-limit-value");
        valueLabel.setWrapText(true);
        Label limitsLabel = new Label("Мин. " + moneyFormat.format(balance.minLimit())
                + " / Макс. " + moneyFormat.format(balance.maxLimit()));
        limitsLabel.getStyleClass().add("cash-desk-limit-title");
        limitsLabel.setWrapText(true);

        box.getChildren().addAll(titleLabel, valueLabel, limitsLabel);
        return box;
    }

    private String formatOptional(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private void selectCashDesk(CashDesk desk) {
        selectedCashDesk = desk;
        renderCashDeskCards();
    }

    private void updateSummary() {
        if (cashDesksTotalLabel != null) {
            cashDesksTotalLabel.setText(String.valueOf(displayedCashDesks.size()));
        }
        if (selectedCashDeskLabel != null) {
            selectedCashDeskLabel.setText(selectedCashDesk == null
                    ? "—"
                    : "#" + selectedCashDesk.getId() + " · " + selectedCashDesk.getName());
        }
    }

    private void updateMeta(String text) {
        if (cashDeskMetaLabel != null) {
            cashDeskMetaLabel.setText(text);
        }
        updateSummary();
    }

    private String buildMetaText(String searchText, String status) {
        boolean hasSearch = !searchText.isBlank();
        boolean hasStatus = ValidationService.isNotBlank(status);

        if (!hasSearch && !hasStatus) {
            return "Все кассы в системе";
        }
        if (hasSearch && hasStatus) {
            return "Запрос “" + searchText + "”, статус: " + status;
        }
        if (hasSearch) {
            return "Результаты по запросу: “" + searchText + "”";
        }
        return "Статус: " + status;
    }

    private java.util.List<String> loadStatusFilterOptions() {
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add(ALL_FILTER);
        for (CashDeskStatus status : CashDeskStatus.values()) {
            options.add(status.getDbValue());
        }
        return options;
    }

    private String selectedStatusFilter() {
        String value = filterStatusBox.getValue();
        return ALL_FILTER.equals(value) ? "" : value;
    }

    private String formatStatus(String status) {
        return status == null || status.isBlank() ? "Без статуса" : status;
    }

    private void applyStatusStyle(Label label, String status) {
        label.getStyleClass().add("status-pill");
        if (CashDeskStatus.WORKING.getDbValue().equalsIgnoreCase(status)) {
            label.getStyleClass().add("status-pill-success");
        } else if (CashDeskStatus.CLOSED.getDbValue().equalsIgnoreCase(status)) {
            label.getStyleClass().add("status-pill-danger");
        } else {
            label.getStyleClass().add("status-pill-neutral");
        }
    }

    private boolean validateCashDeskForm(CashDeskForm form) {
        if (!ValidationService.isNotBlank(form.name()) || !ValidationService.isNotBlank(form.address())) {
            AlertUtil.warning("Валидация", "Название и адрес обязательны.");
            return false;
        }
        if (form.status() == null) {
            AlertUtil.warning("Валидация", "Выберите статус кассы.");
            return false;
        }
        return true;
    }

    private double parseNotNegative(String value, String message) {
        try {
            double parsed = Double.parseDouble(value);
            if (!ValidationService.isNotNegative(parsed)) {
                throw new IllegalArgumentException(message);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }

    private boolean isNotNegativeNumber(String value) {
        try {
            return ValidationService.isNotNegative(Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Map<String, BalanceFormValues> collectBalanceValues(Map<String, BalanceInputs> balanceInputs) {
        Map<String, BalanceFormValues> values = new LinkedHashMap<>();
        balanceInputs.forEach((code, inputs) -> values.put(code, new BalanceFormValues(
                inputs.balance().getText().trim(),
                inputs.minLimit().getText().trim(),
                inputs.maxLimit().getText().trim()
        )));
        return values;
    }

    private Map<String, ParsedBalance> parseBalances(Map<String, BalanceFormValues> balanceValues) {
        Map<String, ParsedBalance> balances = new LinkedHashMap<>();
        for (Map.Entry<String, BalanceFormValues> entry : balanceValues.entrySet()) {
            String code = entry.getKey();
            BalanceFormValues values = entry.getValue();
            double balance = parseNotNegative(
                    values.balance(),
                    "Баланс " + entry.getKey() + " не может быть отрицательным."
            );
            double minLimit = parseNotNegative(
                    values.minLimit(),
                    "Минимальный лимит " + code + " не может быть отрицательным."
            );
            double maxLimit = parseNotNegative(
                    values.maxLimit(),
                    "Максимальный лимит " + code + " не может быть отрицательным."
            );
            if (maxLimit < minLimit) {
                throw new IllegalArgumentException("Максимальный лимит " + code + " не может быть меньше минимального.");
            }
            balances.put(code, new ParsedBalance(balance, minLimit, maxLimit));
        }
        return balances;
    }

    private List<Currency> loadCurrencies() {
        String sql = "SELECT currency_code, currency_name FROM currencies ORDER BY currency_code";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
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
            return List.of();
        }
    }

    private List<CashDesk.Balance> loadCashDeskBalances(Connection connection, int cashDeskId) throws SQLException {
        String sql = "SELECT c.currency_code, c.currency_name, " +
                "COALESCE(cdb.balance, 0) AS balance, " +
                "COALESCE(cdb.min_limit, 0) AS min_limit, " +
                "COALESCE(cdb.max_limit, 0) AS max_limit " +
                "FROM currencies c " +
                "LEFT JOIN cash_desk_balances cdb ON cdb.currency_code = c.currency_code AND cdb.cash_desk_id = ? " +
                "ORDER BY c.currency_code";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, cashDeskId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CashDesk.Balance> balances = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    balances.add(new CashDesk.Balance(
                            resultSet.getString("currency_code"),
                            resultSet.getString("currency_name"),
                            resultSet.getDouble("balance"),
                            resultSet.getDouble("min_limit"),
                            resultSet.getDouble("max_limit")
                    ));
                }
                return balances;
            }
        }
    }

    private void saveCashDeskBalances(Connection connection, int cashDeskId, Map<String, ParsedBalance> balances) throws SQLException {
        String sql = "INSERT INTO cash_desk_balances(cash_desk_id, currency_code, balance, min_limit, max_limit) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (cash_desk_id, currency_code) DO UPDATE SET " +
                "balance = EXCLUDED.balance, min_limit = EXCLUDED.min_limit, max_limit = EXCLUDED.max_limit";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Map.Entry<String, ParsedBalance> entry : balances.entrySet()) {
                ParsedBalance balance = entry.getValue();
                statement.setInt(1, cashDeskId);
                statement.setString(2, entry.getKey());
                statement.setDouble(3, balance.amount());
                statement.setDouble(4, balance.minLimit());
                statement.setDouble(5, balance.maxLimit());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Keep the original database error visible to the user.
        }
    }

    private Optional<CashDeskForm> showCashDeskDialog(CashDesk desk) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(CASH_DESK_DIALOG_VIEWPORT_WIDTH + 44);
        ModalDialogUtil.configureFormDialog(
                dialog,
                desk == null ? "Добавить кассу" : "Изменить кассу",
                "fas-cash-register"
        );

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));
        grid.getStyleClass().add("cash-desk-form-grid");
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(112);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        fieldColumn.setFillWidth(true);
        grid.getColumnConstraints().setAll(labelColumn, fieldColumn);

        TextField nameInput = new TextField(desk == null ? "" : desk.getName());
        TextField addressInput = new TextField(desk == null ? "" : desk.getAddress());
        TextField phoneInput = new TextField(desk == null ? "" : formatOptionalForEdit(desk.getPhone()));
        SearchableComboBox<CashDeskStatus> statusInput = new SearchableComboBox<>();
        statusInput.getItems().setAll(CashDeskStatus.values());
        if (desk != null) {
            statusInput.setValue(CashDeskStatus.fromDbValue(desk.getStatus()));
        }
        List<Currency> currencies = loadCurrencies();
        Map<String, BalanceInputs> balanceInputs = new LinkedHashMap<>();
        nameInput.setMaxWidth(Double.MAX_VALUE);
        addressInput.setMaxWidth(Double.MAX_VALUE);
        phoneInput.setMaxWidth(Double.MAX_VALUE);
        statusInput.setMaxWidth(Double.MAX_VALUE);

        int row = 0;
        grid.add(new Label("Название:"), 0, row);
        grid.add(nameInput, 1, row++);
        grid.add(new Label("Адрес:"), 0, row);
        grid.add(addressInput, 1, row++);
        grid.add(new Label("Телефон:"), 0, row);
        grid.add(phoneInput, 1, row++);
        grid.add(new Label("Статус:"), 0, row);
        grid.add(statusInput, 1, row++);

        Label limitsTitle = new Label("Лимиты по валютам");
        limitsTitle.getStyleClass().add("cash-desk-form-section-title");
        grid.add(limitsTitle, 0, row++, 2, 1);

        GridPane limitsGrid = createBalancesFormGrid();
        int limitsRow = 1;
        for (Currency currency : currencies) {
            TextField balanceInput = new TextField(desk == null
                    ? "0"
                    : String.valueOf(desk.getBalance(currency.getCode())));
            TextField minLimitInput = new TextField(desk == null
                    ? "0"
                    : String.valueOf(desk.getMinLimit(currency.getCode())));
            TextField maxLimitInput = new TextField(desk == null
                    ? "0"
                    : String.valueOf(desk.getMaxLimit(currency.getCode())));
            configureMoneyField(balanceInput);
            configureMoneyField(minLimitInput);
            configureMoneyField(maxLimitInput);
            balanceInputs.put(currency.getCode(), new BalanceInputs(balanceInput, minLimitInput, maxLimitInput));

            Label currencyLabel = new Label(currency.getCode());
            currencyLabel.getStyleClass().add("cash-desk-currency-code");
            limitsGrid.add(currencyLabel, 0, limitsRow);
            limitsGrid.add(balanceInput, 1, limitsRow);
            limitsGrid.add(minLimitInput, 2, limitsRow);
            limitsGrid.add(maxLimitInput, 3, limitsRow);
            limitsRow++;
        }
        GridPane.setHgrow(limitsGrid, Priority.ALWAYS);
        grid.add(limitsGrid, 0, row++, 2, 1);

        ScrollPane formScrollPane = new ScrollPane(grid);
        formScrollPane.setFitToWidth(true);
        formScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        formScrollPane.setPrefViewportWidth(CASH_DESK_DIALOG_VIEWPORT_WIDTH);
        formScrollPane.setPrefViewportHeight(calculateDialogViewportHeight(currencies.size()));
        formScrollPane.getStyleClass().addAll("content-scroll", "cash-desk-form-scroll");

        dialog.getDialogPane().setContent(formScrollPane);
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(null);
        validationSupport.setErrorDecorationEnabled(false);
        validationSupport.registerValidator(nameInput, Validator.createEmptyValidator("Название кассы обязательно."));
        validationSupport.registerValidator(addressInput, Validator.createEmptyValidator("Адрес кассы обязателен."));
        balanceInputs.forEach((code, inputs) -> {
            validationSupport.registerValidator(inputs.balance(), Validator.createPredicateValidator(
                    this::isNotNegativeNumber,
                    "Баланс " + code + " должен быть числом 0 или больше.",
                    Severity.ERROR
            ));
            validationSupport.registerValidator(inputs.minLimit(), Validator.createPredicateValidator(
                    this::isNotNegativeNumber,
                    "Минимальный лимит " + code + " должен быть числом 0 или больше.",
                    Severity.ERROR
            ));
            validationSupport.registerValidator(inputs.maxLimit(), Validator.createPredicateValidator(
                    this::isNotNegativeNumber,
                    "Максимальный лимит " + code + " должен быть числом 0 или больше.",
                    Severity.ERROR
            ));
        });
        validationSupport.registerValidator(statusInput, Validator.createEmptyValidator("Выберите статус кассы."));
        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.disableProperty().bind(validationSupport.invalidProperty());

        Optional<ButtonType> result = ModalDialogUtil.showAndWait(dialog);
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        return Optional.of(new CashDeskForm(
                nameInput.getText().trim(),
                addressInput.getText().trim(),
                phoneInput.getText().trim(),
                collectBalanceValues(balanceInputs),
                statusInput.getValue()
        ));
    }

    private GridPane createBalancesFormGrid() {
        GridPane limitsGrid = new GridPane();
        limitsGrid.setHgap(8);
        limitsGrid.setVgap(8);
        limitsGrid.setMaxWidth(Double.MAX_VALUE);
        limitsGrid.getStyleClass().add("cash-desk-currency-grid");

        ColumnConstraints currencyColumn = new ColumnConstraints();
        currencyColumn.setMinWidth(78);
        currencyColumn.setPrefWidth(82);
        limitsGrid.getColumnConstraints().setAll(
                currencyColumn,
                createMoneyColumnConstraints(),
                createMoneyColumnConstraints(),
                createMoneyColumnConstraints()
        );

        addGridHeader(limitsGrid, "Валюта", 0);
        addGridHeader(limitsGrid, "Баланс", 1);
        addGridHeader(limitsGrid, "Мин.", 2);
        addGridHeader(limitsGrid, "Макс.", 3);
        return limitsGrid;
    }

    private ColumnConstraints createMoneyColumnConstraints() {
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        column.setFillWidth(true);
        column.setMinWidth(112);
        return column;
    }

    private void addGridHeader(GridPane grid, String text, int column) {
        Label header = new Label(text);
        header.getStyleClass().add("cash-desk-grid-header");
        grid.add(header, column, 0);
    }

    private void configureMoneyField(TextField field) {
        field.setMaxWidth(Double.MAX_VALUE);
        field.getStyleClass().add("cash-desk-money-field");
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private double calculateDialogViewportHeight(int currenciesCount) {
        return Math.min(CASH_DESK_DIALOG_MAX_VIEWPORT_HEIGHT, 260 + Math.max(currenciesCount, 1) * 42);
    }

    private String formatOptionalForEdit(String value) {
        return value == null ? "" : value;
    }

    private record CashDeskForm(
            String name,
            String address,
            String phone,
            Map<String, BalanceFormValues> balances,
            CashDeskStatus status
    ) {
    }

    private record BalanceInputs(TextField balance, TextField minLimit, TextField maxLimit) {
    }

    private record BalanceFormValues(String balance, String minLimit, String maxLimit) {
    }

    private record ParsedBalance(double amount, double minLimit, double maxLimit) {
    }
}
