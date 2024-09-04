package com.skyflytech.accountservice.report;

public enum DataType {
    DEBIT_AMOUNT("借方发生额"),
    CREDIT_AMOUNT("贷方发生额"),
    BALANCE("余额");

    private final String description;

    DataType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}