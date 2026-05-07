package com.example.currencyexchange.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ValidationService {
    private ValidationService() {
    }

    public static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static boolean isPositive(double value) {
        return value > 0;
    }

    public static boolean isNotNegative(double value) {
        return value >= 0;
    }

    public static boolean isValidDate(LocalDate date) {
        return date != null;
    }

    public static boolean isValidDateTime(LocalDateTime dateTime) {
        return dateTime != null;
    }

    public static boolean isLimitRangeValid(double min, double max) {
        return isNotNegative(min) && isNotNegative(max) && max > min;
    }
}

