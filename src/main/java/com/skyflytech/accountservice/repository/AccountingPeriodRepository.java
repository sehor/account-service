package com.skyflytech.accountservice.repository;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.account.Account;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends MongoRepository<AccountingPeriod, String> {

    // 根据账套ID查找所有会计期间
    List<AccountingPeriod> findByAccountSetId(String accountSetId);

    // 根据账套ID和是否关闭状态查找会计期间
    List<AccountingPeriod> findByAccountSetIdAndIsClosed(String accountSetId, boolean isClosed);

    // 查找特定账套的最新会计期间
    Optional<AccountingPeriod> findTopByAccountSetIdOrderByEndDateDesc(String accountSetId);

    // 查找特定日期范围内的会计期间
    List<AccountingPeriod> findByAccountSetIdAndStartDateGreaterThanEqualAndEndDateLessThanEqual(
            String accountSetId, LocalDate startDate, LocalDate endDate);

    // 查找特定名称的会计期间
    Optional<AccountingPeriod> findByAccountSetIdAndName(String accountSetId, String name);

    // 检查是否存在重叠的会计期间
    boolean existsByAccountSetIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            String accountSetId, LocalDate endDate, LocalDate startDate);

    // 查找特定月份的会计期间
    Optional<AccountingPeriod> findByAccountSetIdAndStartDate(String accountSetId, LocalDate startDate);

    // 查找最近的未关闭的会计期间
    Optional<AccountingPeriod> findFirstByAccountSetIdAndIsClosedOrderByEndDateDesc(String accountSetId, boolean isClosed);

    // 检查是否存在下一个月的会计期间
    boolean existsByAccountSetIdAndStartDate(String accountSetId, LocalDate startDate);

    Optional<AccountingPeriod> findByAccountSetIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(String accountSetId,
            LocalDate date, LocalDate date2);
}