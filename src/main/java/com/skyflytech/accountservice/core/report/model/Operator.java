package com.skyflytech.accountservice.core.report.model;

public enum Operator {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}