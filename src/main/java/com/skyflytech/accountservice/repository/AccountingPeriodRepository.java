package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends MongoRepository<AccountingPeriod, String> {

    // 查找特定月份的会计期间
    Optional<AccountingPeriod> findByAccountSetIdAndStartDate(String accountSetId, LocalDate startDate);

}