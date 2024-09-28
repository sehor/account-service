package com.skyflytech.accountservice.core.transaction.service;

import com.skyflytech.accountservice.core.accountingPeriod.model.AccountingPeriod;
import com.skyflytech.accountservice.core.journalEntry.model.JournalEntry;
import com.skyflytech.accountservice.core.transaction.model.Transaction;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * @Author pzr
 * @date:2024-09-27-18:25
 * @Description:
 **/
public interface TransactionService {

    /**
     * 获取所有交易
     * @param accountSetId 账套ID
     * @return 交易列表
     */
    List<Transaction> getAllTransactions(String accountSetId);

    /**
     * 模糊搜索交易
     * @param query 搜索关键词
     * @param accountSetId 账套ID
     * @return 匹配的交易列表
     */
    List<Transaction> searchTransactions(String query, String accountSetId);

    /**
     * 根据账户ID查找交易
     * @param accountId 账户ID
     * @return 交易列表
     */
    List<Transaction> findByAccountId(String accountId);

    /**
     * 根据账户和时间段查找交易
     * @param leafAccountsIds 所有叶子账号的ids
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param page 页码
     * @param size 每页大小
     * @return 分页后的交易列表
     */
    Page<Transaction> findTransactionsByAccountAndPeriod(List<String> leafAccountsIds, LocalDate startDate, LocalDate endDate, int page, int size);

    /**
     * 计算总借方和总贷方金额
     * @param accountId 账户ID
     * @param modifiedDate 修改日期
     * @return 总借方和总贷方金额
     */
    Map.Entry<BigDecimal, BigDecimal> calculateTotalDebitAndCredit(String accountId, LocalDate modifiedDate);

    /**
     * 保存交易
     * @param transaction 要保存的交易
     * @return 保存后的交易
     */
    Transaction saveTransaction(Transaction transaction);

    /**
     * 根据记账凭证删除交易
     * @param entry 记账凭证
     */
    void deleteByJourneyEntry(JournalEntry entry);

    /**
     * 删除交易
     * @param transactionId 要删除的交易ID
     */
    void deleteTransaction(String transactionId);

    /**
     * 根据会计期间查找交易
     * @param accountingPeriod 会计期间
     * @return 交易列表
     */
    List<Transaction> findTransactionsByAccountingPeriod(AccountingPeriod accountingPeriod);

    void saveAll(List<Transaction> transactions);
}
