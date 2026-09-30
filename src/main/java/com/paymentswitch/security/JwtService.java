package com.paymentswitch.security;

import com.paymentswitch.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Issues and validates HS256-signed JWTs. Tokens carry the username and
 * roles, so authenticating a request needs no database lookup.
 */
@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = Duration.ofMinutes(props.jwt().expirationMinutes());
    }

    public String issue(String username, Collection<? extends GrantedAuthority> authorities) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLES_CLAIM, authorities.stream().map(GrantedAuthority::getAuthority).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Returns the verified principal, or empty for a missing, expired, tampered or malformed token. */
    public Optional<AuthenticatedUser> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            List<?> roles = claims.get(ROLES_CLAIM, List.class);
            List<SimpleGrantedAuthority> authorities = roles == null ? List.of()
                    : roles.stream().map(Object::toString).map(SimpleGrantedAuthority::new).toList();
            return Optional.of(new AuthenticatedUser(claims.getSubject(), authorities));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long expirationSeconds() {
        return ttl.toSeconds();
    }

    public record AuthenticatedUser(String username, List<SimpleGrantedAuthority> authorities) {
    }
}
