package com.example.currencyexchange.model;

public class CashDesk {
    private int id;
    private String name;
    private String address;
    private double minLimitMdl;
    private double maxLimitMdl;
    private String status;

    public CashDesk() {
    }

    public CashDesk(int id, String name, String address, double minLimitMdl, double maxLimitMdl, String status) {
        this.id = id;
        this.name = name;
        this.address = address;
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

