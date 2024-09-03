package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.repository.AccountingPeriodRepository;
import com.skyflytech.accountservice.repository.TransactionMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountingPeriodServiceTest {

    @InjectMocks
    private AccountingPeriodService accountingPeriodService;

    @InjectMocks
    private CloseAccountingPeriodService closeAccountingPeriodService;

    @Mock
    private AccountingPeriodRepository accountingPeriodRepository;

    @Mock
    private TransactionMongoRepository transactionRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private JournalEntryService journalEntryService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void closeAccountingPeriod_Success() {
        // 准备测试数据
        String accountingPeriodId = "testPeriodId";
        String accountSetId = "testAccountSetId";
        LocalDate endDate = LocalDate.of(2023, 5, 31);
        AccountingPeriod currentPeriod = new AccountingPeriod();
        currentPeriod.setId(accountingPeriodId);
        currentPeriod.setAccountSetId(accountSetId);
        currentPeriod.setEndDate(endDate);
        currentPeriod.setClosed(false);

        Map<String, BigDecimal> closingBalances = new HashMap<>();
        closingBalances.put("account1", BigDecimal.valueOf(1000));
        closingBalances.put("account2", BigDecimal.valueOf(2000));

        // 设置 mock 行为
        when(accountingPeriodRepository.findById(accountingPeriodId)).thenReturn(Optional.of(currentPeriod));
        when(accountService.calculateAllAccountBalancesForMonth(eq(accountSetId), any(YearMonth.class)))
                .thenReturn(closingBalances);
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(i -> i.getArguments()[0]);

        // 执行测试
        AccountingPeriod newPeriod = closeAccountingPeriodService.closeAccountingPeriod(accountingPeriodId);

        // 验证结果
        assertNotNull(newPeriod);
        assertTrue(currentPeriod.isClosed());
        assertEquals(YearMonth.from(endDate).plusMonths(1).atDay(1), newPeriod.getStartDate());
        assertEquals(closingBalances, newPeriod.getOpeningBalances());

        // 验证方法调用
        verify(accountingPeriodRepository, times(2)).save(any(AccountingPeriod.class));
        verify(journalEntryService, times(1)).processJournalEntryView(any());
    }

    @Test
    void closeAccountingPeriod_PeriodNotFound() {
        // 设置 mock 行为
        when(accountingPeriodRepository.findById(anyString())).thenReturn(Optional.empty());

        // 执行测试并验证异常
        assertThrows(RuntimeException.class, () -> closeAccountingPeriodService.closeAccountingPeriod("nonExistentId"));
    }

    @Test
    void closeAccountingPeriod_AlreadyClosed() {
        // 准备测试数据
        AccountingPeriod closedPeriod = new AccountingPeriod();
        closedPeriod.setClosed(true);

        // 设置 mock 行为
        when(accountingPeriodRepository.findById(anyString())).thenReturn(Optional.of(closedPeriod));

        // 执行测试并验证异常
        assertThrows(RuntimeException.class, () -> closeAccountingPeriodService.closeAccountingPeriod("closedPeriodId"));
    }

    // 可以添加更多测试方法，如测试会计恒等式检查、创建初始期间等
}