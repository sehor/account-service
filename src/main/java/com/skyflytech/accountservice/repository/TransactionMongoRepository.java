package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.Transaction;
import com.skyflytech.accountservice.domain.journalEntry.JournalEntry;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * @Author pzr
 * @date:2024-08-15-7:11
 * @Description:
 **/
@Repository
public interface TransactionMongoRepository extends MongoRepository<Transaction,String> {

    List<Transaction> findByAccountId(String accountId);

    List<Transaction> findDebitsAndCreditsByAccountIdAndModifiedDateLessThan(String accountId, LocalDate modifiedDate);
      @Query("{'accountSetId': ?0, 'date': {$gte: ?1, $lte: ?2}}")
    List<JournalEntry> findByAccountSetIdAndDateRange(String accountSetId, LocalDate startDate, LocalDate endDate);
}
