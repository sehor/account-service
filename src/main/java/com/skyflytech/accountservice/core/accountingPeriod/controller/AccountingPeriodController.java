package com.skyflytech.accountservice.core.accountingPeriod.controller;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.accountingPeriod.service.AccountingPeriodService;
import com.skyflytech.accountservice.core.accountingPeriod.service.CloseAccountingPeriodService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounting-periods")
public class AccountingPeriodController {

    private final CloseAccountingPeriodService closeAccountingPeriodService;
    @Autowired
    public AccountingPeriodController(CloseAccountingPeriodService closeAccountingPeriodService, AccountingPeriodService accountingPeriodService) {
        this.closeAccountingPeriodService = closeAccountingPeriodService;
    }

    //检查需要结转的账户余额是否为都是零
    @GetMapping("/{periodId}/check-transfer-balances")
    public ResponseEntity<Boolean> checkTransferAccountBalancesIsZero(@PathVariable String periodId) {
        boolean isZero = closeAccountingPeriodService.checkTransferAccountBalancesIsZero(periodId);
        return ResponseEntity.ok(isZero);
    }

    //检查会计等式是否成立
    @GetMapping("/{periodId}/check-accounting-equation")
    public ResponseEntity<BigDecimal> checkAccountingEquation(@PathVariable String periodId) {
        BigDecimal difference = closeAccountingPeriodService.checkAccountingEquation(periodId);
        return ResponseEntity.ok(difference);
    }

    //检查凭证号是否连续
    @GetMapping("/{periodId}/check-voucher-continuity")
    public ResponseEntity<Boolean> checkVoucherWordIsContinuous(@PathVariable String periodId) {
        boolean isContinuous = closeAccountingPeriodService.checkVoucherWordIsContinuous(periodId);
        return ResponseEntity.ok(isContinuous);
    }

    //结账
    @PostMapping("/{accountingPeriodId}/close")
    public ResponseEntity<?> closeAccountingPeriod(@PathVariable String accountingPeriodId) {
        try {
            
            AccountingPeriod newPeriod = closeAccountingPeriodService.closeAccountingPeriod(accountingPeriodId);
            return ResponseEntity.ok(newPeriod);
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "结账失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }


    //自动生成结转凭证
    @PostMapping("/auto-transfer/{periodId}")
    public ResponseEntity<?> autoTransferPeriod(@PathVariable String periodId) {
        try {
            closeAccountingPeriodService.transferPeriod(periodId);
            return ResponseEntity.ok().body("会计期间自动结转成功");
        } catch (RuntimeException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "会计期间自动结转失败");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}