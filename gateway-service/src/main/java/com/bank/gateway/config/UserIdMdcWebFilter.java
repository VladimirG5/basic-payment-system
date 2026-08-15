package com.bank.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Tags every log line for an authenticated request with the caller's userId (see
 * MdcContextConfig for the "restore Context into MDC" half of this). Explicitly writes into
 * Reactor's Context via .contextWrite() rather than relying on automatic ThreadLocal capture -
 * verified empirically that Hooks.enableAutomaticContextPropagation()'s automatic *capture* of
 * a mid-chain MDC.put() is not reliable across a Schedulers.boundedElastic() hop (the value was
 * correctly visible right up to chain.filter(exchange) on the same thread, then silently lost
 * once execution reached boundedElastic-scheduled code deeper in the chain). Writing into
 * Context directly sidesteps automatic capture entirely - only the registered
 * ThreadLocalAccessor's restore half is still relied on.
 *
 * chain.filter(exchange) returns Mono<Void>, which never emits a value even on success - a
 * naive .flatMap(...).switchIfEmpty(...) here would treat that as "empty" and invoke
 * chain.filter(exchange) a SECOND time for every request (verified empirically: caused
 * "response already committed" / "no request body" errors on every call). Branching inside a
 * single flatMap, decided by a sentinel default, avoids that trap.
 *
 * Must run after JwtAuthenticationWebFilter, which is what actually populates
 * ReactiveSecurityContextHolder - ordered accordingly. Public routes (auth/actuator) have no
 * security context, so requests to them simply log without a userId.
 */
@Component
public class UserIdMdcWebFilter implements WebFilter, Ordered {

    private static final String USER_ID_MDC_KEY = "userId";
    private static final String NO_AUTHENTICATED_USER = "";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> (String) context.getAuthentication().getPrincipal())
                .defaultIfEmpty(NO_AUTHENTICATED_USER)
                .flatMap(userId -> userId.equals(NO_AUTHENTICATED_USER)
                        ? chain.filter(exchange)
                        : chain.filter(exchange).contextWrite(Context.of(USER_ID_MDC_KEY, userId)));
    }
}
