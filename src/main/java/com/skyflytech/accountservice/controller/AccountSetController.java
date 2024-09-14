package com.skyflytech.accountservice.controller;

import java.util.List;
import java.util.Map;
import com.skyflytech.accountservice.domain.AccountSet;
import com.skyflytech.accountservice.service.AccountSetService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.skyflytech.accountservice.security.CurrentAccountSetIdHolder;
@RestController
@RequestMapping("/api/account-sets")
public class AccountSetController {
    private final Logger log = LoggerFactory.getLogger(AccountSetController.class);
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
    public ResponseEntity<AccountSet> createAccountSet(@RequestBody AccountSet accountSet) {
        AccountSet createdAccountSet = accountSetService.createAccountSet(accountSet);
        return new ResponseEntity<>(createdAccountSet, HttpStatus.CREATED);
    }

    @GetMapping("/all")
    public ResponseEntity<List<AccountSet>> getAllAccountSets() {
        List<String> accountSetIds = currentAccountSetIdHolder.getAccountSetIds();
        List<AccountSet> accountSets = accountSetService.getAccountSetsByIds(accountSetIds);
        return new ResponseEntity<>(accountSets, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteAccountSet(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof CustomAuthentication) {
            CustomAuthentication customAuth = (CustomAuthentication) auth;
            if (customAuth.getAccountSetIds().contains(id)) {
                accountSetService.deleteAccountSet(id);
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);
            }
        }
        return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    }

    @PostMapping("/switch/{id}")
    public ResponseEntity<?> switchAccountSet(@PathVariable String id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof CustomAuthentication) {
            CustomAuthentication customAuth = (CustomAuthentication) auth;
            String username = customAuth.getName();
            
            try {
                User user = userService.getUserByUsername(username);
                
                if (user.getAccountSetIds().contains(id)) {
                    user = userService.updateUserCurrentAccountSetId(username, id);
                    
                    CustomAuthentication newAuth = new CustomAuthentication(
                        user, 
                        auth.getCredentials(), 
                        auth.getAuthorities(),
                        id,
                        user.getAccountSetIds()
                    );
                    SecurityContextHolder.getContext().setAuthentication(newAuth);

                    String newToken = jwtUtil.generateToken(user);

                    return ResponseEntity.ok(Map.of(
                        "message", "Account set switched successfully", 
                        "currentAccountSetId", id,
                        "accountSetIds", user.getAccountSetIds(),
                        "token", newToken
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
}