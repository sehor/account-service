package com.skyflytech.accountservice.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final AuthenticationManager authenticationManager;

    @Autowired
    public AuthController(JwtUtil jwtUtil, UserService userService, AuthenticationManager authenticationManager) {
        this.jwtUtil = jwtUtil;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/user")
    public ResponseEntity<?> getUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated() || 
            authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                 .body(Map.of("error", "未认证", "message", "用户未登录或会话已过期"));
        }

        Map<String, Object> response = new HashMap<>();

        // 尝试从cookie中获取access token
        String accessToken = getCookieValue(request, "access_token");
        if (accessToken != null) {
            try {
                // 从token中提取信息
                String username = jwtUtil.extractUsername(accessToken);
                String currentAccountSetId = jwtUtil.extractCurrentAccountSetId(accessToken);
                List<String> accountSetIds = jwtUtil.extractAccountSetIds(accessToken);

                response.put("username", username);
                response.put("currentAccountSetId", currentAccountSetId);
                response.put("accountSetIds", accountSetIds);

                // 如果需要额外的用户信息，可以从数据库中获取
                User user = userService.getUserByUsername(username);
                if (user != null) {
                    response.put("email", user.getEmail());
                    // 添加其他需要的用户信息
                }
            } catch (Exception e) {
                logger.error("Error extracting information from JWT", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("处理用户信息时出错");
            }
        } else if (authentication.getPrincipal() instanceof OAuth2User) {
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
            response.put("name", oauth2User.getAttribute("name"));
            response.put("email", oauth2User.getAttribute("email"));
        } else {
            // 如果没有token，也不是OAuth2User，则返回基本认证信息
            response.put("username", authentication.getName());
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        logger.info("registerUser: " + user.getUsername());
       
        try {
            User registeredUser = userService.registerUser(user);
            return ResponseEntity.ok(registeredUser);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody Map<String, String> loginRequest, HttpServletResponse response) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");
        boolean isRememberMe = Boolean.parseBoolean(loginRequest.get("isRememberMe"));
        logger.info("Attempting to log in user: " + username);
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));

            if (authentication.getPrincipal() instanceof User user) {
                String accessToken = jwtUtil.generateToken(user);
                String refreshToken = jwtUtil.generateToken(user);

                // 设置HTTP-only cookies
                jwtUtil.addTokenCookie(response, "access_token", accessToken, false);
                jwtUtil.addTokenCookie(response, "refresh_token", refreshToken, isRememberMe);

                String currentAccountSetId = user.getCurrentAccountSetId();
                List<String> accountSetIds = user.getAccountSetIds();

                // If currentAccountSetId is null, use default value or first accountSetId
                if (currentAccountSetId == null) {
                    currentAccountSetId = accountSetIds != null && !accountSetIds.isEmpty() 
                        ? accountSetIds.get(0) 
                        : "default";
                }

                // If accountSetIds is null, use empty list
                if (accountSetIds == null) {
                    accountSetIds = Collections.emptyList();
                }

                CustomAuthentication customAuth = new CustomAuthentication(
                    user, 
                    authentication.getCredentials(), 
                    authentication.getAuthorities(),
                    currentAccountSetId,
                    accountSetIds
                );

                SecurityContextHolder.getContext().setAuthentication(customAuth);

                logger.info("User logged in successfully: " + username);
                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("username", user.getUsername());
                responseBody.put("email", user.getEmail());
                responseBody.put("currentAccountSetId", currentAccountSetId);
                responseBody.put("accountSetIds", accountSetIds);
                
                return ResponseEntity.ok(responseBody);
            } else {
                logger.error("Authentication principal is not of expected User type: " + username);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
            }
        } catch (AuthenticationException e) {
            logger.error("User login failed: " + username, e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid username or password", "status", 401));
        }       
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getCookieValue(request, "refresh_token");
        if (refreshToken != null) {
            String username = jwtUtil.extractUsername(refreshToken);
            UserDetails userDetails = userService.getUserByUsername(username);
            if (jwtUtil.validateToken(refreshToken, userDetails)) {
                String newAccessToken = jwtUtil.generateToken(userDetails);
                jwtUtil.addTokenCookie(response, "access_token", newAccessToken, false);
                return ResponseEntity.ok().build();
            }
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        // 清除cookies
        deleteCookie(response, "access_token");
        deleteCookie(response, "refresh_token");
        return ResponseEntity.ok().body("Logged out successfully");
    }



    @GetMapping("/public/checkLoginStatus")
    public ResponseEntity<?> checkLoginStatus(HttpServletRequest request) {
        String refreshToken = getCookieValue(request, "refresh_token");
        if (refreshToken != null && !jwtUtil.isTokenExpired(refreshToken)) {
            return ResponseEntity.ok().body(Map.of("loggedIn", true));
        } else {
            return ResponseEntity.ok().body(Map.of("loggedIn", false));
        }
    }

    private void deleteCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}