package com.example.currencyexchange.model;

import java.time.LocalDateTime;

public class ExchangeOperation {
    private int id;
    private int cashDeskId;
    private String cashDeskName;
    private LocalDateTime operationDate;
    private String currencyFrom;
    private String currencyFromName;
    private String currencyTo;
    private String currencyToName;
    private double amountFrom;
    private double rate;
    private double amountTo;

    public ExchangeOperation() {
    }

    public ExchangeOperation(int id, int cashDeskId, LocalDateTime operationDate, String currencyFrom,
                             String currencyTo, double amountFrom, double rate, double amountTo) {
        this.id = id;
        this.cashDeskId = cashDeskId;
        this.operationDate = operationDate;
        this.currencyFrom = currencyFrom;
        this.currencyTo = currencyTo;
        this.amountFrom = amountFrom;
        this.rate = rate;
        this.amountTo = amountTo;
    }

    public ExchangeOperation(int id, int cashDeskId, String cashDeskName, LocalDateTime operationDate,
                             String currencyFrom, String currencyFromName, String currencyTo, String currencyToName,
                             double amountFrom, double rate, double amountTo) {
        this(id, cashDeskId, operationDate, currencyFrom, currencyTo, amountFrom, rate, amountTo);
        this.cashDeskName = cashDeskName;
        this.currencyFromName = currencyFromName;
        this.currencyToName = currencyToName;
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

    public String getCurrencyFrom() {
        return currencyFrom;
    }

    public void setCurrencyFrom(String currencyFrom) {
        this.currencyFrom = currencyFrom;
    }

    public String getCurrencyFromName() {
        return currencyFromName;
    }

    public void setCurrencyFromName(String currencyFromName) {
        this.currencyFromName = currencyFromName;
    }

    public String getCurrencyTo() {
        return currencyTo;
    }

    public void setCurrencyTo(String currencyTo) {
        this.currencyTo = currencyTo;
    }

    public String getCurrencyToName() {
        return currencyToName;
    }

    public void setCurrencyToName(String currencyToName) {
        this.currencyToName = currencyToName;
    }

    public LocalDateTime getOperationDate() {
        return operationDate;
    }

    public void setOperationDate(LocalDateTime operationDate) {
        this.operationDate = operationDate;
    }

    public double getAmountFrom() {
        return amountFrom;
    }

    public void setAmountFrom(double amountFrom) {
        this.amountFrom = amountFrom;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public double getAmountTo() {
        return amountTo;
    }

    public void setAmountTo(double amountTo) {
        this.amountTo = amountTo;
    }
}


