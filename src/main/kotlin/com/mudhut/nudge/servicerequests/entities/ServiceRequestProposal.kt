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
import java.time.LocalDateTime

/**
 * An alternative date and time a provider offered for a request.
 *
 * Under the current single-round rules there is at most one row per request:
 * accepting confirms the booking and rejecting declines it, both terminal. The
 * table exists rather than columns on [ServiceRequest] so that multi-round
 * negotiation would need no reshaping — a deliberate trade of a join today for
 * optionality later.
 */
@Entity
@Table(
    name = "service_request_proposal",
    // Declared here, not only in the deploy note, so dev and CI get the same
    // index prod does — findFirstByRequestIdAndOutcome is the hot path.
    indexes = [Index(name = "idx_srp_request_outcome", columnList = "request_id, outcome")],
)
class ServiceRequestProposal(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    var request: ServiceRequest? = null,

    @Column(name = "proposed_date", nullable = false)
    var proposedDate: LocalDateTime? = null,

    /** Optional "why", mirroring declineReason. Blank normalises to null. */
    @Column(columnDefinition = "TEXT")
    var note: String? = null,

    @Column(name = "proposed_at", nullable = false)
    var proposedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var outcome: ProposalOutcome = ProposalOutcome.OUTSTANDING,

    /**
     * The customer's reason for rejecting.
     *
     * Deliberately not `ServiceRequest.declineReason`, which reads as "the
     * provider's reason" everywhere it is displayed — overloading it would put
     * the customer's words under the provider's name.
     */
    @Column(name = "response_note", columnDefinition = "TEXT")
    var responseNote: String? = null,

    @Column(name = "responded_at")
    var respondedAt: LocalDateTime? = null,
)
