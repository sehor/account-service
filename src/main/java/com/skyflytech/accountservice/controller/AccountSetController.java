package com.skyflytech.accountservice.controller;

import java.util.List;
import com.skyflytech.accountservice.domain.AccountSet;
import com.skyflytech.accountservice.service.AccountSetService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/account-sets")
public class AccountSetController {

    @Autowired
    private AccountSetService accountSetService;

    @PostMapping("/create")
    public ResponseEntity<AccountSet> createAccountSet( @RequestBody AccountSet accountSet) {
        AccountSet createdAccountSet = accountSetService.createAccountSet(accountSet);
        return new ResponseEntity<>(createdAccountSet, HttpStatus.CREATED);
    }

    //get all account sets
    @GetMapping
    public ResponseEntity<List<AccountSet>> getAllAccountSets() {
        List<AccountSet> accountSets = accountSetService.getAllAccountSets();
        return new ResponseEntity<>(accountSets, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccountSet(@PathVariable String id) {
        accountSetService.deleteAccountSet(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/switch/{id}")
    public ResponseEntity<Void> switchAccountSet(@PathVariable String id) {
        accountSetService.switchAccountSet(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}