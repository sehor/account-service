package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.repository.EntryMongoRepository;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    public JournalEntryService(EntryMongoRepository journalEntryRepository, 
                               TransactionService transactionService,
                               CurrentAccountSetIdHolder currentAccountSetIdHolder,
                               MongoTemplate mongoTemplate) {
        this.journalEntryRepository = journalEntryRepository;
        this.transactionService = transactionService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.mongoTemplate = mongoTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public JournalEntryView processJournalEntryView(JournalEntryView journalEntryView) {
        JournalEntry journalEntry = journalEntryView.getJournalEntry();
        checkAccountSetId(journalEntry);
        List<Transaction> transactions_new = new ArrayList<>();

        //更新journalEntry Id
        if(!Utils.isNotEmpty(journalEntry.getId())){
            setNewJournalEntryBeforeSave(journalEntry);
        }

        for (Transaction transaction : journalEntryView.getTransactions()) {
            if (!Utils.isNotEmpty(transaction.getId())) {
                // 如果Transaction的ID为空，表示需要新建Transaction
                transaction.setCreatedDate(journalEntry.getCreatedDate());
                transaction.setModifiedDate(journalEntry.getModifiedDate());
                transaction.setAccountSetId(journalEntry.getAccountSetId());
            } else {
                // 如果Transaction的ID不为空，表示需要更新Transaction
                transaction.setModifiedDate(journalEntry.getModifiedDate());
            }
             transactions_new.add(transactionService.saveTransaction(transaction));
        }

        //删除更新分录后那些已经不存在的entry transactionsIds
        Set<String> transactionIds_new = transactions_new.stream().map(Transaction::getId).collect(Collectors.toSet());

        if(Utils.isNotNullOrEmpty(journalEntry.getTransactionIds())){
            Set<String> toDeleteTransactionIds = journalEntry.getTransactionIds().stream().filter(i -> !transactionIds_new.contains(i)).collect(Collectors.toSet());
            for(String transactionId: toDeleteTransactionIds){
                transactionService.deleteTransaction(transactionId);
            }
        }

        // 更新JournalEntry的transactionIds
        journalEntry.setTransactionIds(transactionIds_new);
        journalEntryRepository.save(journalEntry);
        // 返回更新后的JournalEntryView
        return new JournalEntryView(journalEntry,transactions_new);
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


    private void setNewJournalEntryBeforeSave(JournalEntry journalEntry) {
        LocalDate now=LocalDate.now();
        journalEntry.setId(UUID.randomUUID().toString());
        journalEntry.setCreatedDate(now);
        journalEntry.setModifiedDate(now);
    }

    public void deleteAllEntry(String accountSetId) {
        for(JournalEntry entry:getAllJournalEntries(accountSetId)){
             deleteEntry(entry);
        }
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
}
