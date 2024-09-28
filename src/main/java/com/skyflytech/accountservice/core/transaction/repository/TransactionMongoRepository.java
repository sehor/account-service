package com.skyflytech.accountservice.core.transaction.repository;

import com.skyflytech.accountservice.core.transaction.model.Transaction;
import org.springframework.data.mongodb.repository.MongoRepository;
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

    List<Transaction> findByAccountSetIdAndModifiedDateBetween(String accountSetId, LocalDate startDate, LocalDate endDate);
}
