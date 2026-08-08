package com.mudhut.nudge.invoices.spi

/**
 * Provider interface implemented by `servicerequests`: derive invoice line data from a service
 * request. Owned by `invoices` (the consumer) — mirrors `tasks.spi.JobSummaryQuery`.
 *
 * Returns null unless the request exists, belongs to `businessId`, and is COMPLETED — so a non-null
 * result doubles as ownership + eligibility validation.
 */
interface RequestLineQuery {
    fun forRequest(businessId: Long, requestId: Long): RequestLineData?
}
