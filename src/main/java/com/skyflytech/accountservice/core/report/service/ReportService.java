package com.skyflytech.accountservice.core.report.service;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.report.model.AccountingFormula;
import com.skyflytech.accountservice.core.report.model.IncomeStatement;

import java.math.BigDecimal;

public interface ReportService {

    /**
     * 初始化默认利润表项目
     * @param incomeStatement 利润表
     */
    void initializeDefaultIncomeStatementItems(IncomeStatement incomeStatement);

    /**
     * 计算利润表
     * @param incomeStatement 利润表
     */
    void calculateIncomeStatement(IncomeStatement incomeStatement);

    /**
     * 计算会计公式
     * @param formula 会计公式
     * @param accountingPeriod 会计期间
     * @return 计算结果
     */
    BigDecimal calculateFormula(AccountingFormula formula, AccountingPeriod accountingPeriod);


}
