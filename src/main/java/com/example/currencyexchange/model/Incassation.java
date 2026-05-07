package com.example.currencyexchange.model;

import java.time.LocalDate;

public class Incassation {
    private int id;
    private int cashDeskId;
    private String cashDeskName;
    private LocalDate incassationDate;
    private String currencyCode;
    private String currencyName;
    private String operationType;
    private double amount;
    private String status;
    private String note;

    public Incassation() {
    }

    public Incassation(int id, int cashDeskId, LocalDate incassationDate, String currencyCode,
                       String operationType, double amount, String status) {
        this(id, cashDeskId, incassationDate, currencyCode, operationType, amount, status, "");
    }

    public Incassation(int id, int cashDeskId, LocalDate incassationDate, String currencyCode,
                       String operationType, double amount, String status, String note) {
        this.id = id;
        this.cashDeskId = cashDeskId;
        this.incassationDate = incassationDate;
        this.currencyCode = currencyCode;
        this.operationType = operationType;
        this.amount = amount;
        this.status = status;
        this.note = note;
    }

    public Incassation(int id, int cashDeskId, String cashDeskName, LocalDate incassationDate, String currencyCode,
                       String currencyName, String operationType, double amount, String status) {
        this(id, cashDeskId, cashDeskName, incassationDate, currencyCode, currencyName, operationType, amount, status, "");
    }

    public Incassation(int id, int cashDeskId, String cashDeskName, LocalDate incassationDate, String currencyCode,
                       String currencyName, String operationType, double amount, String status, String note) {
        this(id, cashDeskId, incassationDate, currencyCode, operationType, amount, status, note);
        this.cashDeskName = cashDeskName;
        this.currencyName = currencyName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCashDeskId() {
        return cashDeskId;
    }

    public void setCashDeskId(int cashDeskId) {
        this.cashDeskId = cashDeskId;
    }

    public String getCashDeskName() {
        return cashDeskName;
    }

    public void setCashDeskName(String cashDeskName) {
        this.cashDeskName = cashDeskName;
    }

    public LocalDate getIncassationDate() {
        return incassationDate;
    }

    public void setIncassationDate(LocalDate incassationDate) {
        this.incassationDate = incassationDate;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}


