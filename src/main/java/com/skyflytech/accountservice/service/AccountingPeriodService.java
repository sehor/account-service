package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.domain.account.AccountType;
import com.skyflytech.accountservice.domain.account.TransferAccountType;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.global.GlobalConst;
import com.skyflytech.accountservice.repository.AccountingPeriodRepository;
import com.skyflytech.accountservice.repository.TransactionMongoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AccountingPeriodService {

    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    @Autowired
    private TransactionMongoRepository transactionRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JournalEntryService journalEntryService;

    @Transactional
    public AccountingPeriod closeAccountingPeriod(String accountingPeriodId) {
        AccountingPeriod currentPeriod = accountingPeriodRepository.findById(accountingPeriodId)
                .orElseThrow(() -> new RuntimeException("会计期间不存在"));

        if (currentPeriod.isClosed()) {
            throw new RuntimeException("该会计期间已经结账");
        }

        // 计算所有账户的期末余额
        YearMonth currentMonth = YearMonth.from(currentPeriod.getEndDate());
        Map<String, BigDecimal> closingBalances = accountService.calculateAllAccountBalancesForMonth(currentPeriod.getAccountSetId(), currentMonth);

        // 1. 结转期间
        transferPeriod(currentPeriod, closingBalances);

        // 2. 重新计算期末余额
        Map<String, BigDecimal> updatedClosingBalances = accountService.calculateAllAccountBalancesForMonth(currentPeriod.getAccountSetId(), currentMonth);
        currentPeriod.setClosingBalances(updatedClosingBalances);

        // 3. 检查会计恒等式
        checkAccountingEquation(updatedClosingBalances);

        // 4. 创建新的会计期间，并将当前期间的期末余额设置为新期间的期初余额
        AccountingPeriod newPeriod = createNewAccountingPeriod(currentPeriod, updatedClosingBalances);

        // 标记当前期间为已关闭
        currentPeriod.setClosed(true);
        accountingPeriodRepository.save(currentPeriod);

        return newPeriod;
    }

    private void transferPeriod(AccountingPeriod period, Map<String, BigDecimal> balances) {
        String accountSetId = period.getAccountSetId();
        LocalDate endDate = period.getEndDate();
        List<Account> allAccounts = accountService.getAllAccounts();

        // 找到"本年利润"科目
        Account profitAccount = allAccounts.stream()
                .filter(account -> GlobalConst.CURRENT_YEAR_PROFIT_CODE.equals(account.getCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到'本年利润'科目，代码: " + GlobalConst.CURRENT_YEAR_PROFIT_CODE));

        // 找到"未分配利润"科目
        Account undistributedProfitAccount = allAccounts.stream()
                .filter(account -> GlobalConst.UNDISTRIBUTED_PROFIT_CODE.equals(account.getCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("未找到'未分配利润'科目，代码: " + GlobalConst.UNDISTRIBUTED_PROFIT_CODE));

        JournalEntryView journalEntryView = new JournalEntryView();
        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setAccountSetId(accountSetId);
        journalEntry.setCreatedDate(endDate);
        journalEntry.setVoucherWord("结转");
        journalEntryView.setJournalEntry(journalEntry);

        List<Transaction> transactions = new ArrayList<>();

        BigDecimal totalProfit = BigDecimal.ZERO;

        for (Map.Entry<TransferAccountType, List<AccountType>> entry : GlobalConst.AUTO_TRANSFER_ACCOUNTS.entrySet()) {
            TransferAccountType transferType = entry.getKey();
            List<AccountType> accountTypes = entry.getValue();

            BigDecimal totalAmount = BigDecimal.ZERO;
            for (Account account : getLeafAccounts(allAccounts)) {
                if (accountTypes.contains(account.getType())) {
                    BigDecimal balance = balances.getOrDefault(account.getId(), BigDecimal.ZERO);
                    totalAmount = totalAmount.add(balance);
                    
                    // 创建从该账户到"本年利润"的结转交易
                    transactions.add(createTransaction(account, balance, transferType != TransferAccountType.INCOME));
                }
            }

            // 创建"本年利润"的对应交易
            if (totalAmount.compareTo(BigDecimal.ZERO) != 0) {
                transactions.add(createTransaction(profitAccount, totalAmount, transferType == TransferAccountType.INCOME));
                totalProfit = totalProfit.add(transferType == TransferAccountType.INCOME ? totalAmount : totalAmount.negate());
            }
        }

        // 结转本年利润到未分配利润
        if (totalProfit.compareTo(BigDecimal.ZERO) != 0) {
            transactions.add(createTransaction(profitAccount, totalProfit, totalProfit.compareTo(BigDecimal.ZERO) > 0));
            transactions.add(createTransaction(undistributedProfitAccount, totalProfit, totalProfit.compareTo(BigDecimal.ZERO) < 0));
        }

        journalEntryView.setTransactions(transactions);
        journalEntryService.processJournalEntryView(journalEntryView);
    }

    private List<Account> getLeafAccounts(List<Account> allAccounts) {
        return accountService.getLeafAccounts(allAccounts);
    }

    private Transaction createTransaction(Account account, BigDecimal amount, boolean isDebit) {
        Transaction transaction = new Transaction();
        transaction.setAccountId(account.getId());
        if (isDebit) {
            transaction.setDebit(amount);
            transaction.setCredit(BigDecimal.ZERO);
        } else {
            transaction.setDebit(BigDecimal.ZERO);
            transaction.setCredit(amount);
        }
        return transaction;
    }

    private void checkAccountingEquation(Map<String, BigDecimal> balances) {
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilitiesAndEquity = BigDecimal.ZERO;

        // 计算资产总额和负债+所有者权益总额
        // 这里需要根据您的账户结构来实现具体的计算逻辑

        if (assets.compareTo(liabilitiesAndEquity) != 0) {
            throw new RuntimeException("会计恒等式不平衡");
        }
    }

    private AccountingPeriod createNewAccountingPeriod(AccountingPeriod currentPeriod, Map<String, BigDecimal> closingBalances) {
        YearMonth nextMonth = YearMonth.from(currentPeriod.getEndDate()).plusMonths(1);
        
        AccountingPeriod newPeriod = new AccountingPeriod();
        newPeriod.setAccountSetId(currentPeriod.getAccountSetId());
        newPeriod.setName(generateNextPeriodName(nextMonth));
        newPeriod.setStartDate(nextMonth.atDay(1));
        newPeriod.setEndDate(nextMonth.atEndOfMonth());
        newPeriod.setOpeningBalances(closingBalances);
        newPeriod.setClosingBalances(Map.of()); // 初始化为空Map
        newPeriod.setClosed(false);

        return accountingPeriodRepository.save(newPeriod);
    }

    private String generateNextPeriodName(YearMonth yearMonth) {
        return yearMonth.format(DateTimeFormatter.ofPattern("yyyy年MM月"));
    }

    public AccountingPeriod createInitialPeriod(String accountSetId, YearMonth startMonth) {
        AccountingPeriod initialPeriod = new AccountingPeriod();
        initialPeriod.setAccountSetId(accountSetId);
        initialPeriod.setName(generateNextPeriodName(startMonth));
        initialPeriod.setStartDate(startMonth.atDay(1));
        initialPeriod.setEndDate(startMonth.atEndOfMonth());
        initialPeriod.setOpeningBalances(Map.of()); // 初始余额为空
        initialPeriod.setClosingBalances(Map.of()); // 初始余额为空
        initialPeriod.setClosed(false);

        return accountingPeriodRepository.save(initialPeriod);
    }
}