package com.paymentswitch.controller;

import com.paymentswitch.config.AppProperties;
import com.paymentswitch.config.SecurityConfig;
import com.paymentswitch.dto.LoginResponse;
import com.paymentswitch.dto.TransactionResponse;
import com.paymentswitch.exception.DuplicateTransactionException;
import com.paymentswitch.exception.TransactionNotFoundException;
import com.paymentswitch.model.Channel;
import com.paymentswitch.model.TransactionStatus;
import com.paymentswitch.ratelimit.RateLimiter;
import com.paymentswitch.security.JwtService;
import com.paymentswitch.service.AuthService;
import com.paymentswitch.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MVC slice: real security chain (JWT filter, role rules, rate limiter,
 * 401/403 handlers) and real exception mapping, with the services mocked.
 */
@WebMvcTest(controllers = {TransactionController.class, AuthController.class})
@Import({SecurityConfig.class, JwtService.class, RateLimiter.class, TransactionControllerTest.Config.class})
class TransactionControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private static final String VALID_BODY = """
            {"idempotencyKey":"order-1001","cardNumber":"4111111111111111","merchantId":"MERCHANT-42",
             "acquirerId":"ACQUIRER-7","amount":150.00,"currency":"USD","channel":"ECOMMERCE"}
            """;

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @MockBean TransactionService transactionService;
    @MockBean AuthService authService;

    private String bearer(String role) {
        return "Bearer " + jwtService.issue(role.toLowerCase(), List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private static TransactionResponse approved(UUID id) {
        return new TransactionResponse(id, "order-1001", "****1111", "MERCHANT-42", "ACQUIRER-7", "ISSUER-VISA-SIM",
                new BigDecimal("150.00"), "USD", Channel.ECOMMERCE, TransactionStatus.APPROVED, "00", null,
                Instant.now(), Instant.now());
    }

    @Test
    void submitReturns201WithLocationAndMaskedCard() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.process(any())).thenReturn(approved(id));

        mvc.perform(post("/api/transactions").header("Authorization", bearer("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/transactions/" + id))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.maskedCardNumber").value("****1111"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("4111111111111111"))));
    }

    @Test
    void missingTokenIs401() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(transactionService);
    }

    @Test
    void invalidTokenIs401() throws Exception {
        mvc.perform(get("/api/transactions/" + UUID.randomUUID()).header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void operatorCannotListAllTransactions() throws Exception {
        mvc.perform(get("/api/transactions").header("Authorization", bearer("OPERATOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        String body = """
                {"idempotencyKey":"","cardNumber":"4111-1111","merchantId":"M","acquirerId":"A",
                 "amount":-5,"currency":"usd","channel":"ECOMMERCE"}
                """;
        mvc.perform(post("/api/transactions").header("Authorization", bearer("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.idempotencyKey").exists())
                .andExpect(jsonPath("$.fieldErrors.cardNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.currency").exists());
        verifyNoInteractions(transactionService);
    }

    @Test
    void unknownChannelIs400() throws Exception {
        mvc.perform(post("/api/transactions").header("Authorization", bearer("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY.replace("ECOMMERCE", "CARRIER_PIGEON")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateIdempotencyKeyIs409() throws Exception {
        when(transactionService.process(any())).thenThrow(new DuplicateTransactionException("order-1001"));

        mvc.perform(post("/api/transactions").header("Authorization", bearer("OPERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("order-1001")));
    }

    @Test
    void unknownTransactionIs404AndMalformedIdIs400() throws Exception {
        UUID id = UUID.randomUUID();
        when(transactionService.get(id)).thenThrow(new TransactionNotFoundException(id));

        mvc.perform(get("/api/transactions/" + id).header("Authorization", bearer("ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/transactions/not-a-uuid").header("Authorization", bearer("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginIsPublicAndReturnsToken() throws Exception {
        when(authService.login(any())).thenReturn(LoginResponse.bearer("jwt-token", 3600));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"operator123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void badCredentialsAre401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("nope"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
