package com.skyflytech.accountservice.core.journalEntry.service;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntryView;

import java.time.LocalDate;
import java.util.List;

public interface JournalEntryService {

    /**
     * 删除记账凭证
     * @param journalEntry 要删除的记账凭证
     */
    void deleteEntry(JournalEntry journalEntry);

    /**
     * 获取所有记账凭证
     * @param accountSetId 账套ID
     * @return 记账凭证列表
     */
    List<JournalEntry> getAllJournalEntries(String accountSetId);

    /**
     * 根据时间段获取记账凭证视图
     * @param accountSetId 账套ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 记账凭证视图列表
     */
    List<JournalEntryView> getJournalEntriesByPeriod(String accountSetId, LocalDate startDate, LocalDate endDate);

    /**
     * 根据会计期间获取记账凭证
     * @param accountSetId 账套ID
     * @param accountingPeriod 会计期间
     * @return 记账凭证列表
     */
    List<JournalEntry> getJournalEntriesByPeriod(String accountSetId, AccountingPeriod accountingPeriod);

    /**
     * 根据账套ID删除记账凭证
     * @param accountSetId 账套ID
     */
    void deleteJournalEntriesByAccountSetId(String accountSetId);



    /**
     * 查找某个会计期间的最大凭证号
     * @param accountSetId 账套ID
     * @param date 日期
     * @return 最大凭证号
     */
    int findMaxVoucherNum(String accountSetId, LocalDate date);

    /**
     * 重新排序并重设凭证号
     * @param accountSetId 账套ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     */
    void reorderVoucherNum(String accountSetId, LocalDate startDate, LocalDate endDate);

    /**
     * 检查某个会计期间的凭证号是否连续
     * @param accountingPeriod 会计期间
     * @return 是否连续
     */
    boolean isVoucherNumContinuous(AccountingPeriod accountingPeriod);
}
