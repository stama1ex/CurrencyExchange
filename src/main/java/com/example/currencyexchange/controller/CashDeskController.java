package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.enums.CashDeskStatus;
import com.example.currencyexchange.model.CashDesk;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Optional;

public class CashDeskController {
    @FXML
    private TextField searchField;
    @FXML
    private Button clearSearchButton;
    @FXML
    private ComboBox<CashDeskStatus> filterStatusBox;
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
        filterStatusBox.getItems().add(null);
        filterStatusBox.getItems().addAll(CashDeskStatus.values());
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
            if (!ValidationService.isLimitRangeValid(min, max)) {
                AlertUtil.warning("Валидация", "Максимальный лимит должен быть больше минимального.");
                return;
            }

            String sql = "INSERT INTO cash_desks(cash_desk_name, address, min_limit_mdl, max_limit_mdl, status) VALUES (?, ?, ?, ?, ?)";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, form.name());
                statement.setString(2, form.address());
                statement.setDouble(3, min);
                statement.setDouble(4, max);
                statement.setString(5, form.status().getDbValue());
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
            if (!ValidationService.isLimitRangeValid(min, max)) {
                AlertUtil.warning("Валидация", "Максимальный лимит должен быть больше минимального.");
                return;
            }

            String sql = "UPDATE cash_desks SET cash_desk_name=?, address=?, min_limit_mdl=?, max_limit_mdl=?, status=? WHERE cash_desk_id=?";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, form.name());
                statement.setString(2, form.address());
                statement.setDouble(3, min);
                statement.setDouble(4, max);
                statement.setString(5, form.status().getDbValue());
                statement.setInt(6, selected.getId());
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
        CashDeskStatus status = filterStatusBox.getValue();
        loadCashDesks(searchText, status);
        updateMeta(buildMetaText(searchText, status));
    }

    @FXML
    private void clearSearch() {
        searchField.clear();
    }

    private void loadCashDesks(String searchText, CashDeskStatus status) {
        displayedCashDesks.clear();
        String search = "%" + searchText.toUpperCase() + "%";

        StringBuilder sql = new StringBuilder(
                "SELECT cash_desk_id, cash_desk_name, address, min_limit_mdl, max_limit_mdl, status " +
                        "FROM cash_desks WHERE (UPPER(cash_desk_name) LIKE ? OR UPPER(address) LIKE ?)");
        if (status != null) {
            sql.append(" AND status=?");
        }
        sql.append(" ORDER BY cash_desk_id");

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setString(1, search);
            statement.setString(2, search);
            if (status != null) {
                statement.setString(3, status.getDbValue());
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    displayedCashDesks.add(new CashDesk(
                            resultSet.getInt("cash_desk_id"),
                            resultSet.getString("cash_desk_name"),
                            resultSet.getString("address"),
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
        Button editButton = new Button("✎");
        editButton.getStyleClass().addAll("icon-action-button", "ghost-button");
        editButton.setOnAction(event -> {
            selectCashDesk(desk);
            updateCashDesk();
            event.consume();
        });
        Button deleteButton = new Button("×");
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
        body.getChildren().addAll(title, address);

        HBox limits = new HBox(10);
        limits.getChildren().addAll(
                createLimitBox("Мин. лимит", desk.getMinLimitMdl()),
                createLimitBox("Макс. лимит", desk.getMaxLimitMdl())
        );

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        Label statusChip = new Label(formatStatus(desk.getStatus()));
        applyStatusStyle(statusChip, desk.getStatus());
        Label limitChip = new Label("MDL лимиты");
        limitChip.getStyleClass().add("soft-chip");
        footer.getChildren().addAll(statusChip, limitChip);

        card.getChildren().addAll(top, body, limits, footer);
        card.setOnMouseClicked(event -> selectCashDesk(desk));
        return card;
    }

    private VBox createLimitBox(String title, double amount) {
        VBox box = new VBox(4);
        box.getStyleClass().add("cash-desk-limit-box");
        HBox.setHgrow(box, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("cash-desk-limit-title");
        Label valueLabel = new Label(moneyFormat.format(amount) + " MDL");
        valueLabel.getStyleClass().add("cash-desk-limit-value");
        valueLabel.setWrapText(true);

        box.getChildren().addAll(titleLabel, valueLabel);
        return box;
    }

    private void selectCashDesk(CashDesk desk) {
        selectedCashDesk = desk;
        renderCashDeskCards();
    }

    private void updateSummary() {
        cashDesksTotalLabel.setText(String.valueOf(displayedCashDesks.size()));
        selectedCashDeskLabel.setText(selectedCashDesk == null
                ? "—"
                : "#" + selectedCashDesk.getId() + " · " + selectedCashDesk.getName());
    }

    private void updateMeta(String text) {
        cashDeskMetaLabel.setText(text);
        updateSummary();
    }

    private String buildMetaText(String searchText, CashDeskStatus status) {
        boolean hasSearch = !searchText.isBlank();
        boolean hasStatus = status != null;

        if (!hasSearch && !hasStatus) {
            return "Все кассы в системе";
        }
        if (hasSearch && hasStatus) {
            return "Запрос “" + searchText + "”, статус: " + status.getDbValue();
        }
        if (hasSearch) {
            return "Результаты по запросу: “" + searchText + "”";
        }
        return "Статус: " + status.getDbValue();
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

    private Optional<CashDeskForm> showCashDeskDialog(CashDesk desk) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(desk == null ? "Добавить кассу" : "Изменить кассу");

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        TextField nameInput = new TextField(desk == null ? "" : desk.getName());
        TextField addressInput = new TextField(desk == null ? "" : desk.getAddress());
        TextField minLimitInput = new TextField(desk == null ? "" : String.valueOf(desk.getMinLimitMdl()));
        TextField maxLimitInput = new TextField(desk == null ? "" : String.valueOf(desk.getMaxLimitMdl()));
        ComboBox<CashDeskStatus> statusInput = new ComboBox<>();
        statusInput.getItems().setAll(CashDeskStatus.values());
        if (desk != null) {
            statusInput.setValue(CashDeskStatus.fromDbValue(desk.getStatus()));
        }

        grid.add(new Label("Название:"), 0, 0);
        grid.add(nameInput, 1, 0);
        grid.add(new Label("Адрес:"), 0, 1);
        grid.add(addressInput, 1, 1);
        grid.add(new Label("Мин. лимит MDL:"), 0, 2);
        grid.add(minLimitInput, 1, 2);
        grid.add(new Label("Макс. лимит MDL:"), 0, 3);
        grid.add(maxLimitInput, 1, 3);
        grid.add(new Label("Статус:"), 0, 4);
        grid.add(statusInput, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        return Optional.of(new CashDeskForm(
                nameInput.getText().trim(),
                addressInput.getText().trim(),
                minLimitInput.getText().trim(),
                maxLimitInput.getText().trim(),
                statusInput.getValue()
        ));
    }

    private record CashDeskForm(String name, String address, String minLimit, String maxLimit, CashDeskStatus status) {
    }
}
