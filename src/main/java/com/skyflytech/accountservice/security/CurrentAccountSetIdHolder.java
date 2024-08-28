package com.skyflytech.accountservice.security;

import org.springframework.stereotype.Component;
import com.skyflytech.accountservice.global.GlobalConst;

@Component
public class CurrentAccountSetIdHolder {
    public String getCurrentAccountSetId() {
        return GlobalConst.Current_AccountSet_Id_Test;
    }
    public void setCurrentAccountSetId(String accountSetId) {
        //todo
    }
}
