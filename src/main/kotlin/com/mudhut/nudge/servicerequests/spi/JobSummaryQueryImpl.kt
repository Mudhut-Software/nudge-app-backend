package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.tasks.spi.JobSummary
import com.mudhut.nudge.tasks.spi.JobSummaryQuery
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Supplies tasks' job summaries without tasks depending on servicerequests internals. */
@Component
class JobSummaryQueryImpl(
    private val repo: ServiceRequestRepository,
) : JobSummaryQuery {

    @Transactional(readOnly = true)
    override fun summaries(businessId: Long, requestIds: Set<Long>): Map<Long, JobSummary> {
        if (requestIds.isEmpty()) return emptyMap()
        return repo.findByBusinessIdAndIdIn(businessId, requestIds).associate { req ->
            val id = req.id!!
            id to JobSummary(
                requestId = id,
                title = req.items.firstOrNull()?.snapshotTitle ?: "Request #$id",
                requestedDate = req.requestedDate,
                status = req.status.name,
            )
        }
    }
}
