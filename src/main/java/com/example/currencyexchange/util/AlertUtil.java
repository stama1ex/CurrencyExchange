package com.example.currencyexchange.util;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

public final class AlertUtil {
    private AlertUtil() {
    }

    public static void info(String title, String message) {
        show(Type.INFO, title, message);
    }

    public static void error(String title, String message) {
        show(Type.ERROR, title, message);
    }

    public static void warning(String title, String message) {
        show(Type.WARNING, title, message);
    }

    private static void show(Type type, String title, String message) {
        Runnable action = () -> {
            Notifications notification = Notifications.create()
                    .title(title)
                    .text(message)
                    .position(Pos.TOP_RIGHT)
                    .hideAfter(Duration.seconds(type == Type.ERROR ? 7 : 4))
                    .threshold(4, Notifications.create()
                            .title("Уведомления")
                            .text("Есть несколько новых сообщений."));

            switch (type) {
                case INFO -> notification.showInformation();
                case WARNING -> notification.showWarning();
                case ERROR -> notification.showError();
            }
        };

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    private enum Type {
        INFO,
        WARNING,
        ERROR
    }
}

