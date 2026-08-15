package com.bank.gateway;

import com.bank.gateway.config.MdcContextConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import reactor.core.publisher.Hooks;

/**
 * ReactiveUserDetailsServiceAutoConfiguration is excluded because spring-security-core alone
 * (no spring-boot-starter-security) is enough to trigger it - it would otherwise generate and
 * log a random default-user password that nothing in this app ever uses, since auth is fully
 * handled by JwtAuthenticationWebFilter.
 */
@SpringBootApplication(exclude = ReactiveUserDetailsServiceAutoConfiguration.class)
public class GatewayServiceApplication {

    static {
        // Reactor's docs call for enabling this as early as possible, before any reactive
        // pipelines are assembled - a Spring-managed @PostConstruct bean runs too late for it
        // to reliably apply (verified empirically: MDC tags came back empty until this moved
        // into a static initializer, which runs at class-load, ahead of SpringApplication.run).
        MdcContextConfig.registerMdcKeys();
        Hooks.enableAutomaticContextPropagation();
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
