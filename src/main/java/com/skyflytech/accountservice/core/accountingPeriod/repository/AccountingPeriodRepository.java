package com.skyflytech.accountservice.core.accountingPeriod.repository;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountingPeriodRepository extends MongoRepository<AccountingPeriod, String> {
}