package com.skyflytech.accountservice.controller;

import com.mongodb.client.result.DeleteResult;
import com.skyflytech.accountservice.domain.APIResponse;
import com.skyflytech.accountservice.domain.account.Account;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
import com.skyflytech.accountservice.service.AccountService;
import com.skyflytech.accountservice.service.ExcelImportService;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.math.BigDecimal;
import java.util.Map;



@Tag(name = "account")
@RestController
@RequestMapping("/api/accounts")
public class AccountController {


    private final  AccountService accountService;
    private final MongoOperations mongoOperations;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;
    private final ExcelImportService excelImportService;
    
    @Value("${spring.profiles.active}")
    private String activeProfile;

    public AccountController(MongoOperations mongoOperations, AccountService accountService, CurrentAccountSetIdHolder currentAccountSetIdHolder, ExcelImportService excelImportService) {
        this.excelImportService = excelImportService;
        this.mongoOperations = mongoOperations;
        this.accountService = accountService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
    }

    @ApiResponse(description = "upload a excel file")
    @PostMapping("/upload/{accountSetId}")
    public ResponseEntity<String> uploadFile(@PathVariable String accountSetId, @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseEntity<>("Please upload a file!", HttpStatus.BAD_REQUEST);
        }

        try {
            // 调用AccountService来处理上传的文件并提取Account实例
            excelImportService.extractAccountsFromExcel(file.getInputStream(), accountSetId);

            return new ResponseEntity<>("File uploaded and processed successfully.", HttpStatus.OK);

        } catch (IOException e) {
            e.printStackTrace();
            return new ResponseEntity<>("An error occurred while processing the file.", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return new ResponseEntity<>("Invalid data in the file.", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<List<Account>> getAllAccounts() {

        List<Account> accounts = accountService.getAllAccounts(getAccountSetId());
       // int slice= Math.min(accounts.size(), 5);
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAccountById(@PathVariable String id) {
        Account account = accountService.getAccountById(id);
        if (account != null) {

            return ResponseEntity.ok(account);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("not found");
        }
    }

    @PostMapping("/create")
    public ResponseEntity<APIResponse<Account>> createAccount(@RequestBody Account account) {
        account.setAccountSetId(getAccountSetId());
        Account create = accountService.createAccount(account);
        if(create==null){
            return ResponseEntity.badRequest().body(null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(new APIResponse<>("save successfully!",create));
    }

    @PutMapping("/update")
    public ResponseEntity<?> updateAccount( @RequestBody Account account) {
        try {
            Account updatedAccount = accountService.updateAccount(account);
            return ResponseEntity.ok(updatedAccount);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Account not found.");
        }
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> deleteAccount(@PathVariable String id) {
       
            accountService.deleteAccount(id);
            return ResponseEntity.ok("Account deleted successfully.");
    
    }

    @GetMapping("/search")
    public ResponseEntity<List<Account>> searchAccounts(@RequestParam("query") String query) {
        List<Account> accounts = accountService.searchAccounts(query,getAccountSetId());
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/{id}/leaf-subaccounts")
    public ResponseEntity<APIResponse<List<Account>>> getLeafSubAccounts(@PathVariable String id) {
        try {
            List<Account> leafSubAccounts = accountService.getLeafSubAccounts(id);
            return ResponseEntity.ok(new APIResponse<>("叶子子科目获取成功", leafSubAccounts));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new APIResponse<>("未找到指定的科目", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new APIResponse<>("获取叶子子科目时发生错误", null));
        }
    }

    //warn : just for developing stage and test
    @PostMapping("/delete/all")
    public ResponseEntity<String> deleteAll() {
        if ("prod".equals(activeProfile)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        DeleteResult result = mongoOperations.remove(new Query(), Account.class);
        long deletedCount = result.getDeletedCount();
        return ResponseEntity.ok(String.format("deleted %d records successfully!",deletedCount));
    }
    //get accountSetId
    private String getAccountSetId(){
        return currentAccountSetIdHolder.getCurrentAccountSetId();
    }

    @PostMapping("/initializeOpeningBalances")
    public ResponseEntity<?> initializeOpeningBalances(
            @RequestBody Map<String, BigDecimal> openingBalances) {
   
            String accountSetId = currentAccountSetIdHolder.getCurrentAccountSetId(); //for test
            accountService.initializeOpeningBalances(accountSetId, openingBalances);
            return ResponseEntity.ok("初始化成功");
      
    }
}