package com.skyflytech.accountservice.service;

import com.skyflytech.accountservice.domain.AccountAmountHolder;
import com.skyflytech.accountservice.domain.AccountingPeriod;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.global.GlobalConst;
import com.skyflytech.accountservice.repository.AccountingPeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest
class CloseAccountingPeriodServiceTest {

    @Autowired
    private CloseAccountingPeriodService closeAccountingPeriodService;

    @Autowired
    private AccountingPeriodRepository accountingPeriodRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ProcessJournalEntry processJournalEntryService;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

   // @Test
    void closeAccountingPeriod_shouldCloseCurrentPeriodAndCreateNewOne() {
        // 准备测试数据
        String accountingPeriodId = "testPeriodId";
        AccountingPeriod currentPeriod = new AccountingPeriod();
        currentPeriod.setId(accountingPeriodId);
        currentPeriod.setAccountSetId("testAccountSetId");
        currentPeriod.setName("2023年05月");
        currentPeriod.setStartDate(LocalDate.of(2023, 5, 1));
        currentPeriod.setEndDate(LocalDate.of(2023, 5, 31));
        currentPeriod.setClosed(false);

        Map<String, AccountAmountHolder> amountHolders = new HashMap<>();
        amountHolders.put("account1", new AccountAmountHolder());
        amountHolders.put("account2", new AccountAmountHolder());
        currentPeriod.setAmountHolders(amountHolders);

        // 设置 mock 行为
        when(accountingPeriodRepository.findById(accountingPeriodId)).thenReturn(Optional.of(currentPeriod));
        when(accountingPeriodRepository.save(any(AccountingPeriod.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 执行被测试的方法
        AccountingPeriod newPeriod = closeAccountingPeriodService.closeAccountingPeriod(accountingPeriodId);

        // 验证结果
        assertTrue(currentPeriod.isClosed());
        assertEquals("2023年06月", newPeriod.getName());
        assertEquals(LocalDate.of(2023, 6, 1), newPeriod.getStartDate());
        assertEquals(LocalDate.of(2023, 6, 30), newPeriod.getEndDate());
        assertFalse(newPeriod.isClosed());
        assertEquals(currentPeriod.getAmountHolders(), newPeriod.getAmountHolders());

        // 验证方法调用
        verify(accountingPeriodRepository).findById(accountingPeriodId);
        verify(accountingPeriodRepository, times(2)).save(any(AccountingPeriod.class));
        verify(accountService).getAllAccounts(currentPeriod.getAccountSetId());
        verify(processJournalEntryService, atLeastOnce()).processJournalEntryView(any());
    }
    //test findAllLeafAccountsForClosingPeriod
    @Test
    void testFindAllLeafAccountsForClosingPeriod() {
        // 准备测试数据
        List<Account> accounts=closeAccountingPeriodService.findAllLeafAccountsForClosingPeriod(GlobalConst.Current_AccountSet_Id_Test);

        for(Account account:closeAccountingPeriodService.findIncomeAccounts(accounts)){
            System.out.println(account.getName());
        }
        for(Account account:closeAccountingPeriodService.findExpenseAndLossAccounts(accounts)){
            System.out.println(account.getName());
        }
        for(Account account:closeAccountingPeriodService.findPriorYearAdjustmentAccounts(accounts)){
            System.out.println(account.getName());
        }
    }

    // 可以添加更多测试用例，比如测试已关闭的期间、不存在的期间等边界情况
}