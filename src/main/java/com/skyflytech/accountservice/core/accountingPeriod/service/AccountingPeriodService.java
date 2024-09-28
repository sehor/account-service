package com.skyflytech.accountservice.core.accountingPeriod.service;

import com.skyflytech.accountservice.core.accountSet.model.AccountSet;
import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * @Author pzr
 * @date:2024-09-27-18:22
 * @Description:
 **/
public interface AccountingPeriodService {

    /**
     * 创建初始会计期间
     * @param accountSet 账套
     * @param startMonth 开始月份
     * @return 创建的会计期间
     */
    AccountingPeriod createInitialPeriod(AccountSet accountSet, YearMonth startMonth);

    /**
     * 当交易金额变化时更新会计期间
     * @param transaction 交易
     * @param debitChange 借方变化
     * @param creditChange 贷方变化
     */
    void updateAccountingPeriodsWhenTransactionAmountChange(Transaction transaction, BigDecimal debitChange, BigDecimal creditChange);

    /**
     * 根据账套ID删除会计期间
     * @param accountSetId 账套ID
     */
    void deleteAccountPeriodsByAccountSetId(String accountSetId);

    /**
     * 从起始会计期间创建到结束日期的会计期间
     * @param startPeriod 起始会计期间
     * @param endDate 结束日期
     * @return 创建的会计期间列表
     */
    List<AccountingPeriod> createAccountingPeriodsFromStartPeriodToEndDate(AccountingPeriod startPeriod, LocalDate endDate);

    /**
     * 根据账套ID查找最后一个会计期间
     * @param accountSetId 账套ID
     * @return 最后一个会计期间
     */
    AccountingPeriod findLastAccountingPeriodByAccountSetId(String accountSetId);

    /**
     * 反向关闭会计期间到指定日期
     * @param accountingPeriod 会计期间
     * @param startDate 开始日期
     */
    void antiCloseAccountingPeriod(AccountingPeriod accountingPeriod, LocalDate startDate);

    /**
     * 查找当前会计期间
     * @param accountSetId 账套ID
     * @return 当前会计期间
     */
    AccountingPeriod findCurrentAccountingPeriod(String accountSetId);

    /**
     * 创建初始会计期间
     * @param accountSet 账套
     * @return 创建的初始会计期间
     */
    AccountingPeriod createInitialAccountingPeriod(AccountSet accountSet);



    void saveAll(List<AccountingPeriod> accountingPeriods);
}
