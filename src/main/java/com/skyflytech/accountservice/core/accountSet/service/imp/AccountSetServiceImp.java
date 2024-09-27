package com.skyflytech.accountservice.core.accountSet.service.imp;

import com.skyflytech.accountservice.core.account.service.imp.AccountServiceImp;
import com.skyflytech.accountservice.core.accountSet.model.AccountSet;
import com.skyflytech.accountservice.core.account.model.Account;
import com.skyflytech.accountservice.core.accountSet.repository.AccountSetRepository;
import com.skyflytech.accountservice.core.accountingPeriod.service.imp.AccountingPeriodServiceImp;
import com.skyflytech.accountservice.security.model.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.security.model.User;
import com.skyflytech.accountservice.security.service.Imp.UserServiceImp;
import com.skyflytech.accountservice.core.account.service.ExcelImportService;
import com.skyflytech.accountservice.core.journalEntry.service.imp.JournalEntryServiceImp;
import com.skyflytech.accountservice.core.transaction.service.imp.TransactionServiceImp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class AccountSetServiceImp {

    private final AccountSetRepository accountSetRepository;
    private final ExcelImportService excelImportService;
    private final AccountServiceImp accountServiceImp;
    private final TransactionServiceImp transactionServiceImp;
    private final JournalEntryServiceImp journalEntryServiceImp;
    private final AccountingPeriodServiceImp accountingPeriodServiceImp;
    private final UserServiceImp userServiceImp;
    private final MongoTemplate mongoTemplate;

    @Autowired
    public AccountSetServiceImp(AccountSetRepository accountSetRepository, ExcelImportService excelImportService,
                                AccountServiceImp accountServiceImp, TransactionServiceImp transactionServiceImp,
                                JournalEntryServiceImp journalEntryServiceImp, AccountingPeriodServiceImp accountingPeriodServiceImp,
                                CurrentAccountSetIdHolder currentAccountSetIdHolder, MongoTemplate mongoTemplate, UserServiceImp userServiceImp) {
        this.accountSetRepository = accountSetRepository;
        this.excelImportService = excelImportService;
        this.accountServiceImp = accountServiceImp;
        this.transactionServiceImp = transactionServiceImp;
        this.journalEntryServiceImp = journalEntryServiceImp;
        this.accountingPeriodServiceImp = accountingPeriodServiceImp;
        this.mongoTemplate = mongoTemplate;
        this.userServiceImp = userServiceImp;
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

        List<Account> accounts = accountServiceImp.getAllAccounts(accountSet.getId());
        // 设置账套的初始会计科目余额
        accountSet.setInitialAccountBalance(
                accounts.stream().collect(Collectors.toMap(Account::getId, Account::getInitialBalance)));
        // 为账套创建最初的会计期间
        accountingPeriodServiceImp.createInitialAccountingPeriod(accountSet);

        // 重设user context
        User user = userServiceImp.getUserByUsername(userName);
        if (user != null) {
            user.getAccountSetIds().add(createdAccountSet.getId());
            userServiceImp.updateUserAccountSetIds(userName, user.getAccountSetIds());
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
                transactionServiceImp.deleteTransactionsByAccountSetId(id);
                System.out.println("Deleted all transactions for account set " + id);

                // 2. Delete all journal entries related to this account set
                journalEntryServiceImp.deleteJournalEntriesByAccountSetId(id);
                System.out.println("Deleted all journal entries for account set " + id);

                // 3. Delete all accounts related to this account set
                accountServiceImp.deleteAccountsByAccountSetId(id);
                System.out.println("Deleted all accounts for account set " + id);

                // 4. Delete all accounting periods related to this account set
                accountingPeriodServiceImp.deleteAccountPeriodsByAccountSetId(id);
                System.out.println("Deleted all accounting periods for account set " + id);

                // 5. Delete the account set
                accountSetRepository.deleteById(id);
                System.out.println("Deleted account set " + id);

                // 6. delete the account set from user's account set list
                User update_user = userServiceImp.getUserByUsername(user.getUsername());

                if (update_user != null) {
                    update_user.getAccountSetIds().remove(id);
                    userServiceImp.updateUserAccountSetIds(update_user.getUsername(), update_user.getAccountSetIds());
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
}