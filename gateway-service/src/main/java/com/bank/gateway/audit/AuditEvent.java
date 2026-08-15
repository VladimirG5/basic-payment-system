package com.bank.gateway.audit;

import java.time.Instant;

/**
 * One security-relevant attempt-level event: an OTP validation outcome, a transfer
 * initiate/confirm attempt, etc. Deliberately separate from the immutable `transactions`
 * ledger in payment-core-service, which only records completed money movement - this captures
 * attempts, including the failed/expired/rate-limited ones the ledger never sees.
 */
public record AuditEvent(Instant timestamp, String actor, String action, String outcome, String target, String detail) {
}
