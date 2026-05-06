package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.util.AlertUtil;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;

public class DashboardController {
    @FXML
    private Label currenciesCount;
    @FXML
    private Label cashDesksCount;
    @FXML
    private Label operationsCount;
    @FXML
    private Label incassationsCount;
    @FXML
    private BarChart<String, Number> operationsChart;
    @FXML
    private PieChart recommendationChart;
    @FXML
    private VBox alertsContainer;

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");

    @FXML
    public void initialize() {
        if (operationsChart != null) {
            operationsChart.setLegendVisible(false);
            operationsChart.setAnimated(false);
        }
        if (recommendationChart != null) {
            recommendationChart.setLegendVisible(false);
            recommendationChart.setLabelsVisible(false);
            recommendationChart.setAnimated(false);
        }
        refreshStats();
    }

    public void refreshStats() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            loadCounts(conn);
            loadOperationsChart(conn);
            loadRecommendationChart(conn);
            loadAttentionCards(conn);
        } catch (SQLException e) {
            AlertUtil.error("Ошибка БД", "Не удалось загрузить статистику: " + e.getMessage());
        }
    }

    private void loadCounts(Connection conn) throws SQLException {
        currenciesCount.setText(loadCount(conn, "SELECT count(*) FROM currencies"));
        cashDesksCount.setText(loadCount(conn, "SELECT count(*) FROM cash_desks"));
        operationsCount.setText(loadCount(conn, "SELECT count(*) FROM exchange_operations"));
        incassationsCount.setText(loadCount(conn, "SELECT count(*) FROM incassations"));
    }

    private String loadCount(Connection conn, String sql) throws SQLException {
        try (PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            return rs.next() ? String.valueOf(rs.getInt(1)) : "0";
        }
    }

    private void loadOperationsChart(Connection conn) {
        if (operationsChart == null) {
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        String sql = "SELECT operation_date::date AS d, COUNT(*) AS total " +
                "FROM exchange_operations " +
                "WHERE operation_date >= CURRENT_DATE - INTERVAL '6 day' " +
                "GROUP BY d ORDER BY d";

        try (PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                String label = rs.getDate("d").toLocalDate().format(SHORT_DATE);
                series.getData().add(new XYChart.Data<>(label, rs.getInt("total")));
            }
        } catch (SQLException ignored) {
            // Dashboard is informative only.
        }

        operationsChart.getData().setAll(series);
    }

    private void loadRecommendationChart(Connection conn) {
        if (recommendationChart == null) {
            return;
        }

        recommendationChart.getData().clear();
        String sql = "SELECT COALESCE(recommendation, 'Без статуса') AS recommendation, COUNT(*) AS total " +
                "FROM cash_desk_status GROUP BY recommendation ORDER BY total DESC";

        try (PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                recommendationChart.getData().add(
                        new PieChart.Data(rs.getString("recommendation"), rs.getInt("total"))
                );
            }
        } catch (SQLException ignored) {
            // View might not exist yet.
        }
    }

    private void loadAttentionCards(Connection conn) {
        if (alertsContainer == null) {
            return;
        }

        alertsContainer.getChildren().clear();
        String sql = "SELECT cash_desk_name, address, total_balance_mdl, recommendation " +
                "FROM cash_desk_status WHERE recommendation IS NOT NULL ORDER BY cash_desk_name";

        try (PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            boolean hasRows = false;
            while (rs.next()) {
                hasRows = true;
                alertsContainer.getChildren().add(createAlertCard(
                        rs.getString("cash_desk_name"),
                        rs.getString("address"),
                        rs.getDouble("total_balance_mdl"),
                        rs.getString("recommendation")
                ));
            }
            if (!hasRows) {
                alertsContainer.getChildren().add(createEmptyState("Все кассы работают в нормальном диапазоне."));
            }
        } catch (SQLException ignored) {
            alertsContainer.getChildren().add(createEmptyState("Панель внимания будет доступна после создания представления cash_desk_status."));
        }
    }

    private HBox createAlertCard(String name, String address, double balance, String recommendation) {
        HBox root = new HBox(14);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(14, 16, 14, 16));
        root.getStyleClass().add("attention-card");

        Label icon = new Label("🏦");
        icon.getStyleClass().add("attention-icon");

        VBox textBox = new VBox(4);
        Label title = new Label(name);
        title.getStyleClass().add("attention-title");
        Label subtitle = new Label(address);
        subtitle.getStyleClass().add("attention-subtitle");
        textBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        VBox right = new VBox(8);
        right.setAlignment(Pos.CENTER_RIGHT);
        Label amount = new Label(String.format("%.2f MDL", balance));
        amount.getStyleClass().add("attention-balance");
        Label badge = new Label(recommendation == null || recommendation.isBlank() ? "Норма" : recommendation);
        badge.getStyleClass().addAll("status-pill", recommendationStyleClass(recommendation));
        right.getChildren().addAll(amount, badge);

        root.getChildren().addAll(icon, textBox, spacer, right);
        return root;
    }

    private Label createEmptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state-label");
        return label;
    }

    private String recommendationStyleClass(String recommendation) {
        if (recommendation == null || recommendation.isBlank() || "Норма".equalsIgnoreCase(recommendation)) {
            return "status-pill-success";
        }
        String value = recommendation.toLowerCase();
        if (value.contains("пополн")) {
            return "status-pill-danger";
        }
        if (value.contains("инкассац")) {
            return "status-pill-warning";
        }
        return "status-pill-neutral";
    }
}
