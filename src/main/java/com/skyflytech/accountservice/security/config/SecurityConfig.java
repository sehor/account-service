package com.skyflytech.accountservice.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skyflytech.accountservice.security.jwt.JwtTokenFilter;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final String jwtSecret;
    private final JwtTokenFilter jwtTokenFilter;

    public SecurityConfig(@Value("${spring.security.oauth2.resourceserver.jwt.secret}") String jwtSecret,
                          JwtTokenFilter jwtTokenFilter) {
        this.jwtSecret = jwtSecret;
        this.jwtTokenFilter = jwtTokenFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable
/*                 .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())  // 这里修改为 withHttpOnlyFalse()
                .ignoringRequestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/refresh", "/api/auth/logout") */
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/user",
                                "/error",
                                "/api/auth/public/**",
                                "/v3/api-docs/**", // 开放 Swagger API 文档路径
                                "/swagger-ui/**",  // 开放 Swagger UI 路径
                                "/swagger-ui.html",//开放swagger-ui.html
                                "/doc.html"//开放knife4j文档
                        )
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()).jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType("application/json;charset=UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            String message = "Authentication failed: " + authException.getMessage();
                            response.getWriter().write(
                                    new ObjectMapper().writeValueAsString(Map.of("message", message, "status", 401)));
                        }))
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtTokenFilter, UsernamePasswordAuthenticationFilter.class); // 添加 JwtTokenFilter

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173")); // 替换为您的前端 URL
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Set-Cookie", "X-XSRF-TOKEN"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

            String message;
            if (exception instanceof BadCredentialsException) {
                message = "Invalid username or password";
            } else if (exception instanceof LockedException) {
                message = "Account is locked";
            } else if (exception instanceof DisabledException) {
                message = "Account is disabled";
            } else {
                message = "Authentication failed";
            }

            response.getWriter()
                    .write(new ObjectMapper().writeValueAsString(Map.of("message", message, "status", 401)));
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(Keys.hmacShaKeyFor(jwtSecret.getBytes())).build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        //grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtAuthenticationConverter;
    }
}