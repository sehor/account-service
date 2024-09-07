package com.skyflytech.accountservice.security;

import org.springframework.stereotype.Component;
import com.skyflytech.accountservice.global.GlobalConst;

@Component
public class CurrentAccountSetIdHolder {
    private String currentAccountSetId = GlobalConst.Current_AccountSet_Id_Test;

    public String getCurrentAccountSetId() {
        return currentAccountSetId;
    }

    public void setCurrentAccountSetId(String accountSetId) {
        this.currentAccountSetId = accountSetId;
    }
}
