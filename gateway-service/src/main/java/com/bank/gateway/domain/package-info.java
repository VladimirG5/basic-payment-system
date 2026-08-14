/**
 * JPA entities that access the shared MySQL `users`/`accounts` tables owned by
 * payment-core-service directly from gateway-service. Deliberate shortcut for a 3-day
 * take-home: a proper internal API on payment-core-service would avoid two services writing
 * to the same tables, but building that distributed, partial-failure-safe call for a single
 * register endpoint wasn't worth the time here. Entity shapes are kept identical to
 * payment-core-service's on purpose - both services run with hibernate.ddl-auto=validate,
 * which fails fast on startup if the two drift apart.
 */
package com.bank.gateway.domain;
