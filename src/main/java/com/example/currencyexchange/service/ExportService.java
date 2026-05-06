package com.example.currencyexchange.service;

import javafx.collections.ObservableList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

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
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(String.join(",", headers));
            writer.write("\n");
            for (ObservableList<String> row : rows) {
                writer.write(String.join(",", row));
                writer.write("\n");
            }
        }
    }
}

