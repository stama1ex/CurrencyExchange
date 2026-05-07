package com.example.currencyexchange.controller;

import com.example.currencyexchange.DatabaseConnection;
import com.example.currencyexchange.util.AlertUtil;
import com.example.currencyexchange.util.IconUtil;
import com.example.currencyexchange.util.SmoothScrollUtil;
import com.example.currencyexchange.util.ThemeManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.sql.Connection;

public class MainController {
    private static final String ACTIVE_CLASS = "active";

    // Статическая ссылка на ReportController для уведомлений об обновлении данных
    private static ReportController reportController;

    @FXML
    private StackPane appRoot;
    @FXML
    private StackPane contentArea;
    @FXML
    private VBox toastHost;
    @FXML
    private ScrollPane sidebarScrollPane;
    @FXML
    private Button dashboardButton;
    @FXML
    private Button currenciesButton;
    @FXML
    private Button exchangeRatesButton;
    @FXML
    private Button cashDesksButton;
    @FXML
    private Button exchangeOperationsButton;
    @FXML
    private Button incassationsButton;
    @FXML
    private Button reportsButton;
    @FXML
    private Button themeToggleButton;

    @FXML
    public void initialize() {
        ThemeManager.install(appRoot);
        updateThemeToggle(ThemeManager.isDarkTheme());
        ThemeManager.darkThemeProperty().addListener((obs, wasDark, isDark) -> updateThemeToggle(isDark));
        AlertUtil.setToastHost(toastHost);
        testDatabaseConnection();
        SmoothScrollUtil.install(sidebarScrollPane);
        // Показываем Dashboard по умолчанию
        showDashboard();
    }

    @FXML
    private void toggleTheme() {
        ThemeManager.toggleTheme();
    }

    private void updateThemeToggle(boolean dark) {
        themeToggleButton.setText(dark ? "Светлая тема" : "Темная тема");
        IconUtil.setIcon(themeToggleButton, dark ? "fas-sun" : "fas-moon", 14);
        themeToggleButton.getStyleClass().remove("toggle-on");
        if (dark) {
            themeToggleButton.getStyleClass().add("toggle-on");
        }
    }

    /**
     * Устанавливает ссылку на ReportController для синхронизации обновлений отчетов
     */
    public static void setReportController(ReportController controller) {
        reportController = controller;
    }

    /**
     * Уведомляет ReportController об обновлении данных
     * (вызывается из других контроллеров при добавлении/обновлении/удалении операций)
     */
    public static void notifyReportUpdate() {
        if (reportController != null) {
            reportController.refreshReport();
        }
    }

    private void testDatabaseConnection() {
        try (Connection ignored = DatabaseConnection.getConnection()) {
            // Подключение проверяется один раз при старте экрана.
        } catch (Exception e) {
            AlertUtil.error("Подключение", "Ошибка подключения к БД: " + e.getMessage());
        }
    }

    // --- View switching helpers ---
    private boolean loadViewIntoContent(String fxml) {
        try {
            Node node = FXMLLoader.load(getClass().getResource(fxml));
            if (node instanceof Parent parent) {
                SmoothScrollUtil.installOnTree(parent);
            }
            ScrollPane scrollPane = createContentScrollPane(node);

            contentArea.getChildren().clear();
            contentArea.getChildren().add(scrollPane);
            return true;
        } catch (IOException e) {
            AlertUtil.error("Ошибка UI", "Не удалось загрузить экран: " + fxml + "\n" + e.getMessage());
            return false;
        }
    }

    private ScrollPane createContentScrollPane(Node node) {
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }

        ScrollPane scrollPane = new ScrollPane(node);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-scroll");
        SmoothScrollUtil.install(scrollPane);
        return scrollPane;
    }

    public void showDashboard() {
        showView("/com/example/currencyexchange/dashboard-view.fxml", dashboardButton);
    }

    public void showCurrencies() {
        showView("/com/example/currencyexchange/currencies-view.fxml", currenciesButton);
    }

    public void showExchangeRates() {
        showView("/com/example/currencyexchange/exchange-rates-view.fxml", exchangeRatesButton);
    }

    public void showCashDesks() {
        showView("/com/example/currencyexchange/cash-desks-view.fxml", cashDesksButton);
    }

    public void showExchangeOperations() {
        showView("/com/example/currencyexchange/exchange-operations-view.fxml", exchangeOperationsButton);
    }

    public void showIncassations() {
        showView("/com/example/currencyexchange/incassations-view.fxml", incassationsButton);
    }

    public void showReports() {
        showView("/com/example/currencyexchange/reports-view.fxml", reportsButton);
    }

    private void showView(String fxml, Button activeButton) {
        if (loadViewIntoContent(fxml)) {
            setActiveSidebarButton(activeButton);
        }
    }

    private void setActiveSidebarButton(Button activeButton) {
        Button[] buttons = {
                dashboardButton,
                currenciesButton,
                exchangeRatesButton,
                cashDesksButton,
                exchangeOperationsButton,
                incassationsButton,
                reportsButton
        };

        for (Button button : buttons) {
            button.getStyleClass().remove(ACTIVE_CLASS);
        }

        if (!activeButton.getStyleClass().contains(ACTIVE_CLASS)) {
            activeButton.getStyleClass().add(ACTIVE_CLASS);
        }
    }
}


