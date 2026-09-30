package com.paymentswitch.security;

import com.paymentswitch.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static JwtService service(String secret, long minutes) {
        return new JwtService(new AppProperties(null, new AppProperties.Jwt(secret, minutes), null, null, true));
    }

    private final JwtService jwt = service("unit-test-secret-unit-test-secret-012345", 5);

    @Test
    void issuedTokenRoundTripsUsernameAndRoles() {
        String token = jwt.issue("operator", List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));

        JwtService.AuthenticatedUser user = jwt.parse(token).orElseThrow();
        assertThat(user.username()).isEqualTo("operator");
        assertThat(user.authorities()).extracting(SimpleGrantedAuthority::getAuthority).containsExactly("ROLE_OPERATOR");
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.issue("operator", List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\",\"roles\":[\"ROLE_ADMIN\"]}".getBytes());

        assertThat(jwt.parse(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = service("another-secret-another-secret-0123456789", 5)
                .issue("admin", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertThat(jwt.parse(foreign)).isEmpty();
    }

    @Test
    void rejectsExpiredTokenAndGarbage() {
        String expired = service("unit-test-secret-unit-test-secret-012345", -1)
                .issue("operator", List.of());
        assertThat(jwt.parse(expired)).isEmpty();
        assertThat(jwt.parse("not-a-jwt")).isEmpty();
    }

    @Test
    void refusesWeakSecrets() {
        assertThatThrownBy(() -> service("too-short", 5)).isInstanceOf(IllegalStateException.class);
    }
}
