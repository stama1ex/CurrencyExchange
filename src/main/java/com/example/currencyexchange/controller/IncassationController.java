package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.model.CashDesk;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.enums.IncassationStatus;
import com.example.currencyexchange.enums.IncassationType;
import com.example.currencyexchange.model.Incassation;
import com.example.currencyexchange.service.CashDeskBalanceService;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import com.example.currencyexchange.util.DeleteConfirmationUtil;
import com.example.currencyexchange.util.ModalDialogUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import org.controlsfx.control.SearchableComboBox;
import org.controlsfx.validation.Severity;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IncassationController {
    private static final String ALL_FILTER = "Все";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final int PAGE_SIZE = 100;

    @FXML
    private TableView<Incassation> incassationTable;
    @FXML
    private TableColumn<Incassation, Integer> idColumn;
    @FXML
    private TableColumn<Incassation, Integer> cashDeskIdColumn;
    @FXML
    private TableColumn<Incassation, LocalDateTime> dateColumn;
    @FXML
    private TableColumn<Incassation, String> currencyCodeColumn;
    @FXML
    private TableColumn<Incassation, Double> amountColumn;
    @FXML
    private TableColumn<Incassation, String> typeColumn;
    @FXML
    private TableColumn<Incassation, String> statusColumn;
    @FXML
    private TableColumn<Incassation, String> noteColumn;

    @FXML
    private SearchableComboBox<String> filterStatusBox;
    @FXML
    private SearchableComboBox<String> filterTypeBox;
    @FXML
    private Button previousPageButton;
    @FXML
    private Button nextPageButton;
    @FXML
    private Label pageInfoLabel;

    private final ObservableList<Incassation> data = FXCollections.observableArrayList();
    private final CashDeskBalanceService cashDeskBalanceService = new CashDeskBalanceService();

    private int currentPage;
    private int totalItems;
    private int totalPages = 1;

    @FXML
    public void initialize() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setVisible(false);
        cashDeskIdColumn.setCellValueFactory(new PropertyValueFactory<>("cashDeskName"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("incassationDate"));
        dateColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDateTime dateTime, boolean empty) {
                super.updateItem(dateTime, empty);
                setText(empty || dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER));
            }
        });
        currencyCodeColumn.setCellValueFactory(new PropertyValueFactory<>("currencyName"));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("operationType"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        noteColumn.setCellValueFactory(new PropertyValueFactory<>("note"));
        typeColumn.setStyle("-fx-alignment: CENTER;");
        statusColumn.setStyle("-fx-alignment: CENTER;");
        typeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                setAlignment(Pos.CENTER);
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
                setAlignment(Pos.CENTER);
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

        filterStatusBox.getItems().setAll(loadStatusFilterOptions());
        filterStatusBox.getSelectionModel().selectFirst();
        filterStatusBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentPage = 0;
            applyFilters();
        });

        filterTypeBox.getItems().setAll(loadTypeFilterOptions());
        filterTypeBox.getSelectionModel().selectFirst();
        filterTypeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            currentPage = 0;
            applyFilters();
        });

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
            if (!ValidationService.isValidDateTime(form.dateTime())) {
                AlertUtil.warning("Валидация", "Выберите дату и время.");
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

            String sql = "INSERT INTO incassations(cash_desk_id, incassation_date, currency_code, operation_type, amount, status, note) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setInt(1, cashDeskId);
                        statement.setTimestamp(2, Timestamp.valueOf(form.dateTime()));
                        statement.setString(3, currencyCode);
                        statement.setString(4, form.type().getDbValue());
                        statement.setDouble(5, amount);
                        statement.setString(6, form.status().getDbValue());
                        statement.setString(7, form.note());
                        statement.executeUpdate();
                    }

                    cashDeskBalanceService.applyIncassationIfCompleted(
                            connection,
                            cashDeskId,
                            currencyCode,
                            amount,
                            form.type(),
                            form.status()
                    );
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    rollbackQuietly(connection);
                    throw e;
                }
            }
            refreshTable();
            // Уведомляем об обновлении данных для синхронизации отчетов и баланса
            MainController.notifyReportUpdate();
            AlertUtil.success("Инкассации", "Запись успешно добавлена.");
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
            if (!ValidationService.isValidDateTime(form.dateTime())) {
                AlertUtil.warning("Валидация", "Выберите дату и время.");
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
                        cashDeskBalanceService.revertIncassationIfCompleted(
                            connection,
                            selected.getCashDeskId(),
                            selected.getCurrencyCode(),
                            selected.getAmount(),
                            IncassationType.fromDbValue(selected.getOperationType()),
                            IncassationStatus.fromDbValue(selected.getStatus())
                        );

                        cashDeskBalanceService.applyIncassationIfCompleted(
                            connection,
                            cashDeskId,
                            currencyCode,
                            amount,
                            form.type(),
                            form.status()
                        );

                    String sql = "UPDATE incassations SET cash_desk_id=?, incassation_date=?, currency_code=?, operation_type=?, amount=?, status=?, note=? WHERE incassation_id=?";
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setInt(1, cashDeskId);
                        statement.setTimestamp(2, Timestamp.valueOf(form.dateTime()));
                        statement.setString(3, currencyCode);
                        statement.setString(4, form.type().getDbValue());
                        statement.setDouble(5, amount);
                        statement.setString(6, form.status().getDbValue());
                        statement.setString(7, form.note());
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
            AlertUtil.success("Инкассации", "Запись успешно изменена.");
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось изменить запись: " + e.getMessage());
        }
    }

    @FXML
    private void deleteIncassation(ActionEvent event) {
        Incassation selected = incassationTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите запись для удаления.");
            return;
        }

        Node owner = event != null && event.getSource() instanceof Node node ? node : incassationTable;
        DeleteConfirmationUtil.show(
                owner,
                "Удалить запись?",
                selected.getCurrencyCode() + " · " + selected.getIncassationDate().format(DATE_TIME_FORMATTER),
                () -> deleteIncassation(selected)
        );
    }

    private void deleteIncassation(Incassation selected) {
        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);
            try {
                cashDeskBalanceService.revertIncassationIfCompleted(
                    connection,
                    selected.getCashDeskId(),
                    selected.getCurrencyCode(),
                    selected.getAmount(),
                    IncassationType.fromDbValue(selected.getOperationType()),
                    IncassationStatus.fromDbValue(selected.getStatus())
                );

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
            AlertUtil.success("Инкассации", "Запись успешно удалена.");
        } catch (IllegalArgumentException e) {
            AlertUtil.warning("Валидация", e.getMessage());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось удалить запись: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        currentPage = 0;
        applyFilters();
    }

    @FXML
    private void previousPage() {
        if (currentPage <= 0) {
            return;
        }
        currentPage--;
        applyFilters();
    }

    @FXML
    private void nextPage() {
        if (currentPage + 1 >= totalPages) {
            return;
        }
        currentPage++;
        applyFilters();
    }

    private void applyFilters() {
        String selectedStatus = selectedStatusFilter();
        String selectedType = selectedTypeFilter();

        data.clear();
        String baseSql = "FROM incassations i JOIN cash_desks cd ON cd.cash_desk_id = i.cash_desk_id JOIN currencies c ON c.currency_code = i.currency_code";

        List<String> conditions = new ArrayList<>();
        List<String> parameters = new ArrayList<>();
        if (ValidationService.isNotBlank(selectedStatus)) {
            conditions.add("i.status=?");
            parameters.add(selectedStatus);
        }
        if (ValidationService.isNotBlank(selectedType)) {
            conditions.add("i.operation_type=?");
            parameters.add(selectedType);
        }

        String whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String countSql = "SELECT COUNT(*) " + baseSql + whereClause;
        totalItems = countIncassations(countSql, parameters);
        totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        if (currentPage >= totalPages) {
            currentPage = Math.max(0, totalPages - 1);
        }

        String sql = "SELECT i.incassation_id, i.cash_desk_id, cd.cash_desk_name, i.incassation_date, i.currency_code, c.currency_name, i.operation_type, i.amount, i.status, i.note "
                + baseSql
                + whereClause
                + " ORDER BY i.incassation_date DESC, i.incassation_id DESC LIMIT ? OFFSET ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = setStringParameters(statement, parameters, 1);
            statement.setInt(index++, PAGE_SIZE);
            statement.setInt(index, currentPage * PAGE_SIZE);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    data.add(new Incassation(
                            resultSet.getInt("incassation_id"),
                            resultSet.getInt("cash_desk_id"),
                            resultSet.getString("cash_desk_name"),
                            resultSet.getTimestamp("incassation_date").toLocalDateTime(),
                            resultSet.getString("currency_code"),
                            resultSet.getString("currency_name"),
                            resultSet.getString("operation_type"),
                            resultSet.getDouble("amount"),
                            resultSet.getString("status"),
                            resultSet.getString("note")
                    ));
                }
            }
            updatePaginationControls();
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось применить фильтр: " + e.getMessage());
        }
    }

    private int countIncassations(String sql, List<String> parameters) {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setStringParameters(statement, parameters, 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось посчитать инкассации: " + e.getMessage());
            return 0;
        }
    }

    private int setStringParameters(PreparedStatement statement, List<String> parameters, int startIndex) throws SQLException {
        int index = startIndex;
        for (String parameter : parameters) {
            statement.setString(index++, parameter);
        }
        return index;
    }

    private void updatePaginationControls() {
        if (pageInfoLabel != null) {
            pageInfoLabel.setText("Страница " + (currentPage + 1) + " из " + totalPages + " · записей: " + totalItems);
        }
        if (previousPageButton != null) {
            previousPageButton.setDisable(currentPage <= 0);
        }
        if (nextPageButton != null) {
            nextPageButton.setDisable(currentPage + 1 >= totalPages);
        }
    }

    private List<String> loadStatusFilterOptions() {
        List<String> options = new ArrayList<>();
        options.add(ALL_FILTER);
        for (IncassationStatus status : IncassationStatus.values()) {
            options.add(status.getDbValue());
        }
        return options;
    }

    private List<String> loadTypeFilterOptions() {
        List<String> options = new ArrayList<>();
        options.add(ALL_FILTER);
        for (IncassationType type : IncassationType.values()) {
            options.add(type.getDbValue());
        }
        return options;
    }

    private String selectedStatusFilter() {
        String value = filterStatusBox.getValue();
        return ALL_FILTER.equals(value) ? "" : value;
    }

    private String selectedTypeFilter() {
        String value = filterTypeBox.getValue();
        return ALL_FILTER.equals(value) ? "" : value;
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

    private boolean isValidTime(String value) {
        try {
            parseTime(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Время должно быть в формате HH:mm.");
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Время должно быть в формате HH:mm.");
        }
    }

    private LocalDateTime combineDateAndTime(LocalDate date, String time) {
        if (date == null) {
            return null;
        }
        return date.atTime(parseTime(time)).truncatedTo(ChronoUnit.MINUTES);
    }

    private String initialTimeText(LocalDateTime dateTime) {
        LocalTime time = dateTime == null
                ? LocalTime.now().truncatedTo(ChronoUnit.MINUTES)
                : dateTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES);
        return time.format(TIME_FORMATTER);
    }

    private Optional<IncassationForm> showIncassationDialog(Incassation incassation) {
        Dialog<ButtonType> dialog = new Dialog<>();
        ModalDialogUtil.configureFormDialog(
                dialog,
                incassation == null ? "Добавить запись" : "Изменить запись",
                "fas-truck-loading"
        );

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        SearchableComboBox<CashDesk> cashDeskInput = new SearchableComboBox<>();
        cashDeskInput.getItems().setAll(loadCashDesks());
        DatePicker dateInput = new DatePicker(incassation == null ? LocalDate.now() : incassation.getIncassationDate().toLocalDate());
        boolean isEditing = incassation != null;
        TextField timeInput = new TextField(initialTimeText(incassation == null ? null : incassation.getIncassationDate()));
        timeInput.setPromptText("HH:mm");
        Label timeLabel = new Label("Время:");
        timeInput.setVisible(isEditing);
        timeInput.setManaged(isEditing);
        timeLabel.setVisible(isEditing);
        timeLabel.setManaged(isEditing);
        SearchableComboBox<Currency> currencyInput = new SearchableComboBox<>();
        currencyInput.getItems().setAll(loadCurrencies());
        TextField amountInput = new TextField(incassation == null ? "" : String.valueOf(incassation.getAmount()));
        SearchableComboBox<IncassationType> typeInput = new SearchableComboBox<>();
        typeInput.getItems().setAll(IncassationType.values());
        SearchableComboBox<IncassationStatus> statusInput = new SearchableComboBox<>();
        statusInput.getItems().setAll(IncassationStatus.values());
        TextArea noteInput = new TextArea(incassation == null ? "" : nullToBlank(incassation.getNote()));
        noteInput.setPrefRowCount(3);
        noteInput.setWrapText(true);

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
        grid.add(timeLabel, 0, 2);
        grid.add(timeInput, 1, 2);
        grid.add(new Label("Валюта:"), 0, 3);
        grid.add(currencyInput, 1, 3);
        grid.add(new Label("Тип:"), 0, 4);
        grid.add(typeInput, 1, 4);
        grid.add(new Label("Сумма:"), 0, 5);
        grid.add(amountInput, 1, 5);
        grid.add(new Label("Статус:"), 0, 6);
        grid.add(statusInput, 1, 6);
        grid.add(new Label("Примечание:"), 0, 7);
        grid.add(noteInput, 1, 7);

        dialog.getDialogPane().setContent(grid);
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(null);
        validationSupport.setErrorDecorationEnabled(false);
        validationSupport.registerValidator(cashDeskInput, Validator.createEmptyValidator("Выберите кассу."));
        validationSupport.registerValidator(dateInput, Validator.createPredicateValidator(
                (LocalDate value) -> value != null,
                "Выберите дату.",
                Severity.ERROR
        ));
        if (isEditing) {
            validationSupport.registerValidator(timeInput, Validator.createPredicateValidator(
                    this::isValidTime,
                    "Введите время в формате HH:mm.",
                    Severity.ERROR
            ));
        }
        validationSupport.registerValidator(currencyInput, Validator.createEmptyValidator("Выберите валюту."));
        validationSupport.registerValidator(typeInput, Validator.createEmptyValidator("Выберите тип операции."));
        validationSupport.registerValidator(amountInput, Validator.createPredicateValidator(
                this::isPositiveNumber,
                "Сумма должна быть числом больше 0.",
                Severity.ERROR
        ));
        validationSupport.registerValidator(statusInput, Validator.createEmptyValidator("Выберите статус."));
        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.disableProperty().bind(validationSupport.invalidProperty());

        Optional<ButtonType> result = ModalDialogUtil.showAndWait(dialog);
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        LocalDateTime dateTime = isEditing
                ? combineDateAndTime(dateInput.getValue(), timeInput.getText())
                : dateInput.getValue().atTime(LocalTime.now().truncatedTo(ChronoUnit.MINUTES));
        
        return Optional.of(new IncassationForm(
                cashDeskInput.getValue(),
                dateTime,
                currencyInput.getValue(),
                typeInput.getValue(),
                amountInput.getText().trim(),
                statusInput.getValue(),
                noteInput.getText().trim()
        ));
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record IncassationForm(
            CashDesk cashDesk,
            LocalDateTime dateTime,
            Currency currency,
            IncassationType type,
            String amount,
            IncassationStatus status,
            String note
    ) {
    }

    private List<CashDesk> loadCashDesks() {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT cash_desk_id, cash_desk_name, address, status FROM cash_desks ORDER BY cash_desk_name");
             ResultSet resultSet = statement.executeQuery()) {
            List<CashDesk> desks = new ArrayList<>();
            while (resultSet.next()) {
                desks.add(new CashDesk(
                        resultSet.getInt("cash_desk_id"),
                        resultSet.getString("cash_desk_name"),
                        resultSet.getString("address"),
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





