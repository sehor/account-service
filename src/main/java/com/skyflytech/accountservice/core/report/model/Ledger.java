package com.skyflytech.accountservice.core.report.model;

import com.skyflytech.accountservice.core.account.model.Account;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * @Author pzr
 * @date:2024-08-24-4:44
 * @Description:
 **/
@Data
@NoArgsConstructor
public class Ledger {

    private Account account;

    private LocalDate start;
    private LocalDate end;

    private List<Transaction> transactions;

    private BigDecimal previousTotalDebit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private BigDecimal previousTotalCredit = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // Constructor with default values for start and end dates

}
