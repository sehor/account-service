package com.skyflytech.accountservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import com.skyflytech.accountservice.global.GlobalConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.List;

@Component
public class CurrentAccountSetIdHolder {

    private static final Logger logger = LoggerFactory.getLogger(CurrentAccountSetIdHolder.class);

    public String getCurrentAccountSetId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof CustomAuthentication customAuth) {
            String accountSetId = customAuth.getCurrentAccountSetId();
            logger.info("Get current account set ID from CustomAuthentication: {}", accountSetId);
            return accountSetId;
        } else if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            String accountSetId = (String) jwtAuth.getToken().getClaims().get("currentAccountSetId");
            logger.info("Get current account set ID from JwtAuthenticationToken: {}", accountSetId);
            return accountSetId;
        }

        logger.warn("Cannot get current account set ID from authentication, using default value");
        return GlobalConst.Current_AccountSet_Id_Test; // Default value
    }

    //get accountSetIds
    public List<String> getAccountSetIds() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof CustomAuthentication customAuth) {
            return customAuth.getAccountSetIds();
        }
        return Collections.emptyList();
    }

}
