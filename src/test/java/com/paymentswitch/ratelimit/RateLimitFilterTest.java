package com.paymentswitch.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentswitch.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(
            new RateLimiter(new AppProperties(null, null, new AppProperties.RateLimit(2, 0.001), null, true)),
            new ObjectMapper().registerModule(new JavaTimeModule()));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse call(String method, String path, String remoteAddr) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setServletPath(path);
        req.setRemoteAddr(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void returns429WithRetryAfterOnceTheBucketIsEmpty() throws Exception {
        assertThat(call("POST", "/api/transactions", "10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/transactions", "10.0.0.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse limited = call("POST", "/api/transactions", "10.0.0.1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getContentAsString()).contains("Rate limit exceeded");
    }

    @Test
    void clientsHaveIndependentBudgets() throws Exception {
        call("POST", "/api/transactions", "10.0.0.1");
        call("POST", "/api/transactions", "10.0.0.1");

        assertThat(call("POST", "/api/transactions", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void authenticatedUsersAreKeyedByUsernameNotIp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", null, List.of()));
        call("POST", "/api/transactions", "10.0.0.1");
        call("POST", "/api/transactions", "10.0.0.2");

        assertThat(call("POST", "/api/transactions", "10.0.0.3").getStatus()).isEqualTo(429);
    }

    @Test
    void onlyTheIngestionEndpointIsLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(call("GET", "/api/transactions/abc", "10.0.0.9").getStatus()).isEqualTo(200);
        }
    }
}
