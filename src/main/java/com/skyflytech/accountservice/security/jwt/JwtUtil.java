package com.skyflytech.accountservice.security.jwt;

import com.skyflytech.accountservice.security.model.CustomAuthentication;
import com.skyflytech.accountservice.security.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private final Long expirationMillis;
    private final SecretKey secretKey;
    private final UserDetailsService userDetailsService;

    public JwtUtil(@Value("${jwt.expiration}") Long expirationSeconds,
                   @Value("${spring.security.oauth2.resourceserver.jwt.secret}") String serverSecret,
                   UserDetailsService userDetailsService) {
        this.expirationMillis = expirationSeconds * 1000;
        this.secretKey = Keys.hmacShaKeyFor(serverSecret.getBytes(StandardCharsets.UTF_8));
        this.userDetailsService = userDetailsService;
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            // 验证签名并解析token
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);

            final String username = extractUsername(token);
            return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
        } catch (Exception e) {
            logger.error("Error validating token", e);
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String refreshAccessToken(String refreshToken) {
        try {
            if (isTokenExpired(refreshToken)) {
                logger.warn("Attempt to refresh with expired token");
                return null;
            }
            String username = extractUsername(refreshToken);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (validateToken(refreshToken, userDetails)) {
                return generateToken(userDetails);
            } else {
                logger.warn("Invalid refresh token for user: {}", username);
            }
        } catch (Exception e) {
            logger.error("Error refreshing access token", e);
        }
        return null;
    }

       // 添加token到cookie
    public void addTokenCookie(HttpServletResponse response, String name, String value, boolean isRememberMe) {
        int maxAge = isRememberMe ? 7 * 24 * 60 * 60 : -1; // 7天或会话结束
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }

    //set cookies and set security context
    public void setCookiesAndSecurityContext(HttpServletResponse response, User user, boolean isRememberMe) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String accessToken = generateToken(user);
        String refreshToken = generateToken(user);
        addTokenCookie(response, "access_token", accessToken, isRememberMe);
        addTokenCookie(response, "refresh_token", refreshToken, isRememberMe);
        CustomAuthentication customAuth = new CustomAuthentication(
            user, 
            auth.getCredentials(), 
            auth.getAuthorities(),
            user.getCurrentAccountSetId(),
            user.getAccountSetIds()
            );  
        SecurityContextHolder.getContext().setAuthentication(customAuth);
    }
}