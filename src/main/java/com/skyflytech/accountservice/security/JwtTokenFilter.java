package com.skyflytech.accountservice.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtTokenFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                    @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        
        String accessToken = getTokenFromCookie(request, "access_token");
        
        
        if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (jwtUtil.isTokenExpired(accessToken)) {
                    String refreshToken = getTokenFromCookie(request, "refresh_token");
                    if (refreshToken == null||jwtUtil.isTokenExpired(refreshToken)) {
                        throw new Exception("Not authenticated, please login!");
                    }
    
                    String newAccessToken = jwtUtil.refreshAccessToken(refreshToken);
                    if (newAccessToken != null) {
                        accessToken = newAccessToken;
                        addTokenCookie(response, "access_token", newAccessToken, false);
                        
                        // 在这里设置安全上下文
                        setSecurityContext(newAccessToken);
                    }
                } else {
                    setSecurityContext(accessToken);
                }
            } catch (Exception e) {
                logger.error("Cannot set user authentication: {}", e);
            }
        } else {
        }
        
        
        chain.doFilter(request, response);
    }

    private void setSecurityContext(String token) {
        String username = jwtUtil.extractUsername(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        
        String currentAccountSetId = jwtUtil.extractCurrentAccountSetId(token);
        List<String> accountSetIds = jwtUtil.extractAccountSetIds(token);
        
        CustomAuthentication authentication = new CustomAuthentication(
            userDetails, null, userDetails.getAuthorities(),
            currentAccountSetId, accountSetIds);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String getTokenFromCookie(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void addTokenCookie(HttpServletResponse response, String name, String value, boolean isRememberMe) {
        int maxAge = isRememberMe ? 7 * 24 * 60 * 60 : -1; // 7天或会话结束
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // 如果不是 HTTPS，考虑设置为 false
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}