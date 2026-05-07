package com.example.currencyexchange.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Parent;
import javafx.scene.control.DialogPane;

public final class ThemeManager {
    public static final String DARK_THEME_CLASS = "dark-theme";
    private static final BooleanProperty darkTheme = new SimpleBooleanProperty(true);

    private ThemeManager() {
    }

    public static BooleanProperty darkThemeProperty() {
        return darkTheme;
    }

    public static boolean isDarkTheme() {
        return darkTheme.get();
    }

    public static void install(Parent root) {
        applyAppTheme(isDarkTheme());
        applyThemeClass(root, isDarkTheme());
        darkTheme.addListener((obs, wasDark, isDark) -> {
            applyAppTheme(isDark);
            applyThemeClass(root, isDark);
        });
    }

    public static void toggleTheme() {
        darkTheme.set(!darkTheme.get());
    }

    public static void applyToDialog(DialogPane dialogPane) {
        String stylesheet = ThemeManager.class.getResource("/com/example/currencyexchange/styles.css").toExternalForm();
        if (!dialogPane.getStylesheets().contains(stylesheet)) {
            dialogPane.getStylesheets().add(stylesheet);
        }
        applyThemeClass(dialogPane, isDarkTheme());
        darkTheme.addListener((obs, wasDark, isDark) -> applyThemeClass(dialogPane, isDark));
    }

    private static void applyAppTheme(boolean dark) {
        Application.setUserAgentStylesheet(dark
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
    }

    private static void applyThemeClass(Parent parent, boolean dark) {
        if (dark) {
            if (!parent.getStyleClass().contains(DARK_THEME_CLASS)) {
                parent.getStyleClass().add(DARK_THEME_CLASS);
            }
        } else {
            parent.getStyleClass().remove(DARK_THEME_CLASS);
        }
    }
}
