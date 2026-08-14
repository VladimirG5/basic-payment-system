package com.bank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;

/**
 * ReactiveUserDetailsServiceAutoConfiguration is excluded because spring-security-core alone
 * (no spring-boot-starter-security) is enough to trigger it - it would otherwise generate and
 * log a random default-user password that nothing in this app ever uses, since auth is fully
 * handled by JwtAuthenticationWebFilter.
 */
@SpringBootApplication(exclude = ReactiveUserDetailsServiceAutoConfiguration.class)
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
