package com.skyflytech.accountservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
        if (userDetails instanceof User) {
            User user = (User) userDetails;
            claims.put("currentAccountSetId", user.getCurrentAccountSetId());
            claims.put("accountSetIds", user.getAccountSetIds());
        }
        return createToken(claims, userDetails.getUsername());
    }

    public String generateToken(OAuth2User oauth2User) {
        Map<String, Object> claims = new HashMap<>();
        // Add relevant OAuth2User details to claims
        claims.put("email", oauth2User.getAttribute("email"));
        claims.put("name", oauth2User.getAttribute("name"));
        return createToken(claims, oauth2User.getName());
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

    public String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public String extractCurrentAccountSetId(String token) {
        return extractClaim(token, claims -> claims.get("currentAccountSetId", String.class));
    }

    public List<String> extractAccountSetIds(String token) {
        return extractClaim(token, claims -> {
            @SuppressWarnings("unchecked")
            List<String> accountSetIds = (List<String>) claims.get("accountSetIds");
            return accountSetIds;
        });
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
}