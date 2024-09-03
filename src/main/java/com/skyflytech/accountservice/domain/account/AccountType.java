package com.skyflytech.accountservice.domain.account;

import java.util.Arrays;
import java.util.List;

/**
 * @Author pzr
 * @date:2024-08-04-5:46
 * @Description:
 **/
public enum AccountType {
    CURRENT_ASSET("流动资产"),
    NON_CURRENT_ASSET("非流动资产"),
    CURRENT_LIABILITY("流动负债"),
    NON_CURRENT_LIABILITY("非流动负债"),
    EQUITY("所有者权益"),
    COST("成本"),
    OPERATING_REVENUE("营业收入"),
    OTHER_INCOME("其他收益"),
    OPERATING_COST_TAX("营业成本及税金"),
    OTHER_EXPENSE("其他损失"),
    PERIOD_EXPENSE("期间费用"), // 修改这里
    INCOME_TAX("所得税"),
    PRIOR_YEAR_ADJUSTMENT("以前年度损益调整");

    private final String chineseName;

    AccountType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public static AccountType fromChineseName(String chineseName) {
        for (AccountType type : AccountType.values()) {
            if (type.getChineseName().equals(chineseName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant for Chinese name: " + chineseName);
    }

    public static List<AccountType> getAssetTypes() {
        return Arrays.asList(CURRENT_ASSET, NON_CURRENT_ASSET);
    }

    public static List<AccountType> getLiabilityTypes() {
        return Arrays.asList(CURRENT_LIABILITY, NON_CURRENT_LIABILITY);
    }

    public static List<AccountType> getEquityTypes() {
        return List.of(EQUITY);
    }

    public static List<AccountType> getCostTypes() {
        return List.of(COST);
    }

    public static List<AccountType> getPeriodProfitLossTypes() {
        return Arrays.asList(
            OPERATING_REVENUE,
            OPERATING_COST_TAX,
            OTHER_EXPENSE,
            PERIOD_EXPENSE,
            INCOME_TAX,
            PRIOR_YEAR_ADJUSTMENT
        );
    }

    public boolean isAsset() {
        return getAssetTypes().contains(this);
    }

    public boolean isLiability() {
        return getLiabilityTypes().contains(this);
    }

    public boolean isEquity() {
        return getEquityTypes().contains(this);
    }

    public boolean isCost() {
        return getCostTypes().contains(this);
    }

    public boolean isPeriodProfitLoss() {
        return getPeriodProfitLossTypes().contains(this);
    }
}