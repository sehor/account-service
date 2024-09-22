package com.skyflytech.accountservice.report;

public enum DataType {
    DEBIT_TOTAL("借方发生额"),
    CREDIT_TOTAL("贷方发生额"),
    BALANCE("余额");

    private final String description;

    DataType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}