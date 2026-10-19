package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import com.mudhut.nudge.servicerequests.spi.SubscriptionPlanQuery
import com.mudhut.nudge.utils.exceptions.QuotaExceededException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset

/**
 * Statuses that hold a unit of the allowance.
 *
 * REVISION_REQUESTED is included because proposing a time is a commitment the
 * provider must honour if accepted — and making it cost quota means they will
 * not offer more slots than they can serve.
 *
 * DRAFT and PENDING never consumed one. DECLINED and CANCELLED released theirs
 * by leaving this set, which is why no release logic exists anywhere.
 */
private val QUOTA_CONSUMING = listOf(
    ServiceRequestStatus.REVISION_REQUESTED,
    ServiceRequestStatus.CONFIRMED,
    ServiceRequestStatus.COMPLETED,
    ServiceRequestStatus.NO_SHOW,
)

@Service
class JobQuotaService(
    private val repo: ServiceRequestRepository,
    private val planQuery: SubscriptionPlanQuery,
    @Value("\${nudge.quota.free-monthly-jobs:10}") private val freeLimit: Int,
) {

    /** @throws QuotaExceededException when the free allowance is spent. */
    fun requireCapacity(ownerId: Long) {
        val snapshot = snapshotFor(ownerId)
        if (snapshot.remaining != null && snapshot.remaining <= 0) {
            throw QuotaExceededException("This business can't take more requests at the moment.")
        }
    }

    fun snapshotFor(ownerId: Long): QuotaSnapshot {
        val plan = planQuery.planFor(ownerId)
        // Unlimited means the count is not merely large — it is not asked for.
        if (plan.unlimited) {
            return QuotaSnapshot(true, null, 0, null, plan.periodStart)
        }
        val used = repo.countQuotaConsuming(
            ownerId,
            QUOTA_CONSUMING,
            plan.periodStart.atOffset(ZoneOffset.UTC).toLocalDateTime(),
        )
        // Never negative: a customer accepting an outstanding proposal consumes a
        // unit without the provider acting, so legitimate overage happens.
        return QuotaSnapshot(false, freeLimit, used, maxOf(0, freeLimit - used), plan.periodStart)
    }
}

/** Null limit and remaining mean unlimited — never a sentinel. */
data class QuotaSnapshot(
    val unlimited: Boolean,
    val limit: Int?,
    val used: Int,
    val remaining: Int?,
    val periodStart: Instant,
)
