package com.mudhut.nudge.servicerequests.events

import java.math.BigDecimal
import java.time.LocalDateTime

enum class PriceEventKind {
    /** The provider set a new price; the customer needs telling. */
    PROPOSED,

    /** The customer accepted it; the provider needs telling. */
    ACCEPTED,
}

/**
 * Published when a provider proposes a price for a booking or the customer accepts one.
 *
 * A decline is not a price event: it cancels the booking and goes out as a
 * [ServiceRequestStatusChangedEvent] to CANCELLED with the customer's reason.
 *
 * Kept flat and small because Modulith persists it as JSON in
 * `event_publication.serialized_event` (varchar(255) outside dev/test unless widened).
 */
data class ServiceRequestPriceEvent(
    val requestId: Long,
    val kind: PriceEventKind,
    val amount: BigDecimal,
    val currency: String,
    val note: String?,
    val businessId: Long,
    val businessName: String,
    /** Provider-bound recipient, resolved at publish time. */
    val ownerEmail: String,
    /** Customer-bound recipient; consumers resolve the address via `users.repositories`. */
    val customerId: Long,
    val customerName: String,
    val serviceTitle: String?,
    /** UTC. */
    val changedAt: LocalDateTime,
)
