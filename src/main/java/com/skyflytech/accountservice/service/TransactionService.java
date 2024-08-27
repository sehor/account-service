package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;
import com.skyflytech.accountservice.repository.TransactionMongoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    @Autowired
    private AccountService accountService;

    @Autowired
    public TransactionService(TransactionMongoRepository transactionRepository, MongoOperations mongoOperations) {
        this.transactionRepository = transactionRepository;
        this.mongoOperations = mongoOperations;
    }

    public List<Transaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public List<Transaction> findByPeriod(String accountId,LocalDateTime start,LocalDateTime end){
       return mongoOperations.find(Query.query(Criteria.where("accountId").is(accountId).and("modifiedDate").gte(start).lte(end)),
                Transaction.class);
    }

    public double getPreviousDebit(Account account, LocalDateTime date){
        //todo
        return 0;
    }


    // Fuzzy search transactions by description
    public List<Transaction> searchTransactions(String query) {
        return transactionRepository.findByDescriptionContainingIgnoreCase(query);
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
        return transactionRepository.save(transaction);
    }

    protected void deleteByJourneyEntry(JournalEntry entry) {

        Set<String> transactionIds = entry.getTransactionIds();
        mongoOperations.findAllAndRemove(new Query(Criteria.where("id").in(transactionIds)),
                                           Transaction.class);
    }

    @Transactional
    protected void updateTransactionsByAccountId(String accountId, String new_accountId) {
        mongoOperations.updateMulti(new Query(Criteria.where("accountId").is(accountId)),
                new Update().set("accountId", new_accountId),
                Transaction.class);
    }

    protected void deleteTransaction(String transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
        if(transaction==null){
            return;
        }
        transactionRepository.delete(transaction);
    }


}