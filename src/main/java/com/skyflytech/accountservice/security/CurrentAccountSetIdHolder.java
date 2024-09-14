package com.skyflytech.accountservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import com.skyflytech.accountservice.global.GlobalConst;
import java.util.Collections;
import java.util.List;

@Component
public class CurrentAccountSetIdHolder {

    public String getCurrentAccountSetId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof CustomAuthentication) {
            CustomAuthentication customAuth = (CustomAuthentication) authentication;
            return customAuth.getCurrentAccountSetId();
        } else if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            return (String) jwtAuth.getToken().getClaims().get("currentAccountSetId");
        }
        return GlobalConst.Current_AccountSet_Id_Test; // 默认值
    }

    @SuppressWarnings("unchecked")
    public List<String> getAccountSetIds() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof CustomAuthentication) {
            CustomAuthentication customAuth = (CustomAuthentication) authentication;
            return customAuth.getAccountSetIds();
        } else if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            return (List<String>) jwtAuth.getToken().getClaims().get("accountSetIds");
        }
        return Collections.emptyList(); // 如果没有认证信息,返回空列表
    }
}
