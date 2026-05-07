package com.example.currencyexchange.util;

import java.util.Locale;

public final class CurrencyCodeUtil {
    private CurrencyCodeUtil() {
    }

    public static boolean isKnownCurrencyCode(String code) {
        try {
            java.util.Currency.getInstance(normalize(code));
            return true;
        } catch (IllegalArgumentException | NullPointerException e) {
            return false;
        }
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
