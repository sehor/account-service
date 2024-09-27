package com.skyflytech.accountservice.core.report.controller;

import com.skyflytech.accountservice.core.report.model.IncomeStatement;
import com.skyflytech.accountservice.core.report.service.imp.ReportServiceImp;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportServiceImp reportServiceImp;

    @GetMapping("/income-statement")
    public ResponseEntity<IncomeStatement> getIncomeStatement(
            @RequestParam String accountSetId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        
        // 创建 AccountingPeriod 对象
        AccountingPeriod begin = new AccountingPeriod();
        begin.setStartDate(startDate);
        AccountingPeriod end = new AccountingPeriod();
        end.setEndDate(endDate);

        // 创建 IncomeStatement 实例
        IncomeStatement incomeStatement = new IncomeStatement(accountSetId, begin, end);

        // 初始化默认项目
        reportServiceImp.initializeDefaultIncomeStatementItems(incomeStatement);

        // 这里应该添加从数据库或其他数据源获取实际数据的逻辑
        // 为了演示，我们只是设置一些示例数据
        incomeStatement.getOperatingRevenue().setAmount(new java.math.BigDecimal("100000"));
        incomeStatement.getOperatingCost().setAmount(new java.math.BigDecimal("60000"));
        incomeStatement.getTaxesAndSurcharges().setAmount(new java.math.BigDecimal("5000"));
        // ... 设置其他项目的金额 ...

        // 计算利润表
        reportServiceImp.calculateIncomeStatement(incomeStatement);

        return ResponseEntity.ok(incomeStatement);
    }
}