package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.domain.account.AccountingDirection;
import com.skyflytech.accountservice.repository.AccountingPeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccountingPeriodService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final MongoTemplate mongoTemplate;
    private final AccountService accountService;

    @Autowired
    public AccountingPeriodService(AccountingPeriodRepository accountingPeriodRepository,
            MongoTemplate mongoTemplate,
            AccountService accountService) {
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.mongoTemplate = mongoTemplate;
        this.accountService = accountService;
    }

    public AccountingPeriod createInitialPeriod(String accountSetId, YearMonth startMonth) {
        AccountingPeriod initialPeriod = new AccountingPeriod();
        initialPeriod.setAccountSetId(accountSetId);
        initialPeriod.setName(generatePeriodName(startMonth));
        initialPeriod.setStartDate(startMonth.atDay(1));
        initialPeriod.setEndDate(startMonth.atEndOfMonth());
        initialPeriod.setOpeningBalances(Map.of()); // 初始余额为空
        initialPeriod.setClosingBalances(Map.of()); // 初始余额为空
        initialPeriod.setClosed(false);

        return accountingPeriodRepository.save(initialPeriod);
    }

    @Transactional

    public void updateAccountingPeriodsWhenTransactionAmountChange(Transaction transaction, BigDecimal debitChange,
            BigDecimal creditChange) {
        Account account = mongoTemplate.findById(transaction.getAccountId(), Account.class);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        List<AccountingPeriod> accountingPeriods = findRelevantAccountingPeriods(account.getAccountSetId(),
                transaction.getModifiedDate());
        List<String> relatedAccountIds = getRelatedAccountIds(account.getId());

        for (AccountingPeriod period : accountingPeriods) {

            // 如果transaction的modifiedDate在accountingPeriod的startDate之后，则不update
            // openingBalances
            if (transaction.getModifiedDate().isAfter(period.getStartDate())) {
                updateAccountingPeriodBalances(period.getOpeningBalances(), relatedAccountIds,
                        account.getBalanceDirection(), debitChange, creditChange);
            }

            // update accountingPeriod's closingBalances
            updateAccountingPeriodBalances(period.getClosingBalances(), relatedAccountIds,
                    account.getBalanceDirection(), debitChange, creditChange);

            // save accountingperiods' openingBalances and closingBalances
            mongoTemplate.save(period);
        }

    }

    private List<AccountingPeriod> findRelevantAccountingPeriods(String accountSetId, LocalDate modifiedDate) {
        Query query = new Query(Criteria.where("accountSetId").is(accountSetId)
                .and("endDate").gte(modifiedDate));
        return mongoTemplate.find(query, AccountingPeriod.class);
    }

    private List<String> getRelatedAccountIds(String accountId) {
        return accountService.findAccountAndAllAncestors(accountId).stream()
                .map(Account::getId)
                .collect(Collectors.toList());
    }

    // update balances of related accounts in accountingPeriod
    private void updateAccountingPeriodBalances(Map<String, BigDecimal> balances, List<String> relatedAccountIds,
            AccountingDirection direction, BigDecimal debitChange, BigDecimal creditChange) {
        for (String accountId : relatedAccountIds) {
            BigDecimal currentBalance = balances.getOrDefault(accountId, BigDecimal.ZERO);
            BigDecimal newBalance = (direction == AccountingDirection.DEBIT)
                    ? currentBalance.add(debitChange).subtract(creditChange)
                    : currentBalance.add(creditChange).subtract(debitChange);
            balances.put(accountId, newBalance);
        }
    }

    private String generatePeriodName(YearMonth yearMonth) {
        return yearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
    }
}