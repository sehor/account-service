package com.skyflytech.accountservice.core.accountingPeriod.service.imp;

import com.skyflytech.accountservice.core.account.model.Account;
import com.skyflytech.accountservice.core.account.model.AccountingDirection;
import com.skyflytech.accountservice.core.account.service.AccountService;
import com.skyflytech.accountservice.core.accountSet.model.AccountSet;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountAmountHolder;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.accountingPeriod.repository.AccountingPeriodRepository;
import com.skyflytech.accountservice.core.accountingPeriod.service.AccountingPeriodService;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Sort;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class AccountingPeriodServiceImp implements AccountingPeriodService {

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final MongoTemplate mongoTemplate;
    private final AccountService accountService;

    @Autowired
    public AccountingPeriodServiceImp(AccountingPeriodRepository accountingPeriodRepository,
                                      MongoTemplate mongoTemplate,
                                      AccountService accountService) {
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.mongoTemplate = mongoTemplate;
        this.accountService = accountService;
    }

    public AccountingPeriod createInitialPeriod(AccountSet accountSet, YearMonth startMonth) {
        AccountingPeriod initialPeriod = new AccountingPeriod();
        initialPeriod.setAccountSetId(accountSet.getId());
        initialPeriod.setName(generatePeriodName(startMonth));
        initialPeriod.setStartDate(startMonth.atDay(1));
        initialPeriod.setEndDate(startMonth.atEndOfMonth());
        initialPeriod.setClosed(false);

        for (Entry<String, BigDecimal> entry : accountSet.getInitialAccountBalance().entrySet()) {
            AccountAmountHolder accountAmountHolder = new AccountAmountHolder();
            accountAmountHolder.setBalance(entry.getValue());
            accountAmountHolder.setTotalCredit(BigDecimal.ZERO);
            accountAmountHolder.setTotalDebit(BigDecimal.ZERO);
            initialPeriod.getAmountHolders().put(entry.getKey(), accountAmountHolder);
        }

        return accountingPeriodRepository.save(initialPeriod);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "accountingPeriods", key = "#transaction.accountSetId + '_current'"),
            @CacheEvict(value = "accountingPeriods", key = "#transaction.accountSetId + '_last'")
    })
    public void updateAccountingPeriodsWhenTransactionAmountChange(Transaction transaction, BigDecimal debitChange,
            BigDecimal creditChange) {
        Account account = mongoTemplate.findById(transaction.getAccountId(), Account.class);
        if (account == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }

        List<AccountingPeriod> accountingPeriods = findRelevantAccountingPeriods(transaction.getAccountSetId(),
                transaction.getModifiedDate());
        List<String> relatedAccountIds = getRelatedAccountIds(transaction.getAccountId());

        for (AccountingPeriod period : accountingPeriods) {
            updatePeriodAmountHolders(period, relatedAccountIds, account.getBalanceDirection(), debitChange,
                    creditChange);
            updateAccountingPeriodCache(period);
        }
        accountingPeriodRepository.saveAll(accountingPeriods);
    }

    @Caching(evict = {
            @CacheEvict(value = "accountingPeriods", key = "#accountSetId + '_current'"),
            @CacheEvict(value = "accountingPeriods", key = "#accountSetId + '_last'")
    })
    public void deleteAccountPeriodsByAccountSetId(String accountSetId) {
        Query query = new Query(Criteria.where("accountSetId").is(accountSetId));
        List<AccountingPeriod> periodsToDelete = mongoTemplate.find(query, AccountingPeriod.class);
        for (AccountingPeriod period : periodsToDelete) {
            evictAccountingPeriodCache(period);
        }
        mongoTemplate.remove(query, AccountingPeriod.class);
    }

    // update balances of related accounts in accountingPeriod
    private void updatePeriodAmountHolders(AccountingPeriod period, List<String> relatedAccountIds,
            AccountingDirection direction, BigDecimal debitChange, BigDecimal creditChange) {
        Map<String, AccountAmountHolder> amountHolders = period.getAmountHolders();
        for (String accountId : relatedAccountIds) {
            AccountAmountHolder accountAmountHolder = amountHolders.get(accountId);
            if (accountAmountHolder == null) {
                throw new NoSuchElementException(
                        "can find account id:" + accountId + " when update period amount holder");
            }
            accountAmountHolder.setTotalCredit(accountAmountHolder.getTotalCredit().add(creditChange));
            accountAmountHolder.setTotalDebit(accountAmountHolder.getTotalDebit().add(debitChange));
            if (direction == AccountingDirection.DEBIT) {
                accountAmountHolder
                        .setBalance(accountAmountHolder.getBalance().add(debitChange).subtract(creditChange));
            } else {
                accountAmountHolder
                        .setBalance(accountAmountHolder.getBalance().add(creditChange).subtract(debitChange));
            }
        }
    }

    // create accounting periods from a start accounting period to an end date
    @Transactional
    public List<AccountingPeriod> createAccountingPeriodsFromStartPeriodToEndDate(AccountingPeriod startPeriod,
            LocalDate endDate) {
        YearMonth startMonth = YearMonth.from(startPeriod.getStartDate());
        YearMonth endMonth = YearMonth.from(endDate);
        List<AccountingPeriod> accountingPeriods = new ArrayList<>();
        AccountingPeriod pre_accountingPeriod = startPeriod;

        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            AccountingPeriod accountingPeriod = createNextAccountingPeriod(pre_accountingPeriod);
            accountingPeriods.add(accountingPeriod);
            pre_accountingPeriod = accountingPeriod;
        }
        return accountingPeriodRepository.saveAll(accountingPeriods);
    }

    // find the latest(mean its date is the greatest) accounting period by
    // accountSetId
    @Cacheable(value = "accountingPeriods", key = "#accountSetId + '_last'")
    public AccountingPeriod findLastAccountingPeriodByAccountSetId(String accountSetId) {
        Query query = new Query(Criteria.where("accountSetId").is(accountSetId));
        // order by endDate in descending already defined in class AccountingPeriod
        return mongoTemplate.findOne(query, AccountingPeriod.class);
    }

    // anti-close to previous months include current month
    @Caching(evict = {
            @CacheEvict(value = "accountingPeriods", key = "#accountingPeriod.accountSetId + '_current'"),
            @CacheEvict(value = "accountingPeriods", key = "#accountingPeriod.accountSetId + '_last'")
    })
    public void antiCloseAccountingPeriod(AccountingPeriod accountingPeriod, LocalDate startDate) {
        Query query = new Query(Criteria.where("accountSetId").is(accountingPeriod.getAccountSetId())
                .and("startDate").gte(startDate).and("endDate").lte(accountingPeriod.getEndDate()));
        List<AccountingPeriod> accountingPeriods = mongoTemplate.find(query, AccountingPeriod.class);
        for (AccountingPeriod period : accountingPeriods) {
            period.setClosed(false);
            updateAccountingPeriodCache(period);
        }
        accountingPeriodRepository.saveAll(accountingPeriods);
    }

    // find current accounting period(mean the last open accounting period)
    // db data is ordered by endDate in descending
    @Cacheable(value = "accountingPeriods", key = "#accountSetId + '_current'")
    public AccountingPeriod findCurrentAccountingPeriod(String accountSetId) {
        Query query = new Query(Criteria.where("accountSetId").is(accountSetId).and("isClosed").is(false));
        query.with(Sort.by(Sort.Direction.ASC, "endDate"));
        return mongoTemplate.findOne(query, AccountingPeriod.class);
    }

    // create an initial accounting period
    @CachePut(value = "accountingPeriods", key = "#result.id")
    public AccountingPeriod createInitialAccountingPeriod(AccountSet accountSet) {
        LocalDate theFirstDayOfMonth = accountSet.getAccountingPeriodStartDate();
        LocalDate theLastDayOfMonth = accountSet.getAccountingPeriodStartDate().plusMonths(1).minusDays(1);
        AccountingPeriod accountingPeriod = new AccountingPeriod();
        accountingPeriod.setAccountSetId(accountSet.getId());
        accountingPeriod.setStartDate(theFirstDayOfMonth);
        accountingPeriod.setEndDate(theLastDayOfMonth);

        // 初始化amountHolders
        for (Entry<String, BigDecimal> entry : accountSet.getInitialAccountBalance().entrySet()) {
            AccountAmountHolder accountAmountHolder = new AccountAmountHolder();
            accountAmountHolder.setTotalDebit(BigDecimal.ZERO);
            accountAmountHolder.setTotalCredit(BigDecimal.ZERO);
            accountAmountHolder.setBalance(entry.getValue());
            accountingPeriod.getAmountHolders().put(entry.getKey(), accountAmountHolder);
        }
        accountingPeriod.setClosed(false);
        // 保存会计期间
        return accountingPeriodRepository.save(accountingPeriod);
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

    private String generatePeriodName(YearMonth yearMonth) {
        return yearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
    }

    @CachePut(value = "accountingPeriods", key = "#result.id")
    public AccountingPeriod createNextAccountingPeriod(AccountingPeriod currentPeriod) {
        YearMonth nextMonth = YearMonth.from(currentPeriod.getEndDate()).plusMonths(1);
        AccountingPeriod newPeriod = new AccountingPeriod();
        newPeriod.setAccountSetId(currentPeriod.getAccountSetId());
        newPeriod.setName(generatePeriodName(nextMonth));
        newPeriod.setStartDate(nextMonth.atDay(1));
        newPeriod.setEndDate(nextMonth.atEndOfMonth());
        newPeriod.setAmountHolders(currentPeriod.getAmountHolders());
        // find all transactions in current period

        return newPeriod;
    }

    // find next period
    @Cacheable(value = "accountingPeriods", key = "#accountPeriod.id + '_next'")
    public AccountingPeriod findNextPeriod(AccountingPeriod accountPeriod) {
        // start and end date of next period
        YearMonth nextMonth = YearMonth.from(accountPeriod.getEndDate()).plusMonths(1);
        LocalDate startDate = nextMonth.atDay(1);
        LocalDate endDate = nextMonth.atEndOfMonth();
        // check if there is a period with the same accountSetId and startDate and
        // endDate
        Query query = new Query(Criteria.where("accountSetId").is(accountPeriod.getAccountSetId())
                .and("startDate").is(startDate).and("endDate").is(endDate));
        return mongoTemplate.findOne(query, AccountingPeriod.class);
    }

    @CachePut(value = "accountingPeriods", key = "#result.id")
    public AccountingPeriod updateAccountingPeriodCache(AccountingPeriod period) {
        return period;
    }

    @CacheEvict(value = "accountingPeriods", key = "#period.id")
    public void evictAccountingPeriodCache(AccountingPeriod period) {
        // This method is intentionally left empty. The @CacheEvict annotation handles
        // the cache eviction.
    }

    @Override
    public List<AccountingPeriod> findByAccountSetId(String accountSetId) {
        return mongoTemplate.find(Query.query(Criteria.where("accountSetId").is(accountSetId)),AccountingPeriod.class);
    }

    @Override
    @CacheEvict(value = "accountingPeriods", allEntries = true)
    public void saveAll(List<AccountingPeriod> accountingPeriods) {
        accountingPeriodRepository.saveAll(accountingPeriods);
    }

}
