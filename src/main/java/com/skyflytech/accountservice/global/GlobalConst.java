package com.skyflytech.accountservice.global;

import com.skyflytech.accountservice.domain.account.TransferAccountType;
import com.skyflytech.accountservice.domain.account.AccountType;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class GlobalConst {

    // 防止实例化
    private GlobalConst() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    //科目编号的位数
    public static final int[] ACCOUNT_Code_LENGTH = {4, 8, 11, 14};
    //科目位数对应的级次map
    public static final Map<Integer, Integer> ACCOUNT_CODE_LEVEL_MAP = Map.of(4, 1, 8, 2, 11, 3, 14, 4);


    // 本年利润科目代码
    public static final String CURRENT_YEAR_PROFIT_CODE = "3103";

    // 未分配利润科目代码
    public static final String UNDISTRIBUTED_PROFIT_CODE = "31040015";

    // 存储需要在期末自动结转的账户类型
    public static final Map<TransferAccountType, List<AccountType>> AUTO_TRANSFER_ACCOUNTS;

    public static final String Current_AccountSet_Id_Test = "accountSetId_for_test";

    static {
        Map<TransferAccountType, List<AccountType>> tempMap = new EnumMap<>(TransferAccountType.class);
        tempMap.put(TransferAccountType.INCOME, Arrays.asList(AccountType.OPERATING_REVENUE, AccountType.OTHER_INCOME));
        tempMap.put(TransferAccountType.COST_AND_EXPENSE, Arrays.asList(AccountType.COST, AccountType.OPERATING_COST_TAX, AccountType.PERIOD_EXPENSE));
        tempMap.put(TransferAccountType.NON_OPERATING_EXPENSE, List.of(AccountType.OTHER_EXPENSE));
        tempMap.put(TransferAccountType.PRIOR_YEAR_ADJUSTMENT, List.of(AccountType.PRIOR_YEAR_ADJUSTMENT));

        AUTO_TRANSFER_ACCOUNTS = Collections.unmodifiableMap(tempMap);
    }
}
