package com.bank.gateway.config;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;

/**
 * MDC is a ThreadLocal; WebFlux hops threads mid-request, so a plain MDC.put() in a WebFilter
 * wouldn't survive to the log lines emitted later in the chain. registerMdcKeys() registers
 * "userId" as a key that Reactor's automatic context propagation
 * (Hooks.enableAutomaticContextPropagation(), called from GatewayServiceApplication's static
 * initializer - see there for why it has to happen that early, not from a Spring bean) knows
 * how to snapshot into the Reactor Context and restore onto MDC whenever execution resumes on
 * a different thread. See UserIdMdcWebFilter for where "userId" actually gets set.
 */
public final class MdcContextConfig {

    private static final String USER_ID_MDC_KEY = "userId";

    private MdcContextConfig() {
    }

    public static void registerMdcKeys() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                USER_ID_MDC_KEY,
                () -> MDC.get(USER_ID_MDC_KEY),
                value -> MDC.put(USER_ID_MDC_KEY, value),
                () -> MDC.remove(USER_ID_MDC_KEY));
    }
}
