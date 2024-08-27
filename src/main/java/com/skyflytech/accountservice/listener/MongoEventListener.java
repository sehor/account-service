package com.skyflytech.accountservice.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.mapping.event.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.skyflytech.accountservice.security.AccountSetIdAware;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;

@Component
public class MongoEventListener extends AbstractMongoEventListener<Object> {
    
    @Autowired
    private CurrentAccountSetIdHolder currentAccountSetIdHolder;

    @Override
    public void onBeforeSave(BeforeSaveEvent<Object> event) {
        AccountSetIdAware source = (AccountSetIdAware) event.getSource();
        if(source == null||source.getAccountSetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "AccountSetId is null");
        }else if(!source.getAccountSetId().equals(currentAccountSetIdHolder.getCurrentAccountSetId())) {
            throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "AccountSetId is not match");
        }
    }
}
