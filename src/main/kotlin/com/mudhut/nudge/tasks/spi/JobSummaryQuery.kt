package com.mudhut.nudge.tasks.spi

/**
 * Provider interface implemented by `servicerequests`: summarize the given service requests.
 * Owned by `tasks` (the consumer) so tasks needs nothing from servicerequests internals —
 * mirrors the `discovery.spi.CompletedRequestQuery` pattern.
 *
 * The returned map contains ONLY requestIds that belong to `businessId`, so a job link is valid
 * iff `jobRequestId == null || summaries(businessId, setOf(jobRequestId)).containsKey(jobRequestId)`.
 */
interface JobSummaryQuery {
    fun summaries(businessId: Long, requestIds: Set<Long>): Map<Long, JobSummary>
}
