package com.example.currencyexchange.model;

public class CashDesk {
    private int id;
    private String name;
    private String address;
    private String phone;
    private double balanceMdl;
    private double balanceRon;
    private double balanceEur;
    private double balanceUsd;
    private double minLimitMdl;
    private double maxLimitMdl;
    private String status;

    public CashDesk() {
    }

    public CashDesk(int id, String name, String address, double minLimitMdl, double maxLimitMdl, String status) {
        this(id, name, address, "", 0, 0, 0, 0, minLimitMdl, maxLimitMdl, status);
    }

    public CashDesk(int id, String name, String address, String phone,
                    double balanceMdl, double balanceRon, double balanceEur, double balanceUsd,
                    double minLimitMdl, double maxLimitMdl, String status) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.balanceMdl = balanceMdl;
        this.balanceRon = balanceRon;
        this.balanceEur = balanceEur;
        this.balanceUsd = balanceUsd;
        this.minLimitMdl = minLimitMdl;
        this.maxLimitMdl = maxLimitMdl;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public double getBalanceMdl() {
        return balanceMdl;
    }

    public void setBalanceMdl(double balanceMdl) {
        this.balanceMdl = balanceMdl;
    }

    public double getBalanceRon() {
        return balanceRon;
    }

    public void setBalanceRon(double balanceRon) {
        this.balanceRon = balanceRon;
    }

    public double getBalanceEur() {
        return balanceEur;
    }

    public void setBalanceEur(double balanceEur) {
        this.balanceEur = balanceEur;
    }

    public double getBalanceUsd() {
        return balanceUsd;
    }

    public void setBalanceUsd(double balanceUsd) {
        this.balanceUsd = balanceUsd;
    }

    public double getMinLimitMdl() {
        return minLimitMdl;
    }

    public void setMinLimitMdl(double minLimitMdl) {
        this.minLimitMdl = minLimitMdl;
    }

    public double getMaxLimitMdl() {
        return maxLimitMdl;
    }

    public void setMaxLimitMdl(double maxLimitMdl) {
        this.maxLimitMdl = maxLimitMdl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return name == null || name.isBlank() ? String.valueOf(id) : name;
    }
}

