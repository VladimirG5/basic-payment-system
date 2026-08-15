package com.bank.auth.audit;

import java.time.Instant;

/**
 * One security-relevant attempt-level event for identity operations: register, login,
 * change-password. Mirrors gateway-service's identically-named class - see there for why this
 * exists separately from the transactions ledger.
 */
public record AuditEvent(Instant timestamp, String actor, String action, String outcome, String target, String detail) {
}
