package com.example.currencyexchange.enums;

public enum IncassationStatus {
    CREATED("Создана"),
    COMPLETED("Выполнена"),
    CANCELED("Отменена");

    private final String dbValue;

    IncassationStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static IncassationStatus fromDbValue(String value) {
        for (IncassationStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус инкассации: " + value);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}


