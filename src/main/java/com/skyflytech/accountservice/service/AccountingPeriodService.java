package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountAmountHolder;
import com.skyflytech.accountservice.domain.AccountSet;
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
import java.util.Map.Entry;
import java.util.NoSuchElementException;
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
        initialPeriod.setClosed(false);
        AccountSet accountSet=mongoTemplate.findById(accountSetId, AccountSet.class);
        if(accountSet==null){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AccountSet not found");
        }
        for(Entry<String,BigDecimal> entry:accountSet.getInitialAccountBalance().entrySet()){
            AccountAmountHolder accountAmountHolder=new AccountAmountHolder();
            accountAmountHolder.setBalance(entry.getValue());
            accountAmountHolder.setTotalCredit(BigDecimal.ZERO);
            accountAmountHolder.setTotalDebit(BigDecimal.ZERO);
            initialPeriod.getAmountHolders().put(entry.getKey(), accountAmountHolder);
        }

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
               updatePeriodAmountHolders(period,relatedAccountIds,account.getBalanceDirection(),debitChange,creditChange);
        }
        accountingPeriodRepository.saveAll(accountingPeriods);
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
    public void updatePeriodAmountHolders(AccountingPeriod period, List<String> relatedAccountIds,
                                           AccountingDirection direction, BigDecimal debitChange, BigDecimal creditChange) {
        Map<String,AccountAmountHolder> amountHolders=period.getAmountHolders();
        for (String accountId : relatedAccountIds) {
            AccountAmountHolder accountAmountHolder = amountHolders.get(accountId);
            if(accountAmountHolder==null){
                throw new NoSuchElementException("can find account id:"+accountId+" when update period amount holder");
            }
            accountAmountHolder.setTotalCredit(accountAmountHolder.getTotalCredit().add(creditChange));
            accountAmountHolder.setTotalDebit(accountAmountHolder.getTotalDebit().add(debitChange));
            if(direction==AccountingDirection.DEBIT){
                accountAmountHolder.setBalance(accountAmountHolder.getBalance().add(debitChange).subtract(creditChange));
            }else{
                accountAmountHolder.setBalance(accountAmountHolder.getBalance().add(creditChange).subtract(debitChange));
            }
        }
    }

    private String generatePeriodName(YearMonth yearMonth) {
        return yearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
    }
}