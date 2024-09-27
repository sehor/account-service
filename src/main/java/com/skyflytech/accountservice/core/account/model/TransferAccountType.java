package com.skyflytech.accountservice.core.account.model;

public enum TransferAccountType {
    INCOME("收入"),
    ALL_EXPENSE_TYPES("营业成本和附加税-费用-损失-营业外支出-所得税"),
    PRIOR_YEAR_ADJUSTMENT("以前年度损益调整");

    private final String chineseName;

    TransferAccountType(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public static TransferAccountType fromChineseName(String chineseName) {
        for (TransferAccountType type : TransferAccountType.values()) {
            if (type.getChineseName().equals(chineseName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("No enum constant for Chinese name: " + chineseName);
    }
}