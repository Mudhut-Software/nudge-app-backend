package com.mudhut.nudge.servicerequests.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * One price the provider set for a whole CONFIRMED booking (estimated or to-be-confirmed only).
 *
 * Mirrors [ServiceRequestProposal]. Unlike date proposals there can be several rounds: while the
 * customer has not answered, a new proposal marks the previous one SUPERSEDED. At most one row per
 * request is OUTSTANDING, enforced in the service. Accepting copies the figure onto the request's
 * `agreedPrice*` columns; rejecting cancels the booking.
 */
@Entity
@Table(
    name = "service_request_price_proposals",
    indexes = [Index(name = "idx_srpp_request_outcome", columnList = "request_id, outcome")],
)
class ServiceRequestPriceProposal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    var request: ServiceRequest? = null,

    @Column(nullable = false, precision = 19, scale = 2)
    var amount: BigDecimal? = null,

    /** ISO-4217. */
    @Column(nullable = false, length = 3)
    var currency: String? = null,

    /** The provider's "why" ("after visiting the venue"). Blank normalises to null. */
    @Column(length = 500)
    var note: String? = null,

    @Column(name = "proposed_at", nullable = false)
    var proposedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var outcome: ProposalOutcome = ProposalOutcome.OUTSTANDING,

    /** The customer's reason for declining; also lands on the request as its cancellation reason. */
    @Column(name = "response_note", columnDefinition = "TEXT")
    var responseNote: String? = null,

    /** When it stopped being OUTSTANDING: answered, superseded, or settled by a cancellation. */
    @Column(name = "responded_at")
    var respondedAt: LocalDateTime? = null,
)
