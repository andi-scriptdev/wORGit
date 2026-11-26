package edu.bsu.worgit.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import edu.bsu.worgit.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * HS256 bearer tokens. The token carries the role for the app's routing, but the server never trusts
 * it: JwtAuthFilter re-reads the role from the database on every request.
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final java.time.Duration ttl;
    private final String issuer;
    private final Clock clock;

    public JwtService(AppProperties props, Clock clock) {
        String secret = props.jwt().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("worgit.jwt.secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = props.jwt().ttl();
        this.issuer = props.jwt().issuer();
        this.clock = clock;
    }

    public IssuedToken issue(UserAccount user) {
        Instant now = clock.instant();
        Instant expires = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(user.userId())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expires);
    }

    /** The user id of a valid, unexpired token; empty for anything else. */
    public Optional<String> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
