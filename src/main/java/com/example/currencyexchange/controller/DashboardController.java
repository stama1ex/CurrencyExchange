package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.service.ExchangeRateAutoUpdateService;
import com.example.currencyexchange.util.AlertUtil;
import com.example.currencyexchange.util.IconUtil;
import javafx.animation.PauseTransition;
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
import javafx.util.Duration;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DashboardController {
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Chisinau");

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
    private PauseTransition dailyRefresh;

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM");

    @FXML
    public void initialize() {
        if (operationsChart != null) {
            operationsChart.setLegendVisible(false);
            operationsChart.setAnimated(false);
        }
        if (recommendationChart != null) {
            recommendationChart.setLegendVisible(true);
            recommendationChart.setLabelsVisible(false);
            recommendationChart.setAnimated(false);
        }
        refreshStats();
        installDailyRefresh();
    }

    private void installDailyRefresh() {
        if (operationsChart == null) {
            return;
        }
        operationsChart.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                stopDailyRefresh();
            } else {
                scheduleNextDailyRefresh();
            }
        });
        scheduleNextDailyRefresh();
    }

    private void scheduleNextDailyRefresh() {
        stopDailyRefresh();

        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(APP_ZONE).plusSeconds(1);
        long delayMillis = Math.max(1_000, java.time.Duration.between(now, nextMidnight).toMillis());

        dailyRefresh = new PauseTransition(Duration.millis(delayMillis));
        dailyRefresh.setOnFinished(event -> {
            refreshStats();
            scheduleNextDailyRefresh();
        });
        dailyRefresh.play();
    }

    private void stopDailyRefresh() {
        if (dailyRefresh != null) {
            dailyRefresh.stop();
            dailyRefresh = null;
        }
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

        operationsChart.setTitle(null);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        LocalDate today = ExchangeRateAutoUpdateService.todayInAppZone();
        String sql = "WITH days AS ( " +
                "SELECT generate_series(?::date - INTERVAL '6 day', ?::date, INTERVAL '1 day')::date AS d " +
                ") " +
                "SELECT days.d, COUNT(eo.operation_id) AS total " +
                "FROM days " +
                "LEFT JOIN exchange_operations eo ON eo.operation_date = days.d " +
                "GROUP BY days.d ORDER BY days.d";

        try (PreparedStatement st = conn.prepareStatement(sql)) {
            st.setDate(1, Date.valueOf(today));
            st.setDate(2, Date.valueOf(today));
            try (ResultSet rs = st.executeQuery()) {
                int totalOperations = 0;
                while (rs.next()) {
                    String label = rs.getDate("d").toLocalDate().format(SHORT_DATE);
                    int total = rs.getInt("total");
                    totalOperations += total;
                    series.getData().add(new XYChart.Data<>(label, total));
                }
                if (totalOperations == 0) {
                    operationsChart.setTitle("Нет операций для отображения");
                }
            }
        } catch (SQLException e) {
            operationsChart.setTitle("Не удалось загрузить динамику операций");
        }

        operationsChart.getData().setAll(series);
    }

    private void loadRecommendationChart(Connection conn) {
        if (recommendationChart == null) {
            return;
        }

        recommendationChart.getData().clear();
        recommendationChart.setTitle(null);
        String sql = "SELECT COALESCE(NULLIF(TRIM(recommendation), ''), 'Без статуса') AS recommendation, COUNT(*) AS total " +
                "FROM cash_desk_status GROUP BY 1 ORDER BY total DESC";

        try {
            populateRecommendationChart(conn, sql);
        } catch (SQLException e) {
            recommendationChart.getData().clear();
            try {
                populateRecommendationChart(conn, fallbackRecommendationSql());
            } catch (SQLException fallbackException) {
                recommendationChart.getData().clear();
                recommendationChart.setTitle("Не удалось загрузить рекомендации");
            }
        }
    }

    private void populateRecommendationChart(Connection conn, String sql) throws SQLException {
        int totalDesks = 0;
        List<RecommendationSlice> slices = new ArrayList<>();
        try (PreparedStatement st = conn.prepareStatement(sql); ResultSet rs = st.executeQuery()) {
            while (rs.next()) {
                String recommendation = rs.getString("recommendation");
                int total = rs.getInt("total");
                totalDesks += total;
                slices.add(new RecommendationSlice(recommendation, total));
            }
        }
        slices.sort(Comparator.comparingInt(slice -> recommendationPriority(slice.recommendation())));
        for (RecommendationSlice slice : slices) {
            recommendationChart.getData().add(
                    new PieChart.Data(slice.recommendation() + " (" + slice.total() + ")", slice.total())
            );
        }
        if (totalDesks == 0) {
            recommendationChart.setTitle("Нет касс для отображения");
        }
    }

    private int recommendationPriority(String recommendation) {
        if (recommendation == null) {
            return 4;
        }
        String value = recommendation.toLowerCase();
        if (value.contains("инкасс")) {
            return 1;
        }
        if (value.contains("пополн")) {
            return 2;
        }
        if (value.contains("норма")) {
            return 3;
        }
        return 4;
    }

    private record RecommendationSlice(String recommendation, int total) {
    }

    private String fallbackRecommendationSql() {
        return "SELECT recommendation, COUNT(*) AS total " +
                "FROM ( " +
                "SELECT CASE " +
                "WHEN total_balance_mdl > max_limit_mdl THEN 'Нужна инкассация' " +
                "WHEN total_balance_mdl < min_limit_mdl THEN 'Нужно пополнение' " +
                "ELSE 'Норма' END AS recommendation " +
                "FROM ( " +
                "SELECT cd.min_limit_mdl, cd.max_limit_mdl, ROUND( " +
                "cd.balance_mdl + " +
                "cd.balance_ron * COALESCE((SELECT er.sell_rate_mdl FROM exchange_rates er WHERE er.currency_code = 'RON' ORDER BY er.rate_date DESC, er.rate_id DESC LIMIT 1), 0) + " +
                "cd.balance_eur * COALESCE((SELECT er.sell_rate_mdl FROM exchange_rates er WHERE er.currency_code = 'EUR' ORDER BY er.rate_date DESC, er.rate_id DESC LIMIT 1), 0) + " +
                "cd.balance_usd * COALESCE((SELECT er.sell_rate_mdl FROM exchange_rates er WHERE er.currency_code = 'USD' ORDER BY er.rate_date DESC, er.rate_id DESC LIMIT 1), 0), 2) AS total_balance_mdl " +
                "FROM cash_desks cd " +
                ") balances " +
                ") recommendations " +
                "GROUP BY recommendation ORDER BY total DESC";
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

        Label icon = new Label();
        IconUtil.setIconOnly(icon, "fas-cash-register");
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
        Label amount = new Label(String.format("≈ %.2f MDL", balance));
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
            return "status-pill-info";
        }
        return "status-pill-neutral";
    }
}
