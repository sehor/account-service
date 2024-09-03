package com.skyflytech.accountservice.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.utils.Utils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
@Service
public class ProcessJournalEntry {
    private final MongoTemplate mongoTemplate;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final AccountingPeriodService accountingPeriodService;

    public ProcessJournalEntry(MongoTemplate mongoTemplate, 
                               CurrentAccountSetIdHolder currentAccountSetIdHolder, 
                               AccountingPeriodService accountingPeriodService) {
        this.mongoTemplate = mongoTemplate;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.accountingPeriodService = accountingPeriodService;
    }

    @Transactional
    public JournalEntryView processJournalEntryView(JournalEntryView journalEntryView) {
        JournalEntry journalEntry = journalEntryView.getJournalEntry();

        // 检查accountSetId
        if (journalEntry.getAccountSetId() == null
                || !journalEntry.getAccountSetId().equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match.");
        }

        LocalDate now = LocalDate.now();
        // 更新journalEntry Id
        if (!Utils.isNotEmpty(journalEntry.getId())) {
            journalEntry.setCreatedDate(now);
            journalEntry.setModifiedDate(now);
            // generate new id
            journalEntry.setId(UUID.randomUUID().toString());
        }

        for (Transaction transaction : journalEntryView.getTransactions()) {
            if (!Utils.isNotEmpty(transaction.getId())) {
                // 如果Transaction的ID为空，表示需要新建Transaction
                transaction.setCreatedDate(journalEntry.getCreatedDate());
                transaction.setModifiedDate(journalEntry.getModifiedDate());
                transaction.setAccountSetId(journalEntry.getAccountSetId());
                accountingPeriodService.updateAccountingPeriodsWhenTransactionAmountChange(transaction, transaction.getDebit(), transaction.getCredit());
            } else {
                // 如果Transaction的ID不为空，表示需要更新Transaction
                transaction.setModifiedDate(journalEntry.getModifiedDate());
                Transaction oldTransaction = mongoTemplate.findById(transaction.getId(), Transaction.class);
                if (oldTransaction == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
                }
                BigDecimal debitChange = transaction.getDebit().subtract(oldTransaction.getDebit());
                BigDecimal creditChange = transaction.getCredit().subtract(oldTransaction.getCredit());
                if (debitChange.compareTo(BigDecimal.ZERO) != 0 || creditChange.compareTo(BigDecimal.ZERO) != 0) {
                    accountingPeriodService.updateAccountingPeriodsWhenTransactionAmountChange(transaction, debitChange, creditChange);
                }
            }
            mongoTemplate.save(transaction);
        }

        // 删除更新分录后那些已经不存在的entry transactionsIds
        List<Transaction> transactions_new = new ArrayList<>();
        Set<String> transactionIds_new = transactions_new.stream().map(Transaction::getId).collect(Collectors.toSet());

        if (Utils.isNotNullOrEmpty(journalEntry.getTransactionIds())) {
            Set<String> toDeleteTransactionIds = journalEntry.getTransactionIds().stream()
                    .filter(i -> !transactionIds_new.contains(i)).collect(Collectors.toSet());
            for (String transactionId : toDeleteTransactionIds) {
                Transaction transaction = mongoTemplate.findById(transactionId, Transaction.class);
                if (transaction == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
                }
               accountingPeriodService.updateAccountingPeriodsWhenTransactionAmountChange(transaction, transaction.getDebit().negate(),
                        transaction.getCredit().negate());
                mongoTemplate.remove(transaction);
            }
        }

        // 更新JournalEntry的transactionIds
        journalEntry.setTransactionIds(transactionIds_new);
        mongoTemplate.save(journalEntry);
        // 返回更新后的JournalEntryView
        return new JournalEntryView(journalEntry, transactions_new);

    }

}
