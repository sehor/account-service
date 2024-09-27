package com.skyflytech.accountservice.core.accountingPeriod.service;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountAmountHolder;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import com.skyflytech.accountservice.core.account.model.Account;
import com.skyflytech.accountservice.core.account.model.AccountType;
import com.skyflytech.accountservice.core.account.model.TransferAccountType;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntryView;
import com.skyflytech.accountservice.global.GlobalConst;
import com.skyflytech.accountservice.core.accountingPeriod.repository.AccountingPeriodRepository;

import com.skyflytech.accountservice.core.journalEntry.service.JournalEntryService;
import com.skyflytech.accountservice.core.journalEntry.service.ProcessJournalEntry;
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
    private final JournalEntryService journalEntryService;

    @Autowired
    public CloseAccountingPeriodService(AccountingPeriodRepository accountingPeriodRepository,
            ProcessJournalEntry processJournalEntryService,
            AccountingPeriodService accountingPeriodService,
            MongoTemplate mongoTemplate,
            JournalEntryService journalEntryService
            ) {
        this.accountingPeriodRepository = accountingPeriodRepository;
        this.processJournalEntryService = processJournalEntryService;
        this.journalEntryService = journalEntryService;
        this.mongoTemplate = mongoTemplate;
        this.accountingPeriodService = accountingPeriodService;
    }

    @Transactional
    public AccountingPeriod closeAccountingPeriod(String accountingPeriodId) {
        AccountingPeriod currentPeriod = accountingPeriodRepository.findById(accountingPeriodId)
                .orElseThrow(() -> new RuntimeException("会计期间不存在"));

            // 检查是否存在下一个会计期间,如果不存在则创建一个新的会计期间 
        AccountingPeriod newPeriod = accountingPeriodService.findNextPeriod(currentPeriod);
        if (newPeriod == null) {
            newPeriod = accountingPeriodService.createNextAccountingPeriod(currentPeriod);
        }

        return newPeriod;
    }

    @Transactional
    public void transferPeriod(String periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("会计期间不存在"));
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
    //会计期间平衡检查
    public BigDecimal checkAccountingEquation(String periodId) {
        
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("会计期间不存在"));
        String accountSetId = period.getAccountSetId();
        //map<accountId,balance>
        Map<String, BigDecimal> balances = new HashMap<>();
        for(Map.Entry<String, AccountAmountHolder> entry:period.getAmountHolders().entrySet()){
            balances.put(entry.getKey(), entry.getValue().getBalance());
        }
        //find all accounts by accountSetId
        List<Account> accounts = mongoTemplate.find(Query.query(Criteria.where("accountSetId").is(accountSetId)), Account.class);
      
        
        BigDecimal assets = BigDecimal.ZERO;
        BigDecimal liabilitiesAndEquity = BigDecimal.ZERO;
        BigDecimal equity = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;

      for(Account account:accounts){
         if(account.getCode().startsWith("1")&&account.getLevel()==1){
            assets = assets.add(balances.get(account.getId()));
         }else if(account.getCode().startsWith("2")&&account.getLevel()==1){
            liabilitiesAndEquity = liabilitiesAndEquity.add(balances.get(account.getId()));
         }else if(account.getCode().startsWith("3")&&account.getLevel()==1){
            equity = equity.add(balances.get(account.getId()));
         }else if(account.getCode().startsWith("4")&&account.getLevel()==1){
            cost = cost.add(balances.get(account.getId()));
         }
        }

        BigDecimal difference = assets.add(cost).subtract(liabilitiesAndEquity.add(equity));
         return difference;
    }

    //检查需要结转的账户余额是否都是0
    public boolean checkTransferAccountBalancesIsZero(String periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("会计期间不存在"));
        List<Account> accounts = findAllLeafAccountsForClosingPeriod(period.getAccountSetId());
        //list<id>
        List<String> accountIds = accounts.stream().map(Account::getId).toList();
        for(String id:accountIds){
            BigDecimal balance = period.getAmountHolders().get(id).getBalance();
            if(balance.compareTo(BigDecimal.ZERO)!=0){
                return false;
            }
        }
        return true;
    }

    //检查凭证号是否连续
    public boolean checkVoucherWordIsContinuous(String periodId){
        AccountingPeriod period = accountingPeriodRepository.findById(periodId).orElseThrow(() -> new RuntimeException("会计期间不存在"));
        return journalEntryService.isVoucherNumContinuous(period);
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
     List<Account> findAllLeafAccountsForClosingPeriod(String accountSetId) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS.values().stream()
                .flatMap(List::stream).toList();
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).collect(Collectors.toList());
        List<Account> accounts = mongoTemplate.find(Query.query(Criteria.where("accountSetId").is(accountSetId)
                .and("type").in(accountTypeValues)), Account.class);
        List<String> parentIds = accounts.stream().map(Account::getParentId).toList();
        return accounts.stream().filter(account -> !parentIds.contains(account.getId())).collect(Collectors.toList());
    }

    // 找到期间收入账户
    private List<Account> findIncomeAccounts(List<Account> accounts) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS.get(TransferAccountType.INCOME);
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).toList();
        return accounts.stream().filter(account -> accountTypeValues.contains(account.getType().name()))
                .collect(Collectors.toList());
    }

    // 找到期间费用和损失账户
    private List<Account> findExpenseAndLossAccounts(List<Account> accounts) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS.get(TransferAccountType.ALL_EXPENSE_TYPES);
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).toList();
        return accounts.stream().filter(account -> accountTypeValues.contains(account.getType().name()))
                .collect(Collectors.toList());
    }

    // 找到以前年度损益调整账户
    private List<Account> findPriorYearAdjustmentAccounts(List<Account> accounts) {
        List<AccountType> accountTypes = GlobalConst.AUTO_TRANSFER_ACCOUNTS
                .get(TransferAccountType.PRIOR_YEAR_ADJUSTMENT);
        List<String> accountTypeValues = accountTypes.stream().map(AccountType::name).collect(Collectors.toList());
        return accounts.stream().filter(account -> accountTypeValues.contains(account.getType().name()))
                .collect(Collectors.toList());
    }

    // 结转收入账户
    private JournalEntryView transferIncomeAccounts(List<Account> incomeAccounts, AccountingPeriod period,
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
    private JournalEntryView transferExpenseAndLossAccounts(List<Account> expenseAndLossAccounts,
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
    private JournalEntryView transferPriorYearAdjustmentAccounts(List<Account> priorYearAdjustmentAccounts,
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

    private Account findAccountByCode(List<Account> accounts, String code) {
        return accounts.stream()
                .filter(account -> code.equals(account.getCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("not found account,the code is: " + code));
    }



}