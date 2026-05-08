package com.example.currencyexchange.util;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

public final class DeleteConfirmationUtil {
    private static final double POPUP_WIDTH = 260;
    private static final double POPUP_GAP = 8;
    private static Popup activePopup;

    private DeleteConfirmationUtil() {
    }

    public static void show(Node owner, String title, String message, Runnable onConfirm) {
        if (owner == null || onConfirm == null) {
            return;
        }
        if (activePopup != null && activePopup.isShowing()) {
            activePopup.hide();
        }

        Label icon = new Label();
        IconUtil.setIconOnly(icon, "fas-exclamation-triangle");
        icon.getStyleClass().add("delete-confirm-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("delete-confirm-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);

        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("delete-confirm-message");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(220);

        VBox copy = new VBox(3, titleLabel, messageLabel);
        copy.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox header = new HBox(9, icon, copy);
        header.setAlignment(Pos.TOP_LEFT);

        Button cancelButton = new Button("Отмена");
        cancelButton.getStyleClass().addAll("secondary-button", "delete-confirm-cancel");

        Button confirmButton = new Button("Удалить");
        IconUtil.setIcon(confirmButton, "fas-trash", 12);
        confirmButton.getStyleClass().addAll("danger-button", "delete-confirm-delete");

        HBox actions = new HBox(8, cancelButton, confirmButton);
        actions.getStyleClass().add("delete-confirm-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(12, header, actions);
        content.getStyleClass().add("delete-confirm-popover");
        content.setPadding(new Insets(12));
        content.setPrefWidth(POPUP_WIDTH);
        applyTheme(content, ThemeManager.isDarkTheme());

        VBox root = new VBox(0, content);
        root.getStyleClass().add("delete-confirm-popup-root");
        root.setAlignment(Pos.TOP_CENTER);
        installStyles(root);
        applyTheme(root, ThemeManager.isDarkTheme());

        Popup popup = new Popup();
        popup.getContent().add(root);
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        popup.setConsumeAutoHidingEvents(false);

        ChangeListener<Boolean> themeListener = (obs, wasDark, isDark) -> {
            applyTheme(content, isDark);
            applyTheme(root, isDark);
        };
        ThemeManager.darkThemeProperty().addListener(themeListener);
        popup.setOnHidden(event -> {
            ThemeManager.darkThemeProperty().removeListener(themeListener);
            if (activePopup == popup) {
                activePopup = null;
            }
        });

        cancelButton.setOnAction(event -> popup.hide());
        confirmButton.setOnAction(event -> {
            popup.hide();
            onConfirm.run();
        });

        activePopup = popup;
        showNearOwner(popup, root, owner);
    }

    private static void installStyles(Parent content) {
        String stylesheet = DeleteConfirmationUtil.class
                .getResource("/com/example/currencyexchange/styles.css")
                .toExternalForm();
        if (!content.getStylesheets().contains(stylesheet)) {
            content.getStylesheets().add(stylesheet);
        }
    }

    private static void applyTheme(Node node, boolean dark) {
        if (dark) {
            if (!node.getStyleClass().contains(ThemeManager.DARK_THEME_CLASS)) {
                node.getStyleClass().add(ThemeManager.DARK_THEME_CLASS);
            }
        } else {
            node.getStyleClass().remove(ThemeManager.DARK_THEME_CLASS);
        }
    }

    private static void showNearOwner(Popup popup, VBox root, Node owner) {
        Window window = owner.getScene() == null ? null : owner.getScene().getWindow();
        Bounds ownerBounds = owner.localToScreen(owner.getBoundsInLocal());
        if (window == null) {
            return;
        }
        if (ownerBounds == null) {
            popup.show(window);
            return;
        }

        double width = POPUP_WIDTH;
        double x = ownerBounds.getMinX() + (ownerBounds.getWidth() - width) / 2;
        double height = calculatePopupHeight(root, width);

        Rectangle2D ownerRect = new Rectangle2D(
                ownerBounds.getMinX(),
                ownerBounds.getMinY(),
                ownerBounds.getWidth(),
                ownerBounds.getHeight()
        );
        Screen screen = Screen.getScreensForRectangle(ownerRect).stream()
                .findFirst()
                .orElse(Screen.getPrimary());
        Rectangle2D visualBounds = screen.getVisualBounds();
        double leftLimit = Math.max(visualBounds.getMinX(), window.getX()) + POPUP_GAP;
        double rightLimit = Math.min(visualBounds.getMaxX(), window.getX() + window.getWidth()) - POPUP_GAP;
        double topLimit = Math.max(visualBounds.getMinY(), window.getY()) + POPUP_GAP;
        double bottomLimit = Math.min(visualBounds.getMaxY(), window.getY() + window.getHeight()) - POPUP_GAP;

        double spaceAbove = ownerBounds.getMinY() - topLimit - POPUP_GAP;
        double spaceBelow = bottomLimit - ownerBounds.getMaxY() - POPUP_GAP;
        boolean showAbove = spaceAbove >= height || spaceAbove >= spaceBelow;

        double y = showAbove
                ? ownerBounds.getMinY() - height - POPUP_GAP
                : ownerBounds.getMaxY() + POPUP_GAP;

        x = clamp(x, leftLimit, rightLimit - width);
        y = clamp(y, topLimit, bottomLimit - height);

        popup.show(window, x, y);
    }

    private static double calculatePopupHeight(Parent root, double width) {
        root.applyCss();
        root.autosize();
        return Math.max(root.prefHeight(width), root.minHeight(width));
    }

    private static double clamp(double value, double min, double max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
