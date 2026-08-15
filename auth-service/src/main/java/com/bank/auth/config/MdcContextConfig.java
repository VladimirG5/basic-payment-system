package com.bank.auth.config;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;

/**
 * Same mechanism as gateway-service's MdcContextConfig (see that class and
 * AuthServiceApplication's static initializer for the full explanation). auth-service tracks
 * two MDC keys, not one: "email" (set as soon as it's parsed from the request body - most
 * endpoints here are pre-authentication) and "userId" (set once identity is established).
 */
public final class MdcContextConfig {

    private MdcContextConfig() {
    }

    public static void registerMdcKeys() {
        registerKey("userId");
        registerKey("email");
    }

    private static void registerKey(String key) {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                key, () -> MDC.get(key), value -> MDC.put(key, value), () -> MDC.remove(key));
    }
}
