package com.bank.gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(JwtAuthenticationWebFilterTest.DummyProtectedController.class)
class JwtAuthenticationWebFilterTest {

    private static final String PROTECTED_PATH = "/api/v1/test/protected";

    @org.springframework.beans.factory.annotation.Autowired
    private WebTestClient webTestClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void noTokenIsRejected() {
        webTestClient.get().uri(PROTECTED_PATH)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void expiredTokenIsRejected() {
        String expiredToken = buildToken(Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void validTokenIsAccepted() {
        String validToken = buildToken(Instant.now(), Instant.now().plusSeconds(3600));

        webTestClient.get().uri(PROTECTED_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }

    private String buildToken(Instant issuedAt, Instant expiresAt) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("1")
                .claim("email", "test@example.com")
                .claim("roles", List.of("USER"))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    @RestController
    static class DummyProtectedController {

        @GetMapping(PROTECTED_PATH)
        public Mono<String> protectedEndpoint() {
            return Mono.just("ok");
        }
    }
}
