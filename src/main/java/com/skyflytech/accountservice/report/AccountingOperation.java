package com.skyflytech.accountservice.report;

public class AccountingOperation {
    private String accountId;
    private Operator operator;
    private DataType dataType;

    public AccountingOperation(String accountId, Operator operator, DataType dataType) {
        this.accountId = accountId;
        this.operator = operator;
        this.dataType = dataType;
    }

    public String getAccountId() {
        return accountId;
    }

    public Operator getOperator() {
        return operator;
    }

    public DataType getDataType() {
        return dataType;
    }

    @Override
    public String toString() {
        return operator.getSymbol() + " " + accountId + "(" + dataType.getDescription() + ")";
    }
}