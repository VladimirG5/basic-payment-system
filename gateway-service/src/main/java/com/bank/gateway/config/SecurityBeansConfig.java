package com.bank.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Only spring-security-crypto is on the classpath (not the full spring-boot-starter-security),
 * so there is no autoconfigured PasswordEncoder bean and no reactive security auto-config
 * fighting with the auth endpoints built here ahead of commit 8's JWT WebFilter.
 */
@Configuration
public class SecurityBeansConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
