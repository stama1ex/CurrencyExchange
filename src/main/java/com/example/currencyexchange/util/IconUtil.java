package com.example.currencyexchange.util;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Labeled;
import org.kordamp.ikonli.javafx.FontIcon;

public final class IconUtil {
    private IconUtil() {
    }

    public static FontIcon icon(String iconLiteral) {
        return icon(iconLiteral, 13);
    }

    public static FontIcon icon(String iconLiteral, int size) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(size);
        icon.getStyleClass().add("app-icon");
        return icon;
    }

    public static void setIcon(Labeled labeled, String iconLiteral) {
        setIcon(labeled, iconLiteral, 13);
    }

    public static void setIcon(Labeled labeled, String iconLiteral, int size) {
        labeled.setGraphic(icon(iconLiteral, size));
        labeled.setContentDisplay(ContentDisplay.LEFT);
        labeled.setGraphicTextGap(8);
    }

    public static void setIconOnly(Labeled labeled, String iconLiteral) {
        labeled.setText("");
        labeled.setGraphic(icon(iconLiteral));
        labeled.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }
}
