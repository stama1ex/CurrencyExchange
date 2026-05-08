package com.example.currencyexchange.model;

import java.util.ArrayList;
import java.util.List;

public class CashDesk {
    private int id;
    private String name;
    private String address;
    private String phone;
    private String status;
    private List<Balance> balances = new ArrayList<>();

    public CashDesk() {
    }

    public CashDesk(int id, String name, String address, String status) {
        this(id, name, address, "", status);
    }

    public CashDesk(int id, String name, String address, String phone, String status) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.phone = phone;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<Balance> getBalances() {
        return balances;
    }

    public void setBalances(List<Balance> balances) {
        this.balances = balances == null ? new ArrayList<>() : new ArrayList<>(balances);
    }

    public double getBalance(String currencyCode) {
        if (currencyCode == null) {
            return 0;
        }
        return balances.stream()
                .filter(balance -> currencyCode.equalsIgnoreCase(balance.currencyCode()))
                .mapToDouble(Balance::amount)
                .findFirst()
                .orElse(0);
    }

    public double getMinLimit(String currencyCode) {
        if (currencyCode == null) {
            return 0;
        }
        return balances.stream()
                .filter(balance -> currencyCode.equalsIgnoreCase(balance.currencyCode()))
                .mapToDouble(Balance::minLimit)
                .findFirst()
                .orElse(0);
    }

    public double getMaxLimit(String currencyCode) {
        if (currencyCode == null) {
            return 0;
        }
        return balances.stream()
                .filter(balance -> currencyCode.equalsIgnoreCase(balance.currencyCode()))
                .mapToDouble(Balance::maxLimit)
                .findFirst()
                .orElse(0);
    }

    @Override
    public String toString() {
        return name == null || name.isBlank() ? String.valueOf(id) : name;
    }

    public record Balance(String currencyCode, String currencyName, double amount, double minLimit, double maxLimit) {
    }
}
