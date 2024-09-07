package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends MongoRepository<AccountingPeriod, String> {

    // 查找特定月份的会计期间 and the endDate is greater than the date
    List<AccountingPeriod> findByAccountSetIdAndEndDateGreaterThanEqual(String accountSetId, LocalDate date);

    Optional<AccountingPeriod> findByAccountSetIdAndStartDate(String accountSetId, LocalDate periodStartDate);

    List<AccountingPeriod> findByAccountSetId(String accountSetId);

    void deleteByAccountSetId(String accountSetId);
}