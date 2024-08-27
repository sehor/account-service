package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.repository.EntryMongoRepository;
import com.skyflytech.accountservice.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    public JournalEntryService(EntryMongoRepository journalEntryRepository, TransactionService transactionService) {
        this.journalEntryRepository = journalEntryRepository;
        this.transactionService = transactionService;
    }


    @Transactional(rollbackFor = Exception.class)
    public JournalEntryView processJournalEntryView(JournalEntryView journalEntryView) {
        JournalEntry journalEntry = journalEntryView.getJournalEntry();
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

        transactionService.deleteByJourneyEntry(journalEntry);
        journalEntryRepository.delete(journalEntry);
    }
    public List<JournalEntry> getAllJournalEntries() {
        return journalEntryRepository.findAll();
    }

    public JournalEntry getJournalEntryById(String id) {
        return journalEntryRepository.findById(id).orElseThrow(()->new NoSuchElementException("no such JournalEntry founded: "+id));
    }


    public JournalEntry creatAndSaveJournalEntry(JournalEntry entry){
        if(!Utils.isNotEmpty(entry.getId())){
            entry.setId(UUID.randomUUID().toString());
            entry.setCreatedDate(LocalDate.now());
        }
        entry.setModifiedDate(LocalDate.now());
        return journalEntryRepository.save(entry);
    }

    private void setNewJournalEntryBeforeSave(JournalEntry journalEntry) {
        LocalDate now=LocalDate.now();
        journalEntry.setId(UUID.randomUUID().toString());
        journalEntry.setCreatedDate(now);
        journalEntry.setModifiedDate(now);
    }

    public void deleteAllEntry() {
        for(JournalEntry entry:getAllJournalEntries()){
             deleteEntry(entry);
        }
    }
}
