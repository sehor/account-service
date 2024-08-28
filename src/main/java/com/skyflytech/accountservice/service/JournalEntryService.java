package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.repository.EntryMongoRepository;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.utils.Utils;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public JournalEntryService(EntryMongoRepository journalEntryRepository, TransactionService transactionService,CurrentAccountSetIdHolder currentAccountSetIdHolder) {
        this.journalEntryRepository = journalEntryRepository;
        this.transactionService = transactionService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
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
