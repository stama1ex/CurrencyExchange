package com.example.currencyexchange.service;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceTest {
    @Test
    void exportsUtf8CsvWithEscapedCells() throws Exception {
        Path file = Files.createTempFile("exchange-report", ".csv");
        ObservableList<String> row = FXCollections.observableArrayList(
                "Касса Центр",
                "10,50",
                "текст \"с кавычками\"",
                "строка\nдве"
        );
        ObservableList<ObservableList<String>> rows = FXCollections.observableArrayList();
        rows.add(row);

        new ExportService().exportCsv(
                file.toFile(),
                List.of("Касса", "Сумма", "Примечание", "Многострочно"),
                rows
        );

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFF"));
        assertTrue(content.contains("Касса Центр"));
        assertTrue(content.contains("\"10,50\""));
        assertTrue(content.contains("\"текст \"\"с кавычками\"\"\""));
        assertTrue(content.contains("\"строка\nдве\""));
    }
}
