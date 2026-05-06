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
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class CashDeskController {
    @FXML
    private TableView<CashDesk> cashDeskTable;
    @FXML
    private TableColumn<CashDesk, Integer> idColumn;
    @FXML
    private TableColumn<CashDesk, String> nameColumn;
    @FXML
    private TableColumn<CashDesk, String> addressColumn;
    @FXML
    private TableColumn<CashDesk, Double> minLimitColumn;
    @FXML
    private TableColumn<CashDesk, Double> maxLimitColumn;
    @FXML
    private TableColumn<CashDesk, String> statusColumn;

    @FXML
    private TextField searchField;
    @FXML
    private Button clearSearchButton;
    @FXML
    private ComboBox<CashDeskStatus> filterStatusBox;

    private final ObservableList<CashDesk> data = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setVisible(false);
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        addressColumn.setCellValueFactory(new PropertyValueFactory<>("address"));
        minLimitColumn.setCellValueFactory(new PropertyValueFactory<>("minLimitMdl"));
        maxLimitColumn.setCellValueFactory(new PropertyValueFactory<>("maxLimitMdl"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                getStyleClass().removeAll("status-pill", "status-pill-success", "status-pill-danger");
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }

                setText(status);
                getStyleClass().add("status-pill");
                if ("Работает".equalsIgnoreCase(status)) {
                    getStyleClass().add("status-pill-success");
                } else if ("Закрыта".equalsIgnoreCase(status)) {
                    getStyleClass().add("status-pill-danger");
                }
            }
        });

        filterStatusBox.getItems().add(null);
        filterStatusBox.getItems().addAll(CashDeskStatus.values());
        filterStatusBox.valueProperty().addListener((obs, oldVal, newVal) -> searchAndFilter());
        clearSearchButton.visibleProperty().bind(Bindings.isNotEmpty(searchField.textProperty()));
        clearSearchButton.managedProperty().bind(clearSearchButton.visibleProperty());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchAndFilter());

        cashDeskTable.setItems(data);
        searchAndFilter();
    }

    @FXML
    private void addCashDesk() {
        Optional<CashDeskForm> formResult = showCashDeskDialog(null);
        if (formResult.isEmpty()) {
            return;
        }
        CashDeskForm form = formResult.get();

        try {
            if (!ValidationService.isNotBlank(form.name()) || !ValidationService.isNotBlank(form.address())) {
                AlertUtil.warning("Валидация", "Название и адрес обязательны.");
                return;
            }
            if (form.status() == null) {
                AlertUtil.warning("Валидация", "Выберите статус кассы.");
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
        CashDesk selected = cashDeskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для изменения.");
            return;
        }

        Optional<CashDeskForm> formResult = showCashDeskDialog(selected);
        if (formResult.isEmpty()) {
            return;
        }
        CashDeskForm form = formResult.get();

        try {
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
        CashDesk selected = cashDeskTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для удаления.");
            return;
        }

        String sql = "DELETE FROM cash_desks WHERE cash_desk_id=?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, selected.getId());
            statement.executeUpdate();
            refreshTable();
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось удалить кассу: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        data.clear();
        String sql = "SELECT cash_desk_id, cash_desk_name, address, min_limit_mdl, max_limit_mdl, status FROM cash_desks ORDER BY cash_desk_id";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                data.add(new CashDesk(
                        resultSet.getInt("cash_desk_id"),
                        resultSet.getString("cash_desk_name"),
                        resultSet.getString("address"),
                        resultSet.getDouble("min_limit_mdl"),
                        resultSet.getDouble("max_limit_mdl"),
                        resultSet.getString("status")
                ));
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить кассы: " + e.getMessage());
        }
    }

    @FXML
    private void searchAndFilter() {
        data.clear();
        String search = "%" + searchField.getText().trim().toUpperCase() + "%";
        CashDeskStatus status = filterStatusBox.getValue();

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
                    data.add(new CashDesk(
                            resultSet.getInt("cash_desk_id"),
                            resultSet.getString("cash_desk_name"),
                            resultSet.getString("address"),
                            resultSet.getDouble("min_limit_mdl"),
                            resultSet.getDouble("max_limit_mdl"),
                            resultSet.getString("status")
                    ));
                }
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось выполнить поиск: " + e.getMessage());
        }
    }

    @FXML
    private void clearSearch() {
        searchField.clear();
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



