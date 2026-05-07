package com.example.currencyexchange.util;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

public final class AlertUtil {
    private static final int MAX_TOASTS = 4;
    private static VBox toastHost;

    private AlertUtil() {
    }

    public static void setToastHost(VBox host) {
        toastHost = host;
        if (toastHost != null) {
            toastHost.setPickOnBounds(false);
        }
    }

    public static void info(String title, String message) {
        show(Type.INFO, title, message);
    }

    public static void success(String title, String message) {
        show(Type.SUCCESS, title, message);
    }

    public static void error(String title, String message) {
        show(Type.ERROR, title, message);
    }

    public static void warning(String title, String message) {
        show(Type.WARNING, title, message);
    }

    private static void show(Type type, String title, String message) {
        Runnable action = () -> {
            if (toastHost != null) {
                showInAppToast(type, title, message);
            } else {
                showFallbackNotification(type, title, message);
            }
        };

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private static void showInAppToast(Type type, String title, String message) {
        while (toastHost.getChildren().size() >= MAX_TOASTS) {
            toastHost.getChildren().remove(toastHost.getChildren().size() - 1);
        }

        Label icon = new Label();
        IconUtil.setIconOnly(icon, type.iconLiteral);
        icon.getStyleClass().addAll("app-toast-icon", type.iconStyleClass);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("app-toast-title");
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("app-toast-message");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(320);

        VBox copy = new VBox(3, titleLabel, messageLabel);
        copy.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(copy, Priority.ALWAYS);

        Button closeButton = new Button();
        IconUtil.setIconOnly(closeButton, "fas-times");
        closeButton.getStyleClass().add("app-toast-close");

        HBox toast = new HBox(10, icon, copy, closeButton);
        toast.getStyleClass().addAll("app-toast", type.toastStyleClass);
        toast.setAlignment(Pos.TOP_LEFT);
        toast.setOpacity(0);
        toast.setTranslateY(-10);

        toastHost.getChildren().add(0, toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(160), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(160), toast);
        slideIn.setFromY(-10);
        slideIn.setToY(0);

        PauseTransition visible = new PauseTransition(Duration.seconds(type.visibleSeconds));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition lifecycle = new SequentialTransition(
                new ParallelTransition(fadeIn, slideIn),
                visible,
                fadeOut
        );
        lifecycle.setOnFinished(event -> toastHost.getChildren().remove(toast));
        closeButton.setOnAction(event -> {
            lifecycle.stop();
            removeToast(toast);
        });
        lifecycle.play();
    }

    private static void removeToast(Node toast) {
        if (toastHost == null || !toastHost.getChildren().contains(toast)) {
            return;
        }

        FadeTransition fadeOut = new FadeTransition(Duration.millis(120), toast);
        fadeOut.setFromValue(toast.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(event -> toastHost.getChildren().remove(toast));
        fadeOut.play();
    }

    private static void showFallbackNotification(Type type, String title, String message) {
        Notifications thresholdNotification = Notifications.create()
                .title("Уведомления")
                .text("Есть несколько новых сообщений.");
        Notifications notification = Notifications.create()
                .title(title)
                .text(message)
                .position(Pos.TOP_RIGHT)
                .hideAfter(Duration.seconds(type.visibleSeconds))
                .threshold(MAX_TOASTS, thresholdNotification);
        if (ThemeManager.isDarkTheme()) {
            notification.darkStyle();
            thresholdNotification.darkStyle();
        }
        Window owner = findOwnerWindow();
        if (owner != null) {
            notification.owner(owner);
        }

        switch (type) {
            case INFO -> notification.showInformation();
            case SUCCESS -> notification.showInformation();
            case WARNING -> notification.showWarning();
            case ERROR -> notification.showError();
        }
    }

    private static Window findOwnerWindow() {
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window.isFocused()) {
                return window;
            }
        }
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                return window;
            }
        }
        return null;
    }

    private enum Type {
        INFO("fas-info", "app-toast-info", "app-toast-icon-info", 4),
        SUCCESS("fas-check-circle", "app-toast-success", "app-toast-icon-success", 4),
        WARNING("fas-exclamation-triangle", "app-toast-warning", "app-toast-icon-warning", 5),
        ERROR("fas-times-circle", "app-toast-error", "app-toast-icon-error", 7);

        private final String iconLiteral;
        private final String toastStyleClass;
        private final String iconStyleClass;
        private final int visibleSeconds;

        Type(String iconLiteral, String toastStyleClass, String iconStyleClass, int visibleSeconds) {
            this.iconLiteral = iconLiteral;
            this.toastStyleClass = toastStyleClass;
            this.iconStyleClass = iconStyleClass;
            this.visibleSeconds = visibleSeconds;
        }
    }
}
