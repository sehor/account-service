package com.skyflytech.accountservice.domain.account;

public enum TransferAccountType {
    INCOME("收入"),
    COST_AND_EXPENSE("成本和费用"),
    NON_OPERATING_EXPENSE("营业外支出"),
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