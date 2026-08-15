/**
 * JPA entities that read the shared MySQL `users`/`accounts`/`transactions` tables owned by
 * payment-core-service directly from gateway-service, for gateway's own read paths
 * (AccountService's balance/history endpoints) and TransferInitiationExecutor's ownership/
 * balance checks at initiate time. Deliberate shortcut for a take-home: a proper internal API
 * on payment-core-service would avoid two services touching the same tables, but building that
 * for a handful of read-only endpoints wasn't worth the time here. Entity shapes are kept
 * identical to payment-core-service's on purpose - both services run with
 * hibernate.ddl-auto=validate, which fails fast on startup if the two drift apart.
 *
 * Gateway never writes through these entities: transfers are written exclusively by
 * payment-core-service's own locked TransferService (via the internal /internal/transfers
 * endpoint), and user/account provisioning (register, login, change-password) has its own
 * proper internal API on payment-core-service (/internal/users*, see auth-service's
 * CoreUserClient) rather than this shortcut - that write path used to go through this package
 * too, until it was extracted into auth-service.
 */
package com.bank.gateway.domain;
