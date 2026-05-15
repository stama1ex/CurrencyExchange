package com.example.currencyexchange.model;

import java.time.LocalDateTime;

public class ExchangeOperation extends Operation {
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
        super(id, cashDeskId, operationDate);
        this.currencyFrom = currencyFrom;
        this.currencyTo = currencyTo;
        this.amountFrom = amountFrom;
        this.rate = rate;
        this.amountTo = amountTo;
    }

    public ExchangeOperation(int id, int cashDeskId, String cashDeskName, LocalDateTime operationDate,
                             String currencyFrom, String currencyFromName, String currencyTo, String currencyToName,
                             double amountFrom, double rate, double amountTo) {
        super(id, cashDeskId, cashDeskName, operationDate);
        this.currencyFrom = currencyFrom;
        this.currencyFromName = currencyFromName;
        this.currencyTo = currencyTo;
        this.currencyToName = currencyToName;
        this.amountFrom = amountFrom;
        this.rate = rate;
        this.amountTo = amountTo;
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

    // Inherited id, cashDeskId, cashDeskName and operationDate from Operation

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


