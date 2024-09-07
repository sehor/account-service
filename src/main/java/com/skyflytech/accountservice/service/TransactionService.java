package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.repository.TransactionMongoRepository;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
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
 * @date:2024-08-15-7:10
 * @Description:
 **/
@Service
public class TransactionService {
    private final TransactionMongoRepository transactionRepository;
    private final MongoOperations mongoOperations;
    private final AccountService accountService;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;

    @Autowired
    public TransactionService(TransactionMongoRepository transactionRepository, MongoOperations mongoOperations,CurrentAccountSetIdHolder currentAccountSetIdHolder,AccountService accountService) {  

        this.transactionRepository = transactionRepository;
        this.mongoOperations = mongoOperations;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
        this.accountService = accountService;
    }

    public List<Transaction> getAllTransactions(String accountSetId) {
        return mongoOperations.find(Query.query(Criteria.where("accountSetId").is(accountSetId)), Transaction.class);
    }


    // Fuzzy search transactions by description
    public List<Transaction> searchTransactions(String query,String accountSetId) {
        return mongoOperations.find(Query.query(Criteria.where("accountSetId").is(accountSetId).and("description").regex(query, "i")), Transaction.class);
    }

    public List<Transaction> findByAccountId(String accountId) {
        return transactionRepository.findByAccountId(accountId);
    }

    public Page<Transaction> findTransactionsByAccountAndPeriod(String accountId, LocalDate startDate, LocalDate endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        
        // 获取所有叶子子账户（包括当前账户，如果它是叶子账户）
        List<Account> leafAccounts = accountService.getLeafSubAccounts(accountId);
        List<String> leafAccountIds = leafAccounts.stream()
                .map(Account::getId)
                .collect(Collectors.toList());

        // 使用MongoDB的聚合操作来查询多个账户的交易并进行分页
        Criteria criteria = Criteria.where("accountId").in(leafAccountIds)
                .and("modifiedDate").gte(startDate).lte(endDate);

        Aggregation aggregation = Aggregation.newAggregation(
            Aggregation.match(criteria),
            Aggregation.sort(Sort.Direction.DESC, "modifiedDate"),
            Aggregation.skip((long) pageable.getPageNumber() * pageable.getPageSize()),
            Aggregation.limit(pageable.getPageSize())
        );

        List<Transaction> transactions = mongoOperations.aggregate(aggregation, Transaction.class, Transaction.class).getMappedResults();

        // 获取总数以计算总页数
        long total = mongoOperations.count(Query.query(criteria), Transaction.class);

        return new PageImpl<>(transactions, pageable, total);
    }
    public Map.Entry<BigDecimal, BigDecimal> calculateTotalDebitAndCredit(String accountId, LocalDate modifiedDate) {
        // Fetch only debit and credit fields
        List<Transaction> transactions = transactionRepository.findDebitsAndCreditsByAccountIdAndModifiedDateLessThan(accountId, modifiedDate);

        // Calculate total debit and total credit manually
        BigDecimal totalDebit = transactions.stream()
                .map(Transaction::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = transactions.stream()
                .map(Transaction::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Return the totals as a Map.Entry
        return new AbstractMap.SimpleEntry<>(totalDebit, totalCredit);
    }


        @Transactional
    protected Transaction saveTransaction(Transaction transaction) {
        checkAccountSetId(transaction);
        return transactionRepository.save(transaction);
    }

    protected void deleteByJourneyEntry(JournalEntry entry) {
        //check entry's accountSetId
        if(entry.getAccountSetId()==null){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is null.");
        }
        if(!entry.getAccountSetId().equals(currentAccountSetIdHolder.getCurrentAccountSetId())){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match."); 
        }   
        Set<String> transactionIds = entry.getTransactionIds();
        mongoOperations.findAllAndRemove(new Query(Criteria.where("id").in(transactionIds)),
                                           Transaction.class);
    }

    protected void deleteTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        checkAccountSetId(transaction);
        transactionRepository.delete(transaction);
    }
    

        private void checkAccountSetId(Transaction transaction){
        if(transaction==null) throw new NoSuchElementException("no such transaction");
        String accountSetId = currentAccountSetIdHolder.getCurrentAccountSetId();
        if(transaction.getAccountSetId()==null){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is null.");
        }
        if(!transaction.getAccountSetId().equals(accountSetId)){
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "The accountSetId is not match."); 
        }
    }

    @Transactional
    public void deleteTransactionsByAccountSetId(String accountSetId) {
        transactionRepository.deleteByAccountSetId(accountSetId);
    }
}