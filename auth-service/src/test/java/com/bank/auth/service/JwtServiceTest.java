package com.bank.auth.service;

import com.bank.auth.exception.InvalidCredentialsException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plain unit tests, no mocking - JwtService only wraps jjwt. The round trip and the failure
 * modes it's designed to remap into InvalidCredentialsException are what matter here.
 */
class JwtServiceTest {

    private static final String SECRET = "dev-only-signing-key-change-me-before-any-real-deploy-32b";

    private final JwtService jwtService = new JwtService(SECRET, 3600L);

    @Test
    void generateTokenThenParseAndValidateRoundTripsTheClaims() {
        String token = jwtService.generateToken(1L, "ada@example.com", List.of("USER"));

        Claims claims = jwtService.parseAndValidate(token);

        assertEquals("1", claims.getSubject());
        assertEquals("ada@example.com", claims.get("email", String.class));
        assertEquals(List.of("USER"), claims.get("roles", List.class));
    }

    @Test
    void getExpirationSecondsReturnsWhatWasConfigured() {
        assertEquals(3600L, jwtService.getExpirationSeconds());
    }

    @Test
    void parseAndValidateRejectsAnExpiredToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(120);
        String expired = Jwts.builder()
                .subject("1")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        assertThrows(InvalidCredentialsException.class, () -> jwtService.parseAndValidate(expired));
    }

    @Test
    void parseAndValidateRejectsATokenSignedWithADifferentKey() {
        JwtService otherIssuer = new JwtService("a-completely-different-signing-key-value-32b", 3600L);
        String tokenFromOtherIssuer = otherIssuer.generateToken(1L, "ada@example.com", List.of("USER"));

        assertThrows(InvalidCredentialsException.class, () -> jwtService.parseAndValidate(tokenFromOtherIssuer));
    }

    @Test
    void parseAndValidateRejectsAMalformedToken() {
        assertThrows(InvalidCredentialsException.class, () -> jwtService.parseAndValidate("not-a-jwt"));
    }
}
