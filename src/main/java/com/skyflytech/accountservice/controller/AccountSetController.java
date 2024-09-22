package com.skyflytech.accountservice.controller;

import java.util.List;
import java.util.Map;
import com.skyflytech.accountservice.domain.AccountSet;
import com.skyflytech.accountservice.service.AccountSetService;

import jakarta.servlet.http.HttpServletResponse;

import com.skyflytech.accountservice.security.User;
import com.skyflytech.accountservice.security.CustomAuthentication;
import com.skyflytech.accountservice.security.JwtUtil;
import com.skyflytech.accountservice.security.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
@RestController
@RequestMapping("/api/account-sets")
public class AccountSetController {
    private final AccountSetService accountSetService;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final CurrentAccountSetIdHolder currentAccountSetIdHolder;

    public AccountSetController(AccountSetService accountSetService, JwtUtil jwtUtil, UserService userService, CurrentAccountSetIdHolder currentAccountSetIdHolder) {
        this.accountSetService = accountSetService;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.currentAccountSetIdHolder = currentAccountSetIdHolder;
    }

    @PostMapping("/create")
    public ResponseEntity<AccountSet> createAccountSet(@RequestBody AccountSet accountSet,HttpServletResponse  response) {
        String userName = SecurityContextHolder.getContext().getAuthentication().getName();
        AccountSet createdAccountSet = accountSetService.createAccountSet(accountSet,userName);
        //set cookies and set security context
        User user = userService.getUserByUsername(userName);
        jwtUtil.setCookiesAndSecurityContext(response, user, false);
        return new ResponseEntity<>(createdAccountSet, HttpStatus.CREATED);
    }

    @GetMapping("/all")
    public ResponseEntity<List<AccountSet>> getAllAccountSets() {
        List<String> accountSetIds = currentAccountSetIdHolder.getAccountSetIds();
        System.out.println("accountSetIds: " + accountSetIds);
        List<AccountSet> accountSets = accountSetService.getAccountSetsByIds(accountSetIds);
        return new ResponseEntity<>(accountSets, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteAccountSet(@PathVariable String id, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof CustomAuthentication) {
            CustomAuthentication customAuth = (CustomAuthentication) auth;
            User user = userService.getUserByUsername(customAuth.getName());
            if (customAuth.getAccountSetIds().contains(id)) {
              User update_user  =accountSetService.deleteAccountSet(id,user);
              //set cookies and set security context
              jwtUtil.setCookiesAndSecurityContext(response, update_user, false);
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
        }
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }

    @PostMapping("/switch/{id}")
    public ResponseEntity<?> switchAccountSet(@PathVariable String id, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof CustomAuthentication) {
            CustomAuthentication customAuth = (CustomAuthentication) auth;
            String username = customAuth.getName();
            
            try {
                User user = userService.getUserByUsername(username);
                
                if (user.getAccountSetIds().contains(id)) {
                    user = userService.updateUserCurrentAccountSetId(username, id);
                    
              //set cookies and set security context
              jwtUtil.setCookiesAndSecurityContext(response, user, false);

                    return ResponseEntity.ok(Map.of(
                        "message", "Account set switched successfully", 
                        "currentAccountSetId", id,
                        "accountSetIds", user.getAccountSetIds()
                    ));
                } else {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "User does not have permission for this account set:" + id));
                }
            } catch (UsernameNotFoundException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
            }
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Unauthorized operation"));
    }

    //get account set by id
    @GetMapping("/{id}")
    public ResponseEntity<AccountSet> getAccountSetById(@PathVariable String id) {
        AccountSet accountSet = accountSetService.getAccountSetById(id);
        return new ResponseEntity<>(accountSet, HttpStatus.OK);
    }
}