package com.paymentswitch.service;

import com.paymentswitch.dto.LoginRequest;
import com.paymentswitch.dto.LoginResponse;
import com.paymentswitch.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Verifies credentials (BCrypt) and issues a signed JWT. Throws BadCredentialsException on failure. */
    public LoginResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        String token = jwtService.issue(auth.getName(), auth.getAuthorities());
        return LoginResponse.bearer(token, jwtService.expirationSeconds());
    }
}
