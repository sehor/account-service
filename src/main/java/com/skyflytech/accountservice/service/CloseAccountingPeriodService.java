package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountAmountHolder;
import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.domain.account.AccountType;
import com.skyflytech.accountservice.domain.account.TransferAccountType;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.global.GlobalConst;
import com.skyflytech.accountservice.repository.AccountingPeriodRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CloseAccountingPeriodService {
    private static final Logger logger = LoggerFactory.getLogger(CloseAccountingPeriodService.class);

    private final AccountingPeriodRepository accountingPeriodRepository;
    private final ProcessJournalEntry processJournalEntryService;
    private final MongoTemplate mongoTemplate;
    private final AccountingPeriodService accountingPeriodService;
    @Autowired
    public CloseAccountingPeriodService(AccountingPeriodRepository accountingPeriodRepository,
            ProcessJournalEntry processJournalEntryService,
            AccountingPeriodService accountingPeriodService,
            MongoTemplate mongoTemplate) {
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.processJournalEntryService = processJournalEntryService;
        this.mongoTemplate = mongoTemplate;
        this.accountingPeriodService = accountingPeriodService;
    }

    @Transactional
    public AccountingPeriod closeAccountingPeriod(String accountingPeriodId) {
        AccountingPeriod currentPeriod = accountingPeriodRepository.findById(accountingPeriodId)
                .orElseThrow(() -> new RuntimeException("会计期间不存在"));

        if (currentPeriod.isClosed()) {
            throw new RuntimeException("该会计期间已经结账");
        }

        // 所有账户的期末余额
        Map<String, BigDecimal> closingBalances = new HashMap<>();
        for (Map.Entry<String, AccountAmountHolder> holderEntry : currentPeriod.getAmountHolders().entrySet()) {
            closingBalances.put(holderEntry.getKey(), holderEntry.getValue().getBalance());
        }

        // 检查是否存在下一个会计期间,如果不存在则创建一个新的会计期间
        // 必须先创建新的会计期间,否则结转数据不能传递到下一个会计期间
        AccountingPeriod newPeriod = accountingPeriodService.findNextPeriod(currentPeriod);
        if (newPeriod == null) {
            newPeriod = accountingPeriodService.createNextAccountingPeriod(currentPeriod);
        }

        // 结转期间费用
        transferPeriod(currentPeriod, closingBalances);

        // 检查会计恒等式
        checkAccountingEquation(closingBalances);

        // 标记当前期间为已关闭
        currentPeriod.setClosed(true);
        accountingPeriodRepository.save(currentPeriod);

        return newPeriod;
    }

    @Transactional
    public void transferPeriod(AccountingPeriod period, Map<String, BigDecimal> balances) {
        String accountSetId = period.getAccountSetId();
        List<Account> allAccounts = findAllLeafAccountsForClosingPeriod(accountSetId);
        Account profitAccount = findAccountByCode(allAccounts, GlobalConst.CURRENT_YEAR_PROFIT_CODE);
        List<JournalEntryView> journalEntryViews = new ArrayList<>();
        // 结转收入
        journalEntryViews.add(transferIncomeAccounts(findIncomeAccounts(allAccounts), period, profitAccount));
        // 结转费用和损失
        journalEntryViews
                .add(transferExpenseAndLossAccounts(findExpenseAndLossAccounts(allAccounts), period, profitAccount));
        // 结转以前年度损益调整
        journalEntryViews.add(transferPriorYearAdjustmentAccounts(findPriorYearAdjustmentAccounts(allAccounts), period,
                profitAccount));
        // 创建journalEntry

        for (JournalEntryView journalEntryView : journalEntryViews) {
            if (journalEntryView != null) {
                processJournalEntryService.processJournalEntryView(journalEntryView);
            } else {
                logger.info("entry is empty");
            }
        }
    }

    private Account findAccountByCode(List<Account> accounts, String code) {
        return accounts.stream()
                .filter(account -> code.equals(account.getCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("not found account,the code is: " + code));
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



    private Transaction createTransaction(Account account, BigDecimal amount, boolean isDebit, String description) {
        LocalDate now = LocalDate.now();
        Transaction transaction = new Transaction();
        transaction.setCreatedDate(now);
        transaction.setModifiedDate(now);
        transaction.setDescription(description);
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

    // create journalEntry
    private JournalEntry createJournalEntry(String accountSetId, String voucherWord) {
        JournalEntry journalEntry = new JournalEntry();
        journalEntry.setAccountSetId(accountSetId);
        LocalDate now = LocalDate.now();
        journalEntry.setCreatedDate(now);
        journalEntry.setModifiedDate(now);
        journalEntry.setVoucherWord(voucherWord);
        return journalEntry;
    }

    /**
     * 查找所有需要期末结转的期间叶子账户
     * 
     * @param accountSetId
     * @return
     **/
    public List<Account> findAllLeafAccountsForClosingPeriod(String accountSetId) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS.values().stream()
                .flatMap(List::stream).toList();
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).collect(Collectors.toList());
        List<Account> accounts = mongoTemplate.find(Query.query(Criteria.where("accountSetId").is(accountSetId)
                .and("type").in(accountTypeValues)), Account.class);
        List<String> parentIds = accounts.stream().map(Account::getParentId).toList();
        return accounts.stream().filter(account -> !parentIds.contains(account.getId())).collect(Collectors.toList());
    }

    // 找到期间收入账户
    public List<Account> findIncomeAccounts(List<Account> accounts) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS.get(TransferAccountType.INCOME);
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).toList();
        return accounts.stream().filter(account -> accountTypeValues.contains(account.getType().name()))
                .collect(Collectors.toList());
    }

    // 找到期间费用和损失账户
    public List<Account> findExpenseAndLossAccounts(List<Account> accounts) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS.get(TransferAccountType.ALL_EXPENSE_TYPES);
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).toList();
        return accounts.stream().filter(account -> accountTypeValues.contains(account.getType().name()))
                .collect(Collectors.toList());
    }

    // 找到以前年度损益调整账户
    public List<Account> findPriorYearAdjustmentAccounts(List<Account> accounts) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS
                .get(TransferAccountType.PRIOR_YEAR_ADJUSTMENT);
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).collect(Collectors.toList());
        return accounts.stream().filter(account -> accountTypeValues.contains(account.getType().name()))
                .collect(Collectors.toList());
    }

    // 结转收入账户
    public JournalEntryView transferIncomeAccounts(List<Account> incomeAccounts, AccountingPeriod period,
            Account profitAccount) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (incomeAccounts.isEmpty())
            return null;
        List<Transaction> transactions = new ArrayList<>();
        for (Account account : incomeAccounts) {
            BigDecimal balance = period.getAmountHolders().get(account.getId()).getBalance();
            totalAmount = totalAmount.add(balance);
            transactions.add(createTransaction(account, balance, false, "结转收入"));
        }
        // create transaction for "本年利润"
        createTransaction(profitAccount, totalAmount, true, "结转收入");
        return new JournalEntryView(createJournalEntry(period.getAccountSetId(), "结转收入"), transactions);
    }

    // 结转费用和损失账户
    public JournalEntryView transferExpenseAndLossAccounts(List<Account> expenseAndLossAccounts,
            AccountingPeriod period, Account profitAccount) {
        if (expenseAndLossAccounts.isEmpty())
            return null;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Transaction> transactions = new ArrayList<>();
        for (Account account : expenseAndLossAccounts) {
            BigDecimal balance = period.getAmountHolders().get(account.getId()).getBalance();
            totalAmount = totalAmount.add(balance);
            transactions.add(createTransaction(account, balance, false, "结转费用和损失"));
        }
        createTransaction(profitAccount, totalAmount, true, "结转费用和损失");
        return new JournalEntryView(createJournalEntry(period.getAccountSetId(), "结转费用和损失"), transactions);
    }

    // 结转以前年度损益调整账户
    public JournalEntryView transferPriorYearAdjustmentAccounts(List<Account> priorYearAdjustmentAccounts,
            AccountingPeriod period, Account profitAccount) {
        if (priorYearAdjustmentAccounts.isEmpty())
            return null;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Transaction> transactions = new ArrayList<>();
        for (Account account : priorYearAdjustmentAccounts) {
            BigDecimal balance = period.getAmountHolders().get(account.getId()).getBalance();
            totalAmount = totalAmount.add(balance);
            transactions.add(createTransaction(account, balance, false, "结转以前年度损益调整"));
        }
        createTransaction(profitAccount, totalAmount, true, "结转以前年度损益调整");
        return new JournalEntryView(createJournalEntry(period.getAccountSetId(), "结转以前年度损益调整"), transactions);
    }



}