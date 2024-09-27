package com.skyflytech.accountservice.core.accountingPeriod.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountAmountHolder {
    private BigDecimal totalDebit;  // 借方发生额
    private BigDecimal totalCredit; // 贷方发生额
    private BigDecimal balance;      // 余额
}