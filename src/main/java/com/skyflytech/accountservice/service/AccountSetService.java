package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountSet;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.repository.AccountSetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.List;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
@Service
public class AccountSetService  {

    private final AccountSetRepository accountSetRepository;
    private final ExcelImportService excelImportService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final JournalEntryService journalEntryService;
    private final AccountPeriodService accountPeriodService;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;

    @Autowired
    public AccountSetService(AccountSetRepository accountSetRepository, ExcelImportService excelImportService, AccountService accountService, TransactionService transactionService, JournalEntryService journalEntryService, AccountPeriodService accountPeriodService, CurrentAccountSetIdHolder currentAccountSetIdHolder) {
        this.accountSetRepository = accountSetRepository;
        this.excelImportService = excelImportService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.journalEntryService = journalEntryService;
        this.accountPeriodService = accountPeriodService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
    }

    @Transactional
    public AccountSet createAccountSet(AccountSet accountSet) {
        accountSet.setCreatedAt(LocalDateTime.now());
        accountSet.setUpdatedAt(LocalDateTime.now());
        //check if the accountSet name is already exists
        if (accountSetRepository.findByName(accountSet.getName()).isPresent()) {
            throw new IllegalArgumentException("Account set with name " + accountSet.getName() + " already exists");
        }   


        AccountSet createdAccountSet = accountSetRepository.save(accountSet);

        // 修改资源路径
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("static/default-accounts.xlsx");
        
        if (inputStream == null) {
            throw new IllegalStateException("无法找到 default_accounts.xlsx 文件");
        }

        excelImportService.extractAccountsFromExcel(inputStream, createdAccountSet.getId());
        List<Account> accounts = accountService.getAllAccounts(createdAccountSet.getId());
        createdAccountSet.setInitialAccountBalance(accounts.stream().collect(Collectors.toMap(Account::getId, Account::getInitialBalance)));
        
        accountSetRepository.save(createdAccountSet);
        return createdAccountSet;
    }
    //get all account sets
    public List<AccountSet> getAllAccountSets() {
        return accountSetRepository.findAll();
    }

    @Transactional
    public void deleteAccountSet(String id) {
        if(accountSetRepository.findById(id).isPresent()) {
            try {
                // 1. Delete all transactions related to this account set
                transactionService.deleteTransactionsByAccountSetId(id);
                System.out.println("Deleted all transactions for account set " + id);
                
                // 2. Delete all journal entries related to this account set
                journalEntryService.deleteJournalEntriesByAccountSetId(id);
                System.out.println("Deleted all journal entries for account set " + id);
                
                // 3. Delete all accounts related to this account set
                accountService.deleteAccountsByAccountSetId(id);
                System.out.println("Deleted all accounts for account set " + id);
                
                // 4. Delete all accounting periods related to this account set
                accountPeriodService.deleteAccountPeriodsByAccountSetId(id);
                System.out.println("Deleted all accounting periods for account set " + id);
                
                // 5. Delete the account set
                accountSetRepository.deleteById(id);
                System.out.println("Deleted account set " + id);
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
        
        currentAccountSetIdHolder.setCurrentAccountSetId(id);
        System.out.println("Switched to account set: " + accountSet.getName() + " (ID: " + id + ")");
    }
}