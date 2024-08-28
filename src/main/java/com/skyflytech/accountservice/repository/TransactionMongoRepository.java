package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.Transaction;
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
}
