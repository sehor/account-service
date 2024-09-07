package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.repository.AccountingPeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPeriodService {

    private final AccountingPeriodRepository accountingPeriodRepository;

    @Autowired
    public AccountPeriodService(AccountingPeriodRepository accountingPeriodRepository) {
        this.accountingPeriodRepository = accountingPeriodRepository;
    }

    @Transactional
    public void deleteAccountPeriodsByAccountSetId(String accountSetId) {
        accountingPeriodRepository.deleteByAccountSetId(accountSetId);
    }
}