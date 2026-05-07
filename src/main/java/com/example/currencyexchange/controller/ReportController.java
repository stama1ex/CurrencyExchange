package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.service.ExportService;
import com.example.currencyexchange.util.AlertUtil;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.FileChooser;
import org.controlsfx.control.CheckComboBox;
import org.controlsfx.control.SearchableComboBox;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReportController {
    @FXML
    private SearchableComboBox<String> reportTypeBox;
    @FXML
    private CheckComboBox<String> columnSelectorBox;
    @FXML
    private TableView<ObservableList<String>> reportTable;

    private final ObservableList<ObservableList<String>> data = FXCollections.observableArrayList();
    private final List<String> headers = new ArrayList<>();
    private final ExportService exportService = new ExportService();

    @FXML
    public void initialize() {
        // Регистрируем этот контроллер в MainController для уведомлений об обновлениях
        MainController.setReportController(this);

        columnSelectorBox.setTitle("Все");
        columnSelectorBox.setShowCheckedCount(true);
        columnSelectorBox.getCheckModel().getCheckedItems()
                .addListener((ListChangeListener<String>) change -> applyColumnSelection());
        
        reportTypeBox.getItems().addAll(
                "Статус касс (cash_desk_status)",
                "Операции обмена (exchange_operations_view)",
                "Инкассации (incassations_view)"
        );
        reportTypeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadReport();
            }
        });
        reportTable.setItems(data);
        reportTypeBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void loadReport() {
        String selected = reportTypeBox.getValue();
        if (selected == null) {
            return;
        }
        loadReportInternal(selected);
    }

    /**
     * Публичный метод для обновления отчета из других контроллеров
     * (вызывается при добавлении/обновлении/удалении операций)
     */
    public void refreshReport() {
        String selected = reportTypeBox.getValue();
        if (selected != null) {
            loadReportInternal(selected);
        }
    }

    private void loadReportInternal(String selected) {
        String sql;
        if (selected.contains("cash_desk_status")) {
            sql = "SELECT cash_desk_name, address, total_balance_mdl, recommendation FROM cash_desk_status ORDER BY cash_desk_name";
        } else if (selected.contains("exchange_operations_view")) {
            sql = "SELECT cd.cash_desk_name AS \"Касса\", to_char(eo.operation_date, 'DD.MM.YYYY HH24:MI') AS \"Дата и время\", cf.currency_name AS \"Из валюты\", " +
                    "ct.currency_name AS \"В валюту\", eo.amount_from AS \"Сумма из\", eo.rate AS \"Курс\", eo.amount_to AS \"Сумма в\" " +
                    "FROM exchange_operations eo " +
                    "JOIN cash_desks cd ON cd.cash_desk_id = eo.cash_desk_id " +
                    "JOIN currencies cf ON cf.currency_code = eo.currency_from " +
                    "JOIN currencies ct ON ct.currency_code = eo.currency_to " +
                    "ORDER BY eo.operation_date DESC, eo.operation_id DESC";
        } else {
            sql = "SELECT cd.cash_desk_name AS \"Касса\", to_char(i.incassation_date, 'DD.MM.YYYY HH24:MI') AS \"Дата и время\", c.currency_name AS \"Валюта\", " +
                    "i.operation_type AS \"Тип\", i.amount AS \"Сумма\", i.status AS \"Статус\", COALESCE(i.note, '') AS \"Примечание\" " +
                    "FROM incassations i " +
                    "JOIN cash_desks cd ON cd.cash_desk_id = i.cash_desk_id " +
                    "JOIN currencies c ON c.currency_code = i.currency_code " +
                    "ORDER BY i.incassation_date DESC, i.incassation_id DESC";
        }

        data.clear();
        headers.clear();
        reportTable.getColumns().clear();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            ResultSetMetaData metaData = resultSet.getMetaData();
            int columns = metaData.getColumnCount();

            for (int i = 1; i <= columns; i++) {
                String header = metaData.getColumnLabel(i);
                headers.add(header);
                final int columnIndex = i - 1;
                TableColumn<ObservableList<String>, String> column = new TableColumn<>(header);
                column.setCellValueFactory(cellData -> {
                    ObservableList<String> row = cellData.getValue();
                    String value = columnIndex < row.size() ? row.get(columnIndex) : "";
                    return new SimpleStringProperty(value);
                });
                reportTable.getColumns().add(column);
            }
            columnSelectorBox.getItems().setAll(headers);
            columnSelectorBox.getCheckModel().checkAll();

            while (resultSet.next()) {
                ObservableList<String> row = FXCollections.observableArrayList();
                for (int i = 1; i <= columns; i++) {
                    Object value = resultSet.getObject(i);
                    row.add(value == null ? "" : value.toString());
                }
                data.add(row);
            }
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить отчет: " + e.getMessage());
        }
    }

    @FXML
    private void exportCsv() {
        if (data.isEmpty()) {
            AlertUtil.warning("Экспорт", "Нет данных для экспорта.");
            return;
        }
        List<Integer> selectedColumnIndexes = getSelectedColumnIndexes();
        if (selectedColumnIndexes.isEmpty()) {
            AlertUtil.warning("Экспорт", "Выберите хотя бы одну колонку для экспорта.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Сохранить CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("report.csv");

        File file = chooser.showSaveDialog(reportTable.getScene().getWindow());
        if (file == null) {
            return;
        }

        try {
            exportService.exportCsv(file, getSelectedHeaders(selectedColumnIndexes), getSelectedRows(selectedColumnIndexes));
            AlertUtil.info("Экспорт", "Файл успешно сохранен: " + file.getAbsolutePath());
        } catch (Exception e) {
            AlertUtil.error("Экспорт", "Не удалось экспортировать файл: " + e.getMessage());
        }
    }

    private void applyColumnSelection() {
        if (reportTable == null || headers.isEmpty()) {
            return;
        }

        List<String> selectedHeaders = columnSelectorBox.getCheckModel().getCheckedItems();
        for (TableColumn<ObservableList<String>, ?> column : reportTable.getColumns()) {
            column.setVisible(selectedHeaders.contains(column.getText()));
        }
    }

    private List<Integer> getSelectedColumnIndexes() {
        List<String> selectedHeaders = columnSelectorBox.getCheckModel().getCheckedItems();
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            if (selectedHeaders.contains(headers.get(i))) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private List<String> getSelectedHeaders(List<Integer> indexes) {
        List<String> selectedHeaders = new ArrayList<>();
        for (int index : indexes) {
            selectedHeaders.add(headers.get(index));
        }
        return selectedHeaders;
    }

    private ObservableList<ObservableList<String>> getSelectedRows(List<Integer> indexes) {
        ObservableList<ObservableList<String>> selectedRows = FXCollections.observableArrayList();
        for (ObservableList<String> row : data) {
            ObservableList<String> selectedRow = FXCollections.observableArrayList();
            for (int index : indexes) {
                selectedRow.add(index < row.size() ? row.get(index) : "");
            }
            selectedRows.add(selectedRow);
        }
        return selectedRows;
    }
}



