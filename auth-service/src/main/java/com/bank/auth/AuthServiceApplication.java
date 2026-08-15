package com.bank.auth;

import com.bank.auth.config.MdcContextConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import reactor.core.publisher.Hooks;

/**
 * ReactiveUserDetailsServiceAutoConfiguration is excluded for the same reason as in
 * gateway-service: spring-security-core alone (no spring-boot-starter-security) is enough to
 * trigger it, generating an unused default-user password nothing here ever uses.
 */
@SpringBootApplication(exclude = ReactiveUserDetailsServiceAutoConfiguration.class)
public class AuthServiceApplication {

    static {
        // See gateway-service's GatewayServiceApplication for why this has to run this early
        // (a Spring-managed @PostConstruct bean was verified too late to reliably apply it).
        MdcContextConfig.registerMdcKeys();
        Hooks.enableAutomaticContextPropagation();
    }

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
