package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.model.Currency;
import com.example.currencyexchange.service.ValidationService;
import com.example.currencyexchange.util.AlertUtil;
import com.example.currencyexchange.util.CurrencyCodeUtil;
import com.example.currencyexchange.util.CurrencyFlagUtil;
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
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import org.controlsfx.validation.Severity;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CurrencyController {
    @FXML
    private TextField searchField;
    @FXML
    private Button clearSearchButton;
    @FXML
    private TilePane currencyCardsContainer;
    @FXML
    private Label currenciesTotalLabel;
    @FXML
    private Label selectedCurrencyLabel;
    @FXML
    private Label searchMetaLabel;

    private final ObservableList<Currency> displayedCurrencies = FXCollections.observableArrayList();
    private Currency selectedCurrency;

    @FXML
    public void initialize() {
        clearSearchButton.visibleProperty().bind(Bindings.isNotEmpty(searchField.textProperty()));
        clearSearchButton.managedProperty().bind(clearSearchButton.visibleProperty());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> searchCurrencies());
        refreshTable();
    }

    @FXML
    private void addCurrency() {
        Optional<CurrencyForm> formResult = showCurrencyDialog(null);
        if (formResult.isEmpty()) {
            return;
        }
        CurrencyForm form = formResult.get();

        try {
            if (!validateCurrencyForm(form)) {
                return;
            }

            String sql = "INSERT INTO currencies(currency_code, currency_name) VALUES (?, ?)";
            try (Connection connection = DatabaseConnection.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    try (PreparedStatement statement = connection.prepareStatement(sql)) {
                        statement.setString(1, form.code());
                        statement.setString(2, form.name());
                        statement.executeUpdate();
                    }
                    createZeroBalancesForCurrency(connection, form.code());
                    connection.commit();
                } catch (SQLException | RuntimeException e) {
                    rollbackQuietly(connection);
                    throw e;
                }
            }
            refreshTable();
            AlertUtil.success("Валюты", "Валюта успешно добавлена.");
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось добавить валюту: " + e.getMessage());
        }
    }

    @FXML
    private void updateCurrency() {
        Currency selected = selectedCurrency;
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите валюту на карточке для изменения.");
            return;
        }

        Optional<CurrencyForm> formResult = showCurrencyDialog(selected);
        if (formResult.isEmpty()) {
            return;
        }
        CurrencyForm form = formResult.get();

        try {
            if (!validateCurrencyForm(form)) {
                return;
            }

            String sql = "UPDATE currencies SET currency_code=?, currency_name=? WHERE currency_code=?";
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, form.code());
                statement.setString(2, form.name());
                statement.setString(3, selected.getCode());
                statement.executeUpdate();
            }
            refreshTable();
            AlertUtil.success("Валюты", "Валюта успешно изменена.");
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось изменить валюту: " + e.getMessage());
        }
    }

    private void confirmDeleteCurrency(Currency selected, Node owner) {
        if (selected == null) {
            AlertUtil.warning("Валидация", "Выберите валюту на карточке для удаления.");
            return;
        }

        CurrencyUsage usage;
        try {
            usage = loadCurrencyUsage(selected.getCode());
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось проверить связи валюты: " + e.getMessage());
            return;
        }

        if (usage.hasBlockingReferences()) {
            AlertUtil.warning("Валюта используется", buildBlockedDeleteMessage(selected, usage));
            return;
        }

        String message = selected.getCode() + " · " + selected.getName();
        if (usage.rates() > 0) {
            message += "\nКурсы этой валюты тоже будут удалены: " + usage.rates();
        }
        DeleteConfirmationUtil.show(
                owner,
                "Удалить валюту?",
                message,
                () -> deleteCurrency(selected)
        );
    }

    private void deleteCurrency(Currency selected) {
        String sql = "DELETE FROM currencies WHERE currency_code=?";
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, selected.getCode());
            statement.executeUpdate();
            selectedCurrency = null;
            refreshTable();
            AlertUtil.success("Валюты", "Валюта успешно удалена.");
        } catch (SQLException e) {
            if ("23503".equals(e.getSQLState())) {
                AlertUtil.warning("Валюта используется", "Валюту нельзя удалить: на нее уже есть ссылки в операциях обмена или инкассациях.");
                return;
            }
            AlertUtil.error("Ошибка БД", "Не удалось удалить валюту: " + e.getMessage());
        }
    }

    @FXML
    private void refreshTable() {
        String sql = "SELECT currency_code, currency_name FROM currencies ORDER BY currency_code";
        loadCurrencies(sql, null);
        updateMeta("Все валюты в системе");
    }

    @FXML
    private void searchCurrencies() {
        String sql = "SELECT currency_code, currency_name FROM currencies " +
                "WHERE UPPER(currency_code) LIKE ? OR UPPER(currency_name) LIKE ? ORDER BY currency_code";
        String search = "%" + searchField.getText().trim().toUpperCase() + "%";
        loadCurrencies(sql, search);
        updateMeta(searchField.getText().isBlank()
                ? "Все валюты в системе"
                : "Результаты по запросу: “" + searchField.getText().trim() + "”");
    }

    @FXML
    private void clearSearch() {
        searchField.clear();
    }

    private void loadCurrencies(String sql, String searchValue) {
        displayedCurrencies.clear();
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (searchValue != null) {
                statement.setString(1, searchValue);
                statement.setString(2, searchValue);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    displayedCurrencies.add(new Currency(
                            resultSet.getString("currency_code"),
                            resultSet.getString("currency_name")
                    ));
                }
            }
            if (selectedCurrency != null && displayedCurrencies.stream().noneMatch(c -> c.getCode().equals(selectedCurrency.getCode()))) {
                selectedCurrency = null;
            }
            renderCurrencyCards();
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить валюты: " + e.getMessage());
        }
    }

    private void renderCurrencyCards() {
        currencyCardsContainer.getChildren().clear();

        if (displayedCurrencies.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(28));
            empty.getStyleClass().add("empty-state-box");
            Label icon = new Label("FX");
            icon.getStyleClass().add("empty-state-icon");
            Label text = new Label("По вашему запросу валюты не найдены.");
            text.getStyleClass().add("empty-state-label");
            empty.getChildren().addAll(icon, text);
            currencyCardsContainer.getChildren().add(empty);
            updateSummary();
            return;
        }

        for (Currency currency : displayedCurrencies) {
            currencyCardsContainer.getChildren().add(createCurrencyCard(currency));
        }
        updateSummary();
    }

    private VBox createCurrencyCard(Currency currency) {
        VBox card = new VBox(16);
        card.getStyleClass().add("currency-card");
        if (selectedCurrency != null && selectedCurrency.getCode().equals(currency.getCode())) {
            card.getStyleClass().add("selected");
        }
        card.setPadding(new Insets(18));
        card.setPrefWidth(230);
        card.setMinWidth(300);
        card.setMaxWidth(260);

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label codeBadge = new Label(currency.getCode());
        codeBadge.getStyleClass().add("currency-code-badge");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button editButton = new Button();
        IconUtil.setIconOnly(editButton, "fas-pen");
        editButton.getStyleClass().addAll("icon-action-button", "ghost-button");
        editButton.setOnAction(event -> {
            selectCurrency(currency);
            updateCurrency();
            event.consume();
        });
        Button deleteButton = new Button();
        IconUtil.setIconOnly(deleteButton, "fas-trash");
        deleteButton.getStyleClass().addAll("icon-action-button", "danger-ghost-button");
        deleteButton.setOnAction(event -> {
            confirmDeleteCurrency(currency, deleteButton);
            event.consume();
        });
        top.getChildren().addAll(CurrencyFlagUtil.createFlag(currency.getCode()), codeBadge, spacer, editButton, deleteButton);

        VBox body = new VBox(6);
        Label title = new Label(currency.getName());
        title.getStyleClass().add("currency-card-title");
        title.setWrapText(true);
        Label subtitle = new Label("ISO-код: " + currency.getCode());
        subtitle.getStyleClass().add("currency-card-subtitle");
        body.getChildren().addAll(title, subtitle);

        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getChildren().addAll(createCurrencyTags(currency));

        card.getChildren().addAll(top, body, footer);
        card.setOnMouseClicked(event -> selectCurrency(currency));
        return card;
    }

    private java.util.List<Label> createCurrencyTags(Currency currency) {
        if (!isKnownCurrencyCode(currency.getCode())) {
            return java.util.List.of(
                    createSoftChip("Неизвестная валюта", "fas-question-circle"),
                    createSoftChip("Недоступна", "fas-ban")
            );
        }

        return java.util.List.of(
                createSoftChip("Доступна", "fas-check-circle"),
                createSoftChip("Международная", "fas-globe")
        );
    }

    private Label createSoftChip(String text, String iconLiteral) {
        Label chip = new Label(text);
        IconUtil.setIcon(chip, iconLiteral, 12);
        chip.getStyleClass().add("soft-chip");
        return chip;
    }

    private boolean isKnownCurrencyCode(String code) {
        return CurrencyCodeUtil.isKnownCurrencyCode(code);
    }

    private CurrencyUsage loadCurrencyUsage(String code) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection()) {
            return new CurrencyUsage(
                    countCurrencyUsage(
                            connection,
                            "SELECT COUNT(*) FROM exchange_operations WHERE currency_from=? OR currency_to=?",
                            code,
                            code
                    ),
                    countCurrencyUsage(
                            connection,
                            "SELECT COUNT(*) FROM exchange_rates WHERE currency_code=?",
                            code
                    ),
                    countCurrencyUsage(
                            connection,
                            "SELECT COUNT(*) FROM incassations WHERE currency_code=?",
                            code
                    )
            );
        }
    }

    private int countCurrencyUsage(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setString(i + 1, values[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private void createZeroBalancesForCurrency(Connection connection, String code) throws SQLException {
        String sql = "INSERT INTO cash_desk_balances(cash_desk_id, currency_code, balance) " +
                "SELECT cash_desk_id, ?, 0 FROM cash_desks " +
                "ON CONFLICT (cash_desk_id, currency_code) DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
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

    private String buildBlockedDeleteMessage(Currency currency, CurrencyUsage usage) {
        List<String> reasons = new ArrayList<>();
        if (usage.operations() > 0) {
            reasons.add("операции обмена: " + usage.operations());
        }
        if (usage.incassations() > 0) {
            reasons.add("инкассации: " + usage.incassations());
        }

        return currency.getCode() + " нельзя удалить: " + String.join("; ", reasons) + ". Сначала удалите связанные записи.";
    }

    private void selectCurrency(Currency currency) {
        selectedCurrency = currency;
        renderCurrencyCards();
    }

    private void updateSummary() {
        if (currenciesTotalLabel != null) {
            currenciesTotalLabel.setText(String.valueOf(displayedCurrencies.size()));
        }
        if (selectedCurrencyLabel != null) {
            selectedCurrencyLabel.setText(selectedCurrency == null
                    ? "—"
                    : selectedCurrency.getCode() + " · " + selectedCurrency.getName());
        }
    }

    private void updateMeta(String text) {
        if (searchMetaLabel != null) {
            searchMetaLabel.setText(text);
        }
        updateSummary();
    }

    private boolean validateCurrencyForm(CurrencyForm form) {
        if (!ValidationService.isNotBlank(form.code()) || !ValidationService.isNotBlank(form.name())) {
            AlertUtil.warning("Валидация", "Код и название валюты обязательны.");
            return false;
        }
        if (form.code().length() != 3) {
            AlertUtil.warning("Валидация", "Код валюты должен содержать 3 символа.");
            return false;
        }
        return true;
    }

    private Optional<CurrencyForm> showCurrencyDialog(Currency currency) {
        Dialog<ButtonType> dialog = new Dialog<>();
        ModalDialogUtil.configureFormDialog(
                dialog,
                currency == null ? "Добавить валюту" : "Изменить валюту",
                "fas-coins"
        );

        ButtonType saveButton = new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, cancelButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        TextField codeInput = new TextField(currency == null ? "" : currency.getCode());
        TextField nameInput = new TextField(currency == null ? "" : currency.getName());

        grid.add(new Label("Код валюты:"), 0, 0);
        grid.add(codeInput, 1, 0);
        grid.add(new Label("Название:"), 0, 1);
        grid.add(nameInput, 1, 1);

        dialog.getDialogPane().setContent(grid);
        ValidationSupport validationSupport = new ValidationSupport();
        validationSupport.setValidationDecorator(null);
        validationSupport.setErrorDecorationEnabled(false);
        validationSupport.registerValidator(codeInput, Validator.combine(
                Validator.createEmptyValidator("Код валюты обязателен."),
                Validator.createRegexValidator("Код должен состоять из 3 латинских букв.", "[A-Za-z]{3}", Severity.ERROR)
        ));
        validationSupport.registerValidator(nameInput, Validator.createEmptyValidator("Название валюты обязательно."));
        Node saveNode = dialog.getDialogPane().lookupButton(saveButton);
        saveNode.disableProperty().bind(validationSupport.invalidProperty());

        Optional<ButtonType> result = ModalDialogUtil.showAndWait(dialog);
        if (result.isEmpty() || result.get() != saveButton) {
            return Optional.empty();
        }

        String code = codeInput.getText().trim().toUpperCase();
        String name = nameInput.getText().trim();
        return Optional.of(new CurrencyForm(code, name));
    }

    private record CurrencyForm(String code, String name) {
    }

    private record CurrencyUsage(int operations, int rates, int incassations) {
        private boolean hasBlockingReferences() {
            return operations > 0 || incassations > 0;
        }
    }
}
