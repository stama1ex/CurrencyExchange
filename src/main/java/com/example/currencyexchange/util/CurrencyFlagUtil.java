package com.example.currencyexchange.util;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CurrencyFlagUtil {
    private static final double FLAG_WIDTH = 42;
    private static final double FLAG_HEIGHT = 28;
    private static final Map<String, String> CURRENCY_COUNTRY_OVERRIDES = Map.ofEntries(
            Map.entry("EUR", "EU"),
            Map.entry("USD", "US"),
            Map.entry("GBP", "GB"),
            Map.entry("CHF", "CH"),
            Map.entry("JPY", "JP"),
            Map.entry("CNY", "CN"),
            Map.entry("RMB", "CN"),
            Map.entry("RON", "RO"),
            Map.entry("MDL", "MD"),
            Map.entry("UAH", "UA"),
            Map.entry("PLN", "PL"),
            Map.entry("CZK", "CZ"),
            Map.entry("HUF", "HU"),
            Map.entry("DKK", "DK"),
            Map.entry("SEK", "SE"),
            Map.entry("NOK", "NO"),
            Map.entry("TRY", "TR"),
            Map.entry("GEL", "GE"),
            Map.entry("AED", "AE")
    );
    private static final Map<String, Image> FLAG_CACHE = new ConcurrentHashMap<>();

    private CurrencyFlagUtil() {
    }

    public static Node createFlag(String currencyCode) {
        String normalizedCurrency = CurrencyCodeUtil.normalize(currencyCode);
        if (!CurrencyCodeUtil.isKnownCurrencyCode(normalizedCurrency)) {
            return createUnknownCurrencyIcon();
        }

        String countryCode = countryCodeForCurrency(normalizedCurrency);

        StackPane flag = new StackPane();
        flag.getStyleClass().add("currency-flag");
        flag.setMinSize(FLAG_WIDTH, FLAG_HEIGHT);
        flag.setPrefSize(FLAG_WIDTH, FLAG_HEIGHT);
        flag.setMaxSize(FLAG_WIDTH, FLAG_HEIGHT);

        Label fallback = new Label(fallbackText(normalizedCurrency, countryCode));
        fallback.getStyleClass().add("currency-flag-fallback");
        flag.getChildren().add(fallback);

        if (countryCode == null) {
            return flag;
        }

        Image image = FLAG_CACHE.computeIfAbsent(countryCode, CurrencyFlagUtil::loadFlagImage);
        ImageView imageView = new ImageView(image);
        imageView.getStyleClass().add("currency-flag-image");
        imageView.setFitWidth(FLAG_WIDTH);
        imageView.setFitHeight(FLAG_HEIGHT);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        Rectangle clip = new Rectangle(FLAG_WIDTH, FLAG_HEIGHT);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        imageView.setClip(clip);

        flag.getChildren().add(0, imageView);

        Runnable updateVisibility = () -> {
            boolean loaded = image.getProgress() >= 1 && !image.isError();
            imageView.setVisible(loaded);
            fallback.setVisible(!loaded);
        };
        image.progressProperty().addListener((obs, oldValue, newValue) -> updateVisibility.run());
        image.errorProperty().addListener((obs, oldValue, newValue) -> updateVisibility.run());
        updateVisibility.run();

        return flag;
    }

    private static Node createUnknownCurrencyIcon() {
        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().addAll("currency-flag", "currency-flag-unknown");
        iconBox.setMinSize(FLAG_WIDTH, FLAG_HEIGHT);
        iconBox.setPrefSize(FLAG_WIDTH, FLAG_HEIGHT);
        iconBox.setMaxSize(FLAG_WIDTH, FLAG_HEIGHT);
        iconBox.getChildren().add(IconUtil.icon("fas-question", 13));
        return iconBox;
    }

    private static Image loadFlagImage(String countryCode) {
        String url = "https://flagcdn.com/w80/" + countryCode.toLowerCase(Locale.ROOT) + ".png";
        return new Image(url, FLAG_WIDTH * 2, FLAG_HEIGHT * 2, false, true, true);
    }

    private static String countryCodeForCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.length() != 3 || currencyCode.startsWith("X")) {
            return null;
        }
        return CURRENCY_COUNTRY_OVERRIDES.getOrDefault(currencyCode, currencyCode.substring(0, 2));
    }

    private static String fallbackText(String currencyCode, String countryCode) {
        if (countryCode != null) {
            return countryCode;
        }
        if (currencyCode == null || currencyCode.length() < 2) {
            return "--";
        }
        return currencyCode.substring(0, 2);
    }

}
