package com.bank.gateway.config;

import com.bank.gateway.exception.InvalidCredentialsException;
import com.bank.gateway.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Validates the JWT on every route except /auth/register, /auth/login and /actuator/**, and
 * pushes the resulting Authentication into the reactive security context via contextWrite - the
 * standard way to populate ReactiveSecurityContextHolder from a plain WebFilter without pulling
 * in the full Spring Security reactive filter chain (spring-boot-starter-security), which isn't
 * needed for a single stateless bearer-token check.
 */
@Component
public class JwtAuthenticationWebFilter implements WebFilter, Ordered {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationWebFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (isExcluded(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing or malformed Authorization header");
        }

        try {
            Claims claims = jwtService.parseAndValidate(header.substring("Bearer ".length()));
            Authentication authentication = toAuthentication(claims);
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        } catch (InvalidCredentialsException ex) {
            return unauthorized(exchange, ex.getMessage());
        }
    }

    private boolean isExcluded(String path) {
        return path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login")
                || path.startsWith("/actuator");
    }

    private Authentication toAuthentication(Claims claims) {
        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        List<GrantedAuthority> authorities = roles == null
                ? List.of()
                : roles.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Invalid Credentials");
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(problem);
        } catch (Exception ex) {
            bytes = ("{\"status\":401,\"detail\":\"" + detail + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
