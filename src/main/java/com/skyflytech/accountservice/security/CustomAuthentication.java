package com.skyflytech.accountservice.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class CustomAuthentication extends UsernamePasswordAuthenticationToken {
    private final String currentAccountSetId;
    private final List<String> accountSetIds;

    public CustomAuthentication(Object principal, Object credentials, 
                                Collection<? extends GrantedAuthority> authorities,
                                String currentAccountSetId, List<String> accountSetIds) {
        super(principal, credentials, authorities);
        this.currentAccountSetId = currentAccountSetId;
        this.accountSetIds = accountSetIds;
    }

    public String getCurrentAccountSetId() {
        return currentAccountSetId;
    }

    public List<String> getAccountSetIds() {
        return accountSetIds;
    }
}