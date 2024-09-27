package com.skyflytech.accountservice.core.journalEntry.service;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountAmountHolder;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import com.skyflytech.accountservice.core.account.model.AccountingDirection;
import com.skyflytech.accountservice.core.journalEntry.model.AutoEntryTemplate;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntryView;
import com.skyflytech.accountservice.core.report.model.AccountingOperation;
import com.skyflytech.accountservice.core.journalEntry.repository.EntryMongoRepository;
import com.skyflytech.accountservice.security.model.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.core.transaction.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Author pzr
 * @date:2024-08-15-7:07
 * @Description:
 **/
@Service
@Transactional(rollbackFor = Exception.class)
public class JournalEntryService {

    private final EntryMongoRepository journalEntryRepository;
    private final TransactionService transactionService;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final MongoTemplate mongoTemplate;
    private final ProcessJournalEntry processJournalEntry;
    @Autowired
    public JournalEntryService(EntryMongoRepository journalEntryRepository, 
                               TransactionService transactionService,
                               CurrentAccountSetIdHolder currentAccountSetIdHolder,
                               MongoTemplate mongoTemplate,
                               ProcessJournalEntry processJournalEntry) {
        this.journalEntryRepository = journalEntryRepository;
        this.transactionService = transactionService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.mongoTemplate = mongoTemplate;
        this.processJournalEntry = processJournalEntry;
    }

    
    @Transactional
    public void deleteEntry(JournalEntry journalEntry) {
        checkAccountSetId(journalEntry);
        transactionService.deleteByJourneyEntry(journalEntry);
        journalEntryRepository.delete(journalEntry);
    }

    public List<JournalEntry> getAllJournalEntries(String accountSetId) {
        return journalEntryRepository.findAllByAccountSetId(accountSetId);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<JournalEntryView> getJournalEntriesByPeriod(String accountSetId, LocalDate startDate, LocalDate endDate) {
        // 获取指定日期范围内的所有记账凭证
        List<JournalEntry> journalEntries = journalEntryRepository.findByAccountSetIdAndModifiedDateBetween(accountSetId, startDate, endDate);
        
        // 收集所有交易ID
        Set<String> allTransactionIds = journalEntries.stream()
                .flatMap(je -> je.getTransactionIds().stream())
                .collect(Collectors.toSet());

        // 批量查询所有相关交易
        Query query = new Query(Criteria.where("_id").in(allTransactionIds));
        List<Transaction> allTransactions = mongoTemplate.find(query, Transaction.class);

        // 创建交易ID到交易对象的映射
        Map<String, Transaction> transactionMap = allTransactions.stream()
                .collect(Collectors.toMap(Transaction::getId, t -> t));

        // 构建JournalEntryView列表
        return journalEntries.stream()
                .map(journalEntry -> {
                    List<Transaction> transactions = journalEntry.getTransactionIds().stream()
                            .map(transactionMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    return new JournalEntryView(journalEntry, transactions);
                })
                .collect(Collectors.toList());
    }

    //获取某个会计期间的所有凭证
    public List<JournalEntry> getJournalEntriesByPeriod(String accountSetId, AccountingPeriod accountingPeriod) {
        return journalEntryRepository.findByAccountSetIdAndModifiedDateBetween(accountSetId, accountingPeriod.getStartDate(), accountingPeriod.getEndDate());
    }

    @Transactional
    public void deleteJournalEntriesByAccountSetId(String accountSetId) {
        journalEntryRepository.deleteByAccountSetId(accountSetId);
    }


// 自动生成凭证
public void autoGenerateJournalEntry(AutoEntryTemplate template,LocalDate createdDate) {
    //find accountPeriod ,startDate<=createdDate<=endDate
    Query query = new Query(Criteria.where("startDate").lte(createdDate).and("endDate").gte(createdDate));
    AccountingPeriod accountPeriod = mongoTemplate.findOne(query, AccountingPeriod.class);  
    //生成借方transactions
    List<Transaction> transactions = generateTransactionsByOperations(template, accountPeriod);

    //生成journalEntry
    JournalEntry journalEntry = new JournalEntry();
    journalEntry.setAccountSetId(template.getAccountSetId());
    journalEntry.setCreatedDate(createdDate);
    journalEntry.setModifiedDate(createdDate);
    //生成journalEntryview
    JournalEntryView journalEntryView = new JournalEntryView(journalEntry, transactions);
    processJournalEntry.processJournalEntryView(journalEntryView);
}

 //找到某个会计期间的最大凭证号
 public int findMaxVoucherNum(String accountSetId, LocalDate date) {
    Query query = new Query(Criteria.where("accountSetId").is(accountSetId).and("modifiedDate").lte(date));
    query.with(Sort.by(Sort.Direction.ASC, "voucherNum"));
    query.limit(1);
    JournalEntry journalEntry = mongoTemplate.findOne(query, JournalEntry.class);
    if(journalEntry==null){
        return 0;
    }
    return journalEntry.getVoucherNum();
}

//按时间顺序重新排序并重设凭证号
public void reorderVoucherNum(String accountSetId, LocalDate startDate,LocalDate endDate) {
    Query query = new Query(Criteria.where("accountSetId").is(accountSetId).and("modifiedDate").lte(endDate).gte(startDate));
    query.with(Sort.by(Sort.Direction.ASC, "modifiedDate"));
    List<JournalEntry> journalEntries = mongoTemplate.find(query, JournalEntry.class);
    for (int i = 0; i < journalEntries.size(); i++) {
        journalEntries.get(i).setVoucherNum(i + 1);
    }
    journalEntryRepository.saveAll(journalEntries);
} 

//某个会计期间凭证号是否连续
public boolean isVoucherNumContinuous(AccountingPeriod accountingPeriod) {
    String accountSetId=accountingPeriod.getAccountSetId();
    LocalDate startDate=accountingPeriod.getStartDate();
    LocalDate endDate=accountingPeriod.getEndDate();
    Query query = new Query(Criteria.where("accountSetId").is(accountSetId).and("modifiedDate").lte(endDate).gte(startDate));
    query.with(Sort.by(Sort.Direction.ASC, "voucherNum"));
    List<JournalEntry> journalEntries = mongoTemplate.find(query, JournalEntry.class);
    for (int i = 0; i < journalEntries.size() - 1; i++) {
        if (journalEntries.get(i).getVoucherNum() + 1 != journalEntries.get(i + 1).getVoucherNum()) {
            return false;
        }
    }
    return true;
}   




    private void checkAccountSetId(JournalEntry journalEntry){
        String accountSetId = currentAccountSetIdHolder.getCurrentAccountSetId();
        if(journalEntry.getAccountSetId()==null){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is null.");
        }
        if(!journalEntry.getAccountSetId().equals(accountSetId)){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match."); 
        }
    }

    private List<Transaction> generateTransactionsByOperations(AutoEntryTemplate  template, AccountingPeriod accountPeriod) {
        List<AccountingOperation> operations=template.getOperations();
        List<Transaction> transactions = new ArrayList<>();
        BigDecimal total=BigDecimal.ZERO;
        for (AccountingOperation operation : operations) {
            Transaction transaction = new Transaction();
            transaction.setAccountId(operation.getAccountId());
            AccountAmountHolder amountHolder=accountPeriod.getAmountHolders().get(operation.getAccountId());
            BigDecimal amount=BigDecimal.ZERO;
            switch(operation.getDataType()){
                case DEBIT_TOTAL:
                    amount=amountHolder.getTotalDebit();
                    break;
                case CREDIT_TOTAL:
                    amount=amountHolder.getTotalCredit();
                case BALANCE:
                    amount=amountHolder.getBalance();
                    break;
            }
            if(template.getOtherSide()==AccountingDirection.DEBIT){
                transaction.setCredit(amount);
            }else{
                transaction.setDebit(amount);
            }
            total=total.add(amount);
            transaction.setCreatedDate(accountPeriod.getEndDate());
            transaction.setModifiedDate(accountPeriod.getEndDate());
            transactions.add(transaction);
        }
            Transaction transaction=new Transaction();
            transaction.setAccountId(template.getOtherSideAccountId());
        if(template.getOtherSide()==AccountingDirection.DEBIT){
            transaction.setDebit(total);
        }else{
            transaction.setCredit(total);
        }
        transaction.setCreatedDate(accountPeriod.getEndDate());
        transaction.setModifiedDate(accountPeriod.getEndDate());
        transactions.add(transaction);
        return transactions;
    }
}
