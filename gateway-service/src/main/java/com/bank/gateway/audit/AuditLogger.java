package com.bank.gateway.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Writes one JSON line per security-relevant event to the dedicated "AUDIT" logger, routed by
 * logback-spring.xml to its own rolling file (audit.log) - additivity="false" there keeps it
 * out of the regular application log. actor/target/detail are all nullable: actor is whatever
 * identifies the caller at that point (email pre-auth, userId once known); target and detail
 * fill in as the situation allows.
 */
@Component
public class AuditLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    private final ObjectMapper objectMapper;

    public AuditLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void log(String actor, String action, String outcome, String target, String detail) {
        AuditEvent event = new AuditEvent(Instant.now(), actor, action, outcome, target, detail);
        try {
            AUDIT_LOG.info(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException ex) {
            AUDIT_LOG.warn("Failed to serialize audit event: action={} outcome={}", action, outcome);
        }
    }
}
