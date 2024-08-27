package com.skyflytech.accountservice.domain.account;

public enum AccountState {
    ACTIVE("启用"),
    INACTIVE("禁用");

    private final String chineseName;

    AccountState(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }
    public static AccountState fromChineseName(String chineseName) {
        for (AccountState state : AccountState.values()) {
            if (state.getChineseName().equals(chineseName)) {
                return state;
            }
        }
        throw new IllegalArgumentException("No enum constant for Chinese name: " + chineseName);
    }
}
