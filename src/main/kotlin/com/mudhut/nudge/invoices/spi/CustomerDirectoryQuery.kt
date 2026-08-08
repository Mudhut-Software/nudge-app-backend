package com.mudhut.nudge.invoices.spi

/**
 * Provider interface implemented by `servicerequests`: confirms a user has an actual relationship
 * with a business before invoices treats them as a valid billing target. Owned by `invoices` (the
 * consumer) — mirrors `RequestLineQuery`.
 *
 * Used to close the "any MANAGER can hand-craft an invoice to an arbitrary user id" gap on
 * `createBlank`; the generate-from-request path doesn't need it since the customer there is
 * derived from the request itself.
 */
interface CustomerDirectoryQuery {
    fun isCustomerOfBusiness(businessId: Long, userId: Long): Boolean
}
