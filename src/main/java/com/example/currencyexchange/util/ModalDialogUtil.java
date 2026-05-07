package com.example.currencyexchange.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Dialog;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class ModalDialogUtil {
    private static final double BLUR_RADIUS = 7.0;
    private static final double DIM_BRIGHTNESS = -0.12;

    private ModalDialogUtil() {
    }

    public static void configureFormDialog(Dialog<?> dialog, String title, String iconLiteral) {
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        Node titleIcon = IconUtil.icon(iconLiteral, 18);
        titleIcon.getStyleClass().add("dialog-title-icon");
        dialog.setGraphic(titleIcon);
        dialog.getDialogPane().getStyleClass().add("form-dialog");
        ThemeManager.applyToDialog(dialog.getDialogPane());
    }

    public static <T> Optional<T> showAndWait(Dialog<T> dialog) {
        Window owner = ensureOwner(dialog);
        Runnable restoreBackdrop = applyBackdrop(owner);

        try {
            return dialog.showAndWait();
        } finally {
            restoreBackdrop.run();
        }
    }

    private static Window ensureOwner(Dialog<?> dialog) {
        Window owner = dialog.getOwner();
        if (owner == null) {
            owner = findOwnerWindow();
            if (owner != null) {
                dialog.initOwner(owner);
            }
        }
        return owner;
    }

    private static Runnable applyBackdrop(Window owner) {
        Parent root = getRoot(owner);
        if (root == null) {
            return () -> {
            };
        }

        if (root instanceof Pane pane) {
            return applyPaneBackdrop(pane);
        }

        Effect backdropEffect = createBackdropEffect();
        Effect previousEffect = root.getEffect();
        root.setEffect(backdropEffect);
        return () -> {
            if (root.getEffect() == backdropEffect) {
                root.setEffect(previousEffect);
            }
        };
    }

    private static Runnable applyPaneBackdrop(Pane pane) {
        Region backdrop = new Region();
        backdrop.getStyleClass().add("modal-backdrop");
        backdrop.setMinSize(0, 0);
        backdrop.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        backdrop.prefWidthProperty().bind(pane.widthProperty());
        backdrop.prefHeightProperty().bind(pane.heightProperty());

        Map<Node, Effect> previousEffects = new IdentityHashMap<>();
        Map<Node, Effect> appliedEffects = new IdentityHashMap<>();
        for (Node child : new ArrayList<>(pane.getChildren())) {
            Effect backdropEffect = createBackdropEffect();
            previousEffects.put(child, child.getEffect());
            appliedEffects.put(child, backdropEffect);
            child.setEffect(backdropEffect);
        }

        pane.getChildren().add(backdrop);
        return () -> {
            backdrop.prefWidthProperty().unbind();
            backdrop.prefHeightProperty().unbind();
            pane.getChildren().remove(backdrop);
            for (Map.Entry<Node, Effect> entry : appliedEffects.entrySet()) {
                Node node = entry.getKey();
                if (node.getEffect() == entry.getValue()) {
                    node.setEffect(previousEffects.get(node));
                }
            }
        };
    }

    private static Parent getRoot(Window owner) {
        if (owner == null || owner.getScene() == null) {
            return null;
        }
        return owner.getScene().getRoot();
    }

    private static Effect createBackdropEffect() {
        GaussianBlur blur = new GaussianBlur(BLUR_RADIUS);
        ColorAdjust dim = new ColorAdjust();
        dim.setBrightness(DIM_BRIGHTNESS);
        dim.setSaturation(-0.08);
        dim.setInput(blur);
        return dim;
    }

    private static Window findOwnerWindow() {
        Window fallback = null;
        for (Window window : Window.getWindows()) {
            if (!window.isShowing() || window.getScene() == null) {
                continue;
            }
            if (window.isFocused()) {
                return window;
            }
            if (fallback == null) {
                fallback = window;
            }
        }
        return fallback;
    }
}
