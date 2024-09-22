package com.skyflytech.accountservice.report;

import lombok.Data;

@Data
public class AccountingOperation {
    private String accountId;
    private Operator operator;
    private DataType dataType;

    public AccountingOperation(String accountId, Operator operator, DataType dataType) {
        this.accountId = accountId;
        this.operator = operator;
        this.dataType = dataType;
    }
}