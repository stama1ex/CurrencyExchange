package com.example.currencyexchange.model;

import java.time.LocalDate;

public class ExchangeRate {
    private int id;
    private String currencyCode;
    private String currencyName;
    private LocalDate rateDate;
    private double buyRateMdl;
    private double sellRateMdl;

    public ExchangeRate() {
    }

    public ExchangeRate(int id, String currencyCode, LocalDate rateDate, double buyRateMdl, double sellRateMdl) {
        this.id = id;
        this.currencyCode = currencyCode;
        this.rateDate = rateDate;
        this.buyRateMdl = buyRateMdl;
        this.sellRateMdl = sellRateMdl;
    }

    public ExchangeRate(int id, String currencyCode, String currencyName, LocalDate rateDate, double buyRateMdl, double sellRateMdl) {
        this(id, currencyCode, rateDate, buyRateMdl, sellRateMdl);
        this.currencyName = currencyName;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public LocalDate getRateDate() {
        return rateDate;
    }

    public void setRateDate(LocalDate rateDate) {
        this.rateDate = rateDate;
    }

    public double getBuyRateMdl() {
        return buyRateMdl;
    }

    public void setBuyRateMdl(double buyRateMdl) {
        this.buyRateMdl = buyRateMdl;
    }

    public double getSellRateMdl() {
        return sellRateMdl;
    }

    public void setSellRateMdl(double sellRateMdl) {
        this.sellRateMdl = sellRateMdl;
    }
}


