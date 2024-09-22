package com.skyflytech.accountservice.report;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@NoArgsConstructor
@Document(collection = "accounting_formulas")
public class AccountingFormula {
    @Id
    private String id;
    @NonNull
    private String accountSetId;
    private String reportItemName;
    private List<AccountingOperation> operations = new ArrayList<>();

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(reportItemName + " = ");
        for (AccountingOperation operation : operations) {
            sb.append(operation.toString()).append(" ");
        }
        return sb.toString().trim();
    }

}