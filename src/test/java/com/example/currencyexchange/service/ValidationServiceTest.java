package com.example.currencyexchange.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationServiceTest {
    @Test
    void validatesBlankText() {
        assertFalse(ValidationService.isNotBlank(null));
        assertFalse(ValidationService.isNotBlank("   "));
        assertTrue(ValidationService.isNotBlank("MDL"));
    }

    @Test
    void validatesNumericBoundaries() {
        assertTrue(ValidationService.isPositive(0.01));
        assertFalse(ValidationService.isPositive(0));
        assertTrue(ValidationService.isNotNegative(0));
        assertFalse(ValidationService.isNotNegative(-0.01));
    }

    @Test
    void validatesDatesAndLimits() {
        assertTrue(ValidationService.isValidDate(LocalDate.of(2026, 5, 6)));
        assertFalse(ValidationService.isValidDate(null));
        assertTrue(ValidationService.isValidDateTime(LocalDateTime.of(2026, 5, 6, 14, 30)));
        assertFalse(ValidationService.isValidDateTime(null));
        assertTrue(ValidationService.isLimitRangeValid(10, 20));
        assertFalse(ValidationService.isLimitRangeValid(20, 20));
    }
}
