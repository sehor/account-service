package com.skyflytech.accountservice.core.report.controller;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.report.model.IncomeStatement;
import com.skyflytech.accountservice.core.report.service.ReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    @Autowired
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

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
        reportService.initializeDefaultIncomeStatementItems(incomeStatement);

        // 这里应该添加从数据库或其他数据源获取实际数据的逻辑
        // 为了演示，我们只是设置一些示例数据
        incomeStatement.getOperatingRevenue().setAmount(new java.math.BigDecimal("100000"));
        incomeStatement.getOperatingCost().setAmount(new java.math.BigDecimal("60000"));
        incomeStatement.getTaxesAndSurcharges().setAmount(new java.math.BigDecimal("5000"));
        // ... 设置其他项目的金额 ...

        // 计算利润表
        reportService.calculateIncomeStatement(incomeStatement);

        return ResponseEntity.ok(incomeStatement);
    }
}