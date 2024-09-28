package com.skyflytech.accountservice.core.accountSet.service.imp;

import com.skyflytech.accountservice.core.account.model.Account;
import com.skyflytech.accountservice.core.account.service.AccountService;
import com.skyflytech.accountservice.core.account.service.ExcelImportService;
import com.skyflytech.accountservice.core.accountSet.model.AccountSet;
import com.skyflytech.accountservice.core.accountSet.repository.AccountSetRepository;
import com.skyflytech.accountservice.core.accountSet.service.AccountSetService;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountAmountHolder;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.accountingPeriod.service.AccountingPeriodService;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import com.skyflytech.accountservice.security.model.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.security.model.User;
import com.skyflytech.accountservice.security.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class AccountSetServiceImp implements AccountSetService {

    private final AccountSetRepository accountSetRepository;
    private final ExcelImportService excelImportService;
    private final AccountService accountService;
    private final AccountingPeriodService accountingPeriodService;
    private final UserService userService;
    private final MongoTemplate mongoTemplate;

    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;


    @Autowired
    public AccountSetServiceImp(AccountSetRepository accountSetRepository, ExcelImportService excelImportService,
                                AccountService accountService,
                                CurrentAccountSetIdHolder currentAccountSetIdHolder,
                                MongoTemplate mongoTemplate,
                                UserService userService,
                                AccountingPeriodService accountingPeriodService) {
        this.accountSetRepository = accountSetRepository;
        this.excelImportService = excelImportService;
        this.accountService = accountService;
        this.mongoTemplate = mongoTemplate;
        this.userService = userService;
        this.currentAccountSetIdHolder=currentAccountSetIdHolder;
        this.accountingPeriodService=accountingPeriodService;
    }

    @Transactional
    @CachePut(value = "accountSets", key = "#result.id")
    public AccountSet createAccountSet(AccountSet accountSet, String userName) {
        // 检查这里是否有权限验证逻辑
        // ...
        accountSet.setCreatedAt(LocalDateTime.now());
        accountSet.setUpdatedAt(LocalDateTime.now());
        // check if the accountSet name is already exists
        if (accountSetRepository.findByName(accountSet.getName()).isPresent()) {
            throw new IllegalArgumentException("Account set with name " + accountSet.getName() + " already exists");
        }
        // 默认的会计科目资源路径
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("static/default-accounts.xlsx");

        if (inputStream == null) {
            throw new IllegalStateException("无法找到 default_accounts.xlsx 文件");
        }
        // 保存账套，得到账套id
        AccountSet createdAccountSet = accountSetRepository.save(accountSet);
        // 将默认的会计科目导入到账套中
        excelImportService.extractAccountsFromExcel(inputStream, createdAccountSet.getId());

        List<Account> accounts = accountService.getAllAccounts(accountSet.getId());
        // 设置账套的初始会计科目余额
        accountSet.setInitialAccountBalance(
                accounts.stream().collect(Collectors.toMap(Account::getId, Account::getInitialBalance)));
        // 为账套创建最初的会计期间
        accountingPeriodService.createInitialAccountingPeriod(accountSet);

        // 重设user context
        User user = userService.getUserByUsername(userName);
        if (user != null) {
            user.getAccountSetIds().add(createdAccountSet.getId());
            userService.updateUserAccountSetIds(userName, user.getAccountSetIds());
        }
        return createdAccountSet;
    }

    // get all account sets
    @Cacheable(value = "accountSets", key = "#root.method.name")
    public List<AccountSet> getAllAccountSets() {
        return accountSetRepository.findAll();
    }

    @Transactional
    @CacheEvict(value = "accountSets", key = "#id")
    public User deleteAccountSet(String id, User user) {
        if (accountSetRepository.findById(id).isPresent()) {
            try {
                // 1. Delete all transactions related to this account set
                deleteTransactionsByAccountSetId(id);
                System.out.println("Deleted all transactions for account set " + id);

                // 2. Delete all journal entries related to this account set
                deleteJournalEntriesByAccountSetId(id);
                System.out.println("Deleted all journal entries for account set " + id);

                // 3. Delete all accounts related to this account set
                accountService.deleteAccountsByAccountSetId(id);
                System.out.println("Deleted all accounts for account set " + id);

                // 4. Delete all accounting periods related to this account set
                accountingPeriodService.deleteAccountPeriodsByAccountSetId(id);
                System.out.println("Deleted all accounting periods for account set " + id);

                // 5. Delete the account set
                accountSetRepository.deleteById(id);
                System.out.println("Deleted account set " + id);

                // 6. delete the account set from user's account set list
                User update_user = userService.getUserByUsername(user.getUsername());

                if (update_user != null) {
                    update_user.getAccountSetIds().remove(id);
                    userService.updateUserAccountSetIds(update_user.getUsername(), update_user.getAccountSetIds());
                    System.out.println("remove id: " + id + " from user " + user.getUsername() + " accountSetIds");
                    return update_user;
                } else {
                    throw new NoSuchElementException("User not found, unable to delete account set. ID: " + id);
                }

            } catch (Exception e) {
                System.err.println("Error occurred while deleting account set " + id + ": " + e.getMessage());
                throw e; // Re-throw the exception so the transaction can be rolled back
            }
        } else {
            throw new IllegalArgumentException("Account set not found, unable to delete. ID: " + id);
        }
    }

    @Transactional
    public void switchAccountSet(String id) {
        AccountSet accountSet = accountSetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account set not found with id: " + id));

        System.out.println("Switched to account set: " + accountSet.getName() + " (ID: " + id + ")");
    }

    @Cacheable(value = "accountSets", key = "#ids")
    public List<AccountSet> getAccountSetsByIds(List<String> ids) {
        Query query = new Query(Criteria.where("id").in(ids));
        query.fields()
                .include("id")
                .include("name")
                .include("description")
                .include("accountingPeriodStartDate");

        return mongoTemplate.find(query, AccountSet.class);
    }

    @Cacheable(value = "accountSets", key = "#id")
    public AccountSet getAccountSetById(String id) {
        return accountSetRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account set not found with id: " + id));
    }

    @Transactional
    public void initializeOpeningBalances(String accountSetId, Map<String, BigDecimal> openingBalances) {
        if (accountSetId == null || !accountSetId.equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match.");
        }
        // find accountSet
        AccountSet accountSet = mongoTemplate.findById(accountSetId, AccountSet.class);
        if (accountSet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AccountSet not found");
        }
        // changes of accountSet initialAccountBalance items
        Map<String, BigDecimal> changes = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : openingBalances.entrySet()) {
            String accountId = entry.getKey();
            BigDecimal oldBalance = accountSet.getInitialAccountBalance().get(accountId);
            if (oldBalance == null) {
                // add new item
                accountSet.getInitialAccountBalance().put(accountId, entry.getValue());
                changes.put(accountId, entry.getValue());
            } else {
                BigDecimal change = entry.getValue().subtract(oldBalance);
                changes.put(accountId, change);
            }
        }
        // update all following accountingPeriods
        List<AccountingPeriod> accountingPeriods = accountingPeriodService.findByAccountSetId(accountSetId);
        for (AccountingPeriod accountingPeriod : accountingPeriods) {
            for (Map.Entry<String, BigDecimal> entry : changes.entrySet()) {
                AccountAmountHolder accountAmountHolder = accountingPeriod.getAmountHolders().get(entry.getKey());
                if (accountAmountHolder != null) {
                    accountAmountHolder.setBalance(accountAmountHolder.getBalance().add(entry.getValue()));
                } else {
                    // add new item
                    accountAmountHolder = new AccountAmountHolder();
                    accountAmountHolder.setBalance(entry.getValue());
                    accountingPeriod.getAmountHolders().put(entry.getKey(), accountAmountHolder);
                }
            }
        }
        // 保存accountingPeriods改变
        accountingPeriodService.saveAll(accountingPeriods);
        // 保存accountSet
        for (Map.Entry<String, BigDecimal> entry : openingBalances.entrySet()) {
            accountSet.getInitialAccountBalance().put(entry.getKey(), entry.getValue());
        }
        mongoTemplate.save(accountSet);
    }

    @Transactional
    void deleteTransactionsByAccountSetId(String accountSetId) {
        Query query=Query.query(Criteria.where("accountSetId").is(accountSetId));
        mongoTemplate.remove(query, Transaction.class);
    }

    @Transactional
    void deleteJournalEntriesByAccountSetId(String accountSetId){
        Query query=Query.query(Criteria.where("accountSetId").is(accountSetId));
        mongoTemplate.remove(query, JournalEntry.class);
    }
}