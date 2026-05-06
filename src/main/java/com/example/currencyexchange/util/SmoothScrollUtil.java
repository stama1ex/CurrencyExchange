package com.example.currencyexchange.util;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TreeView;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

public final class SmoothScrollUtil {
    private static final String INSTALLED_KEY = "smoothScrollInstalled";
    private static final String ANIMATION_KEY = "smoothScrollAnimation";
    private static final String TARGET_VALUE_KEY = "smoothScrollTargetValue";
    private static final double SCROLL_MULTIPLIER = 1.75;
    private static final Duration ANIMATION_DURATION = Duration.millis(180);

    private SmoothScrollUtil() {
    }

    public static void installOnTree(Parent parent) {
        if (parent == null) {
            return;
        }

        if (parent instanceof ScrollPane scrollPane) {
            install(scrollPane);
            if (scrollPane.getContent() instanceof Parent contentParent) {
                installOnTree(contentParent);
            }
        }

        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof ScrollPane scrollPane) {
                install(scrollPane);
            }
            if (child instanceof Parent childParent) {
                installOnTree(childParent);
            }
        }
    }

    public static void install(ScrollPane scrollPane) {
        if (scrollPane == null || Boolean.TRUE.equals(scrollPane.getProperties().get(INSTALLED_KEY))) {
            return;
        }

        scrollPane.getProperties().put(INSTALLED_KEY, true);
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> handleScroll(scrollPane, event));
    }

    private static void handleScroll(ScrollPane scrollPane, ScrollEvent event) {
        if (event.getDeltaY() == 0
                || isFromNestedScrollPane(scrollPane, event.getTarget())
                || isFromControlThatHandlesScroll(scrollPane, event.getTarget())) {
            return;
        }

        Node content = scrollPane.getContent();
        if (content == null) {
            return;
        }

        Bounds viewportBounds = scrollPane.getViewportBounds();
        Bounds contentBounds = content.getLayoutBounds();
        double scrollableHeight = contentBounds.getHeight() - viewportBounds.getHeight();
        if (scrollableHeight <= 0) {
            return;
        }

        double currentValue = scrollPane.getVvalue();
        double baseTarget = getBaseTarget(scrollPane, currentValue);
        double targetValue = clamp(baseTarget - event.getDeltaY() * SCROLL_MULTIPLIER / scrollableHeight);
        if (Math.abs(targetValue - currentValue) < 0.0001) {
            return;
        }

        animateTo(scrollPane, targetValue);
        event.consume();
    }

    private static double getBaseTarget(ScrollPane scrollPane, double currentValue) {
        Object targetValue = scrollPane.getProperties().get(TARGET_VALUE_KEY);
        if (targetValue instanceof Double value) {
            return value;
        }
        return currentValue;
    }

    private static void animateTo(ScrollPane scrollPane, double targetValue) {
        Object previousAnimation = scrollPane.getProperties().get(ANIMATION_KEY);
        if (previousAnimation instanceof Timeline timeline) {
            timeline.stop();
        }

        scrollPane.getProperties().put(TARGET_VALUE_KEY, targetValue);

        Timeline timeline = new Timeline(
                new KeyFrame(
                        ANIMATION_DURATION,
                        new KeyValue(scrollPane.vvalueProperty(), targetValue, Interpolator.EASE_BOTH)
                )
        );
        timeline.setOnFinished(event -> {
            scrollPane.getProperties().remove(ANIMATION_KEY);
            scrollPane.getProperties().remove(TARGET_VALUE_KEY);
        });
        scrollPane.getProperties().put(ANIMATION_KEY, timeline);
        timeline.play();
    }

    private static boolean isFromNestedScrollPane(ScrollPane scrollPane, EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }

        Node current = node;
        while (current != null && current != scrollPane) {
            if (current instanceof ScrollPane) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean isFromControlThatHandlesScroll(ScrollPane scrollPane, EventTarget target) {
        if (!(target instanceof Node node)) {
            return false;
        }

        Node current = node;
        while (current != null && current != scrollPane) {
            if (current instanceof TableView<?>
                    || current instanceof ListView<?>
                    || current instanceof TreeView<?>
                    || current instanceof ComboBoxBase<?>
                    || current instanceof TextInputControl) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
