package com.example.currencyexchange.service;

import javafx.collections.ObservableList;

import java.io.File;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class ExportService {
    private final Exportable exporter = new CsvExportService();

    public void exportCsv(File file, List<String> headers, ObservableList<ObservableList<String>> rows) throws IOException {
        exporter.export(file, headers, rows);
    }
}

interface Exportable {
    void export(File file, List<String> headers, ObservableList<ObservableList<String>> rows) throws IOException;
}

class CsvExportService implements Exportable {
    @Override
    public void export(File file, List<String> headers, ObservableList<ObservableList<String>> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writeCsvRow(writer, headers);
            for (ObservableList<String> row : rows) {
                writeCsvRow(writer, row);
            }
        }
    }

    private void writeCsvRow(BufferedWriter writer, List<String> values) throws IOException {
        writer.write(values.stream()
                .map(this::escapeCsv)
                .collect(Collectors.joining(",")));
        writer.write(System.lineSeparator());
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        boolean needsQuotes = value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }
}

