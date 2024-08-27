package com.skyflytech.accountservice.domain.account;

/**
 * @Author pzr
 * @date:2024-08-04-5:48
 * @Description:
 **/
public enum AccountingDirection {
    DEBIT("借"),
    CREDIT("贷");

    private final String chineseName;

    AccountingDirection(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }
    public static AccountingDirection fromChineseName(String chineseName) {
        for (AccountingDirection direction : AccountingDirection.values()) {
            if (direction.getChineseName().equals(chineseName)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("No enum constant for Chinese name: " + chineseName);
    }
}
