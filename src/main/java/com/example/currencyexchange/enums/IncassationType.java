package com.example.currencyexchange.enums;

public enum IncassationType {
    INCASSATION("Инкассация"),
    TOP_UP("Пополнение");

    private final String dbValue;

    IncassationType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static IncassationType fromDbValue(String value) {
        for (IncassationType type : values()) {
            if (type.dbValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип инкассации: " + value);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}


