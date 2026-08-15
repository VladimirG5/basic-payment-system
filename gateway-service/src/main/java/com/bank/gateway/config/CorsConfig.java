package com.bank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Without this, WebFlux's own preflight handling rejects any cross-origin OPTIONS request with
 * 403 before it ever reaches JwtAuthenticationWebFilter - curl-based checks never noticed since
 * curl doesn't send/enforce preflight requests, only a real browser does.
 */
@Configuration
public class CorsConfig implements WebFluxConfigurer {

    @Value("${cors.allowed-origins:http://localhost:3000}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
