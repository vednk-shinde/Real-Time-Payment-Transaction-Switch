package com.paymentswitch.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentswitch.dto.ErrorResponse;
import com.paymentswitch.ratelimit.RateLimitFilter;
import com.paymentswitch.ratelimit.RateLimiter;
import com.paymentswitch.security.JwtAuthenticationFilter;
import com.paymentswitch.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService, RateLimiter rateLimiter,
                                                   ObjectMapper objectMapper) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);
        RateLimitFilter rateLimitFilter = new RateLimitFilter(rateLimiter, objectMapper);

        return http
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/transactions").hasRole("ADMIN")
                        .requestMatchers("/api/transactions/**").hasAnyRole("ADMIN", "OPERATOR")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(res, req, HttpStatus.UNAUTHORIZED, "Missing or invalid bearer token", objectMapper))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(res, req, HttpStatus.FORBIDDEN, "Insufficient role for this operation", objectMapper)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // After JWT auth so the limiter can key on the authenticated client, not just the IP.
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private static void writeError(HttpServletResponse res, HttpServletRequest req, HttpStatus status, String message,
                                   ObjectMapper objectMapper) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(res.getOutputStream(),
                ErrorResponse.of(status.value(), status.getReasonPhrase(), message, req.getRequestURI()));
    }
}
