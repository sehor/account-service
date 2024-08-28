package com.skyflytech.accountservice.listener;

import com.skyflytech.accountservice.security.AccountSetIdAware;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MongoEventListener extends AbstractMongoEventListener<Object> {
    
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;

    @Autowired

    public MongoEventListener(CurrentAccountSetIdHolder currentAccountSetIdHolder) {
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
    }

    @Override
    public void onBeforeSave(BeforeSaveEvent<Object> event) {
        AccountSetIdAware source = (AccountSetIdAware) event.getSource();
        if(source.getAccountSetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "AccountSetId is null");
        }else if(!source.getAccountSetId().equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "AccountSetId is not match");
        }
    }
}
