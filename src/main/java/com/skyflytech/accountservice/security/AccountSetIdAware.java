package com.skyflytech.accountservice.security;

public interface AccountSetIdAware {
    void setAccountSetId(String accountSetId);
    String getAccountSetId();
}
