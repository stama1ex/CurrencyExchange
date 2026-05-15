package com.example.currencyexchange.model;

import java.time.LocalDateTime;

public abstract class Operation {
    private int id;
    private int cashDeskId;
    private String cashDeskName;
    private LocalDateTime operationDate;

    public Operation() {
    }

    public Operation(int id, int cashDeskId, LocalDateTime operationDate) {
        this.id = id;
        this.cashDeskId = cashDeskId;
        this.operationDate = operationDate;
    }

    public Operation(int id, int cashDeskId, String cashDeskName, LocalDateTime operationDate) {
        this(id, cashDeskId, operationDate);
        this.cashDeskName = cashDeskName;
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

    public LocalDateTime getOperationDate() {
        return operationDate;
    }

    public void setOperationDate(LocalDateTime operationDate) {
        this.operationDate = operationDate;
    }
}
