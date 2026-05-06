package com.example.currencyexchange.enums;

public enum CashDeskStatus {
    WORKING("Работает"),
    CLOSED("Закрыта");

    private final String dbValue;

    CashDeskStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static CashDeskStatus fromDbValue(String value) {
        for (CashDeskStatus status : values()) {
            if (status.dbValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Неизвестный статус кассы: " + value);
    }

    @Override
    public String toString() {
        return dbValue;
    }
}


