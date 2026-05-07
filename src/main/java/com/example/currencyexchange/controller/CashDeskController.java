package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.enums.CashDeskStatus;
import com.example.currencyexchange.model.CashDesk;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import com.example.currencyexchange.util.IconUtil;
import com.example.currencyexchange.util.ThemeManager;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.SearchableComboBox;
import org.controlsfx.validation.Severity;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Optional;

public class CashDeskController {
    private static final String ALL_FILTER = "Все";

    @FXML
    private TextField searchField;
    @FXML
    private Button clearSearchButton;
    @FXML
    private SearchableComboBox<String> filterStatusBox;
    @FXML
    private TilePane cashDeskCardsContainer;
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

            double min = parseNotNegative(form.minLimit(), "Минимальный лимит не может быть отрицательным.");
            double max = parseNotNegative(form.maxLimit(), "Максимальный лимит не может быть отрицательным.");
            double balanceMdl = parseNotNegative(form.balanceMdl(), "Баланс MDL не может быть отрицательным.");
            double balanceRon = parseNotNegative(form.balanceRon(), "Баланс RON не может быть отрицательным.");
            double balanceEur = parseNotNegative(form.balanceEur(), "Баланс EUR не может быть отрицательным.");
            double balanceUsd = parseNotNegative(form.balanceUsd(), "Баланс USD не может быть отрицательным.");
            if (!ValidationService.isLimitRangeValid(min, max)) {
                AlertUtil.warning("Валидация", "Максимальный лимит должен быть больше минимального.");
                return;
            }

            String sql = "INSERT INTO cash_desks(cash_desk_name, address, phone, balance_mdl, balance_ron, balance_eur, balance_usd, min_limit_mdl, max_limit_mdl, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, form.name());
                statement.setString(2, form.address());
                statement.setString(3, form.phone());
                statement.setDouble(4, balanceMdl);
                statement.setDouble(5, balanceRon);
                statement.setDouble(6, balanceEur);
                statement.setDouble(7, balanceUsd);
                statement.setDouble(8, min);
                statement.setDouble(9, max);
                statement.setString(10, form.status().getDbValue());
                statement.executeUpdate();
            }
            refreshTable();
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

            double min = parseNotNegative(form.minLimit(), "Минимальный лимит не может быть отрицательным.");
            double max = parseNotNegative(form.maxLimit(), "Максимальный лимит не может быть отрицательным.");
            double balanceMdl = parseNotNegative(form.balanceMdl(), "Баланс MDL не может быть отрицательным.");
            double balanceRon = parseNotNegative(form.balanceRon(), "Баланс RON не может быть отрицательным.");
            double balanceEur = parseNotNegative(form.balanceEur(), "Баланс EUR не может быть отрицательным.");
            double balanceUsd = parseNotNegative(form.balanceUsd(), "Баланс USD не может быть отрицательным.");
            if (!ValidationService.isLimitRangeValid(min, max)) {
                AlertUtil.warning("Валидация", "Максимальный лимит должен быть больше минимального.");
                return;
            }

            String sql = "UPDATE cash_desks SET cash_desk_name=?, address=?, phone=?, balance_mdl=?, balance_ron=?, balance_eur=?, balance_usd=?, min_limit_mdl=?, max_limit_mdl=?, status=? WHERE cash_desk_id=?";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, form.name());
                statement.setString(2, form.address());
                statement.setString(3, form.phone());
                statement.setDouble(4, balanceMdl);
                statement.setDouble(5, balanceRon);
                statement.setDouble(6, balanceEur);
                statement.setDouble(7, balanceUsd);
                statement.setDouble(8, min);
                statement.setDouble(9, max);
                statement.setString(10, form.status().getDbValue());
                statement.setInt(11, selected.getId());
                statement.executeUpdate();
            }
            refreshTable();
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось изменить кассу: " + e.getMessage());
        }
    }

    @FXML
    private void deleteCashDesk() {
        CashDesk selected = selectedCashDesk;
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите кассу на карточке для удаления.");
            return;
        }

        String sql = "DELETE FROM cash_desks WHERE cash_desk_id=?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, selected.getId());
            statement.executeUpdate();
            selectedCashDesk = null;
            refreshTable();
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
                "SELECT cash_desk_id, cash_desk_name, address, phone, balance_mdl, balance_ron, balance_eur, balance_usd, min_limit_mdl, max_limit_mdl, status " +
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
                    displayedCashDesks.add(new CashDesk(
                            resultSet.getInt("cash_desk_id"),
                            resultSet.getString("cash_desk_name"),
                            resultSet.getString("address"),
                            resultSet.getString("phone"),
                            resultSet.getDouble("balance_mdl"),
                            resultSet.getDouble("balance_ron"),
                            resultSet.getDouble("balance_eur"),
                            resultSet.getDouble("balance_usd"),
                            resultSet.getDouble("min_limit_mdl"),
                            resultSet.getDouble("max_limit_mdl"),
                            resultSet.getString("status")
                    ));
                }
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
        card.setPrefWidth(320);
        card.setMinWidth(300);
        card.setMaxWidth(340);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label idBadge = new Label("Касса #" + desk.getId());
        idBadge.getStyleClass().add("cash-desk-id-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editButton = new Button();
        IconUtil.setIconOnly(editButton, "fas-pen");
        editButton.getStyleClass().addAll("icon-action-button", "ghost-button");
        editButton.setOnAction(event -> {
            selectCashDesk(desk);
            updateCashDesk();
            event.consume();
        });
        Button deleteButton = new Button();
        IconUtil.setIconOnly(deleteButton, "fas-trash");
        deleteButton.getStyleClass().addAll("icon-action-button", "danger-ghost-button");
        deleteButton.setOnAction(event -> {
            selectCashDesk(desk);
            deleteCashDesk();
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

        HBox limits = new HBox(10);
        limits.getChildren().addAll(
                createMoneyBox("Мин. лимит", desk.getMinLimitMdl(), "MDL"),
                createMoneyBox("Макс. лимит", desk.getMaxLimitMdl(), "MDL")
        );

        HBox balancesTop = new HBox(10);
        balancesTop.getChildren().addAll(
                createMoneyBox("Баланс MDL", desk.getBalanceMdl(), "MDL"),
                createMoneyBox("Баланс RON", desk.getBalanceRon(), "RON")
        );

        HBox balancesBottom = new HBox(10);
        balancesBottom.getChildren().addAll(
                createMoneyBox("Баланс EUR", desk.getBalanceEur(), "EUR"),
                createMoneyBox("Баланс USD", desk.getBalanceUsd(), "USD")
        );

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        Label statusChip = new Label(formatStatus(desk.getStatus()));
        applyStatusStyle(statusChip, desk.getStatus());
        Label limitChip = new Label("MDL лимиты");
        limitChip.getStyleClass().add("soft-chip");
        footer.getChildren().addAll(statusChip, limitChip);

        card.getChildren().addAll(top, body, balancesTop, balancesBottom, limits, footer);
        card.setOnMouseClicked(event -> selectCashDesk(desk));
        return card;
    }

    private VBox createMoneyBox(String title, double amount, String currency) {
        VBox box = new VBox(4);
        box.getStyleClass().add("cash-desk-limit-box");
        HBox.setHgrow(box, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("cash-desk-limit-title");
        Label valueLabel = new Label(moneyFormat.format(amount) + " " + currency);
        valueLabel.getStyleClass().add("cash-desk-limit-value");
        valueLabel.setWrapText(true);

        box.getChildren().addAll(titleLabel, valueLabel);
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

    private Optional<CashDeskForm> showCashDeskDialog(CashDesk desk) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(desk == null ? "Добавить кассу" : "Изменить кассу");
        dialog.getDialogPane().getStyleClass().add("form-dialog");
        ThemeManager.applyToDialog(dialog.getDialogPane());

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        TextField nameInput = new TextField(desk == null ? "" : desk.getName());
        TextField addressInput = new TextField(desk == null ? "" : desk.getAddress());
        TextField phoneInput = new TextField(desk == null ? "" : formatOptionalForEdit(desk.getPhone()));
        TextField balanceMdlInput = new TextField(desk == null ? "0" : String.valueOf(desk.getBalanceMdl()));
        TextField balanceRonInput = new TextField(desk == null ? "0" : String.valueOf(desk.getBalanceRon()));
        TextField balanceEurInput = new TextField(desk == null ? "0" : String.valueOf(desk.getBalanceEur()));
        TextField balanceUsdInput = new TextField(desk == null ? "0" : String.valueOf(desk.getBalanceUsd()));
        TextField minLimitInput = new TextField(desk == null ? "" : String.valueOf(desk.getMinLimitMdl()));
        TextField maxLimitInput = new TextField(desk == null ? "" : String.valueOf(desk.getMaxLimitMdl()));
        SearchableComboBox<CashDeskStatus> statusInput = new SearchableComboBox<>();
        statusInput.getItems().setAll(CashDeskStatus.values());
        if (desk != null) {
            statusInput.setValue(CashDeskStatus.fromDbValue(desk.getStatus()));
        }

        grid.add(new Label("Название:"), 0, 0);
        grid.add(nameInput, 1, 0);
        grid.add(new Label("Адрес:"), 0, 1);
        grid.add(addressInput, 1, 1);
        grid.add(new Label("Телефон:"), 0, 2);
        grid.add(phoneInput, 1, 2);
        grid.add(new Label("Баланс MDL:"), 0, 3);
        grid.add(balanceMdlInput, 1, 3);
        grid.add(new Label("Баланс RON:"), 0, 4);
        grid.add(balanceRonInput, 1, 4);
        grid.add(new Label("Баланс EUR:"), 0, 5);
        grid.add(balanceEurInput, 1, 5);
        grid.add(new Label("Баланс USD:"), 0, 6);
        grid.add(balanceUsdInput, 1, 6);
        grid.add(new Label("Мин. лимит MDL:"), 0, 7);
        grid.add(minLimitInput, 1, 7);
        grid.add(new Label("Макс. лимит MDL:"), 0, 8);
        grid.add(maxLimitInput, 1, 8);
        grid.add(new Label("Статус:"), 0, 9);
        grid.add(statusInput, 1, 9);

        dialog.getDialogPane().setContent(grid);
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(null);
        validationSupport.setErrorDecorationEnabled(false);
        validationSupport.registerValidator(nameInput, Validator.createEmptyValidator("Название кассы обязательно."));
        validationSupport.registerValidator(addressInput, Validator.createEmptyValidator("Адрес кассы обязателен."));
        validationSupport.registerValidator(balanceMdlInput, Validator.createPredicateValidator(
                this::isNotNegativeNumber,
                "Баланс MDL должен быть числом 0 или больше.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(balanceRonInput, Validator.createPredicateValidator(
                this::isNotNegativeNumber,
                "Баланс RON должен быть числом 0 или больше.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(balanceEurInput, Validator.createPredicateValidator(
                this::isNotNegativeNumber,
                "Баланс EUR должен быть числом 0 или больше.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(balanceUsdInput, Validator.createPredicateValidator(
                this::isNotNegativeNumber,
                "Баланс USD должен быть числом 0 или больше.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(minLimitInput, Validator.createPredicateValidator(
                this::isNotNegativeNumber,
                "Минимальный лимит должен быть числом 0 или больше.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(maxLimitInput, Validator.createPredicateValidator(
                this::isNotNegativeNumber,
                "Максимальный лимит должен быть числом 0 или больше.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(statusInput, Validator.createEmptyValidator("Выберите статус кассы."));
        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.disableProperty().bind(validationSupport.invalidProperty());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        return Optional.of(new CashDeskForm(
                nameInput.getText().trim(),
                addressInput.getText().trim(),
                phoneInput.getText().trim(),
                balanceMdlInput.getText().trim(),
                balanceRonInput.getText().trim(),
                balanceEurInput.getText().trim(),
                balanceUsdInput.getText().trim(),
                minLimitInput.getText().trim(),
                maxLimitInput.getText().trim(),
                statusInput.getValue()
        ));
    }

    private String formatOptionalForEdit(String value) {
        return value == null ? "" : value;
    }

    private record CashDeskForm(
            String name,
            String address,
            String phone,
            String balanceMdl,
            String balanceRon,
            String balanceEur,
            String balanceUsd,
            String minLimit,
            String maxLimit,
            CashDeskStatus status
    ) {
    }
}
