package com.skyflytech.accountservice.report;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReportItem {
    private String name;
    private String chineseName;
    private BigDecimal amount;
    private AccountingFormula formula;
    private int level;

    public ReportItem(String name, String chineseName, int level) {
        this.name = name;
        this.chineseName = chineseName;
        this.amount = BigDecimal.ZERO;
        this.level = level;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("  ".repeat(level - 1))
          .append(chineseName)
          .append(" (").append(name).append(") = ")
          .append(amount);
        return sb.toString();
    }
}