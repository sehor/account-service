package com.skyflytech.accountservice.security.jwt;

import com.skyflytech.accountservice.security.model.User;
import com.skyflytech.accountservice.security.service.Imp.UserServiceImp;
import com.skyflytech.accountservice.security.model.CustomAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenFilter.class);

    private final JwtUtil jwtUtil;
    private final UserServiceImp userServiceImp;

    public JwtTokenFilter(JwtUtil jwtUtil, UserServiceImp userServiceImp) {
        this.jwtUtil = jwtUtil;
        this.userServiceImp = userServiceImp;
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
                        addTokenCookie(response, newAccessToken);
                        
                        // 在这里设置安全上下文
                        setSecurityContext(newAccessToken);
                    }
                } else {
                    setSecurityContext(accessToken);
                }
            } catch (Exception e) {
                logger.error("Cannot set user authentication: {}", e);
            }
        }


        chain.doFilter(request, response);
    }

    private void setSecurityContext(String token) {
        String username = jwtUtil.extractUsername(token);
        User user = userServiceImp.getUserByUsername(username);
        
        String currentAccountSetId = user.getCurrentAccountSetId();
        List<String> accountSetIds = user.getAccountSetIds();
        
        CustomAuthentication authentication = new CustomAuthentication(
            user, null, user.getAuthorities(),
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

    private void addTokenCookie(HttpServletResponse response, String value) {
        int maxAge = -1; // 7天或会话结束
        Cookie cookie = new Cookie("access_token", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // 如果不是 HTTPS，考虑设置为 false
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}