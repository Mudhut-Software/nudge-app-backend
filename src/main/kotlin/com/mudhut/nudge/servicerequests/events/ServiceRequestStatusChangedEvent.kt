package com.mudhut.nudge.servicerequests.events

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import java.time.LocalDateTime

/**
 * Published on every service-request status transition.
 *
 * One event rather than one per transition: the status set is still growing
 * (NO_SHOW and REVISION_REQUESTED are planned), and a per-transition design
 * would need a new class and a new consumer method for each. Consumers branch
 * on [to] instead.
 *
 * [reason] carries whichever reason applies to this transition — the provider's
 * decline reason or the customer's cancellation reason — so a consumer never
 * needs a second lookup to explain what happened.
 */
data class ServiceRequestStatusChangedEvent(
    val requestId: Long,
    val from: ServiceRequestStatus,
    val to: ServiceRequestStatus,
    val businessId: Long,
    val businessName: String,
    /** Provider-bound recipient, resolved at publish time so consumers need no lookup. */
    val ownerEmail: String,
    /** Customer-bound recipient; consumers resolve the address via `users.repositories`. */
    val customerId: Long,
    val customerName: String,
    val serviceTitle: String?,
    val requestedDate: LocalDateTime?,
    val reason: String?,
    val changedAt: LocalDateTime,
    /** Which side caused the change. Decides the recipient alongside [to]. */
    val actor: RequestActor,
    /** Set only for REVISION_REQUESTED — the time being offered. */
    val proposedDate: LocalDateTime? = null,
)
