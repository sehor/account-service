package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntryView;
import com.skyflytech.accountservice.repository.TransactionMongoRepository;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.utils.Utils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
@Service
public class ProcessJournalEntry {
    private final MongoTemplate mongoTemplate;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final AccountingPeriodService accountingPeriodService;
    private final TransactionMongoRepository transactionRepository;

    public ProcessJournalEntry(MongoTemplate mongoTemplate,
                               CurrentAccountSetIdHolder currentAccountSetIdHolder,
                               AccountingPeriodService accountingPeriodService, TransactionMongoRepository transactionRepository) {
        this.mongoTemplate = mongoTemplate;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.accountingPeriodService = accountingPeriodService;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public JournalEntryView processJournalEntryView(JournalEntryView journalEntryView) {
        JournalEntry journalEntry = journalEntryView.getJournalEntry();
        List<Transaction> transactions_new = new ArrayList<>();

        // 检查accountSetId
        if (journalEntry.getAccountSetId() == null
                || !journalEntry.getAccountSetId().equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match.");
        }

        LocalDate now = LocalDate.now();
        //如果ModifedDate存在，检查modifiedDate是否超过当前日期
        if (journalEntry.getModifiedDate() != null && journalEntry.getModifiedDate().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The modifiedDate is after current date.");
        }
        //检查是否借贷平衡
        validateBalancedDebitsAndCredits(journalEntryView.getTransactions());

        // 更新journalEntry Id
        if (!Utils.isNotEmpty(journalEntry.getId())) {
            journalEntry.setCreatedDate(now);
            if(journalEntry.getModifiedDate() == null){
                journalEntry.setModifiedDate(now);
            }
            // generate new id
            journalEntry.setId(UUID.randomUUID().toString());
        }

        //检查最后的accountingPeriod是否在journalEntry modifiedDate 之后
        AccountingPeriod last_accountingPeriod = accountingPeriodService.findLastAccountingPeriodByAccountSetId(journalEntry.getAccountSetId());
        if (last_accountingPeriod.getEndDate().isBefore(journalEntry.getModifiedDate())) {
            // 创建缺失accountingPeriods
            accountingPeriodService.createAccountingPeriodsFromStartPeriodToEndDate(last_accountingPeriod, journalEntry.getModifiedDate());
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
            transactions_new.add(transaction);
        }
       transactions_new= transactionRepository.saveAll(transactions_new);


        // 删除更新分录后那些已经不存在的entry transactionsIds

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

    //
    private void validateBalancedDebitsAndCredits(List<Transaction> transactions) {
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            totalDebits = totalDebits.add(transaction.getDebit());
            totalCredits = totalCredits.add(transaction.getCredit());
        }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Debits and credits are not balanced. Total Debits: " + totalDebits + 
                ", Total Credits: " + totalCredits);
        }
    }

}
