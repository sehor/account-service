package com.skyflytech.accountservice.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        logger.info("JwtTokenFilter initialized");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, 
                                    @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {
        logger.info("JwtTokenFilter is processing a request to: {}", request.getRequestURI());
        
        String accessToken = getTokenFromCookie(request, "access_token");
        
        logger.info("Extracted access token from cookie: {}", accessToken);
        
        if (accessToken != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                if (jwtUtil.isTokenExpired(accessToken)) {
                    logger.info("Access token is expired, attempting to refresh");
                    String refreshToken = getTokenFromCookie(request, "refresh_token");
                    if (refreshToken == null||jwtUtil.isTokenExpired(refreshToken)) {
                        throw new Exception("not authenticated, please login!");
                    }
    
                    String newAccessToken = jwtUtil.refreshAccessToken(refreshToken);
                    if (newAccessToken != null) {
                        accessToken = newAccessToken;
                        addTokenCookie(response, "access_token", newAccessToken, false);
                        logger.info("New access token generated and added to cookie");
                    }
                }
                
               else{
                    String username = jwtUtil.extractUsername(accessToken);
                    logger.info("Extracted username: {}", username);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    logger.info("Loaded user details: {}", userDetails);
                    
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    logger.info("Authentication set in SecurityContext");
                } 

            } catch (Exception e) {
                logger.error("Cannot set user authentication: {}", e);
            }
        } else {
            logger.info("No token found in cookie or authentication already set");
        }
        
        chain.doFilter(request, response);
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