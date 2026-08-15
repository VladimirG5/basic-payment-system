package com.bank.core.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * payment-core-service has no authenticated principal of its own - it's internal-only, trusted
 * via the private Docker network, with no JWT validation here. So unlike a typical MDC filter
 * (which would read SecurityContextHolder), this reads a caller-supplied X-User-Id header:
 * gateway-service and auth-service set it on outbound calls to /internal/* when the actor is
 * already known (see PaymentCoreClient/CoreUserClient), giving cross-service trace correlation.
 */
@Component
public class UserIdMdcFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ID_MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String userId = request.getHeader(USER_ID_HEADER);
            if (userId != null && !userId.isBlank()) {
                MDC.put(USER_ID_MDC_KEY, userId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID_MDC_KEY);
        }
    }
}
