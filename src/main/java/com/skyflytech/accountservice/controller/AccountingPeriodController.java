package com.skyflytech.accountservice.controller;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.service.AccountingPeriodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting-periods")
public class AccountingPeriodController {


    private final AccountingPeriodService accountingPeriodService;
    @Autowired
    public AccountingPeriodController(AccountingPeriodService accountingPeriodService) {
        this.accountingPeriodService = accountingPeriodService;
    }

    @PostMapping("/{accountingPeriodId}/close")
    public ResponseEntity<?> closeAccountingPeriod(@PathVariable String accountingPeriodId) {
        try {
            AccountingPeriod newPeriod = accountingPeriodService.closeAccountingPeriod(accountingPeriodId);
            return ResponseEntity.ok(newPeriod);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "结账失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}