package com.bank.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;

/**
 * ReactiveUserDetailsServiceAutoConfiguration is excluded for the same reason as in
 * gateway-service: spring-security-core alone (no spring-boot-starter-security) is enough to
 * trigger it, generating an unused default-user password nothing here ever uses.
 */
@SpringBootApplication(exclude = ReactiveUserDetailsServiceAutoConfiguration.class)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
