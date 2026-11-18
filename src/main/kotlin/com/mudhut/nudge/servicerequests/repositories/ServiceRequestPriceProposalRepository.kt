package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRequestPriceProposalRepository : JpaRepository<ServiceRequestPriceProposal, Long> {

    /**
     * Every proposal with this outcome. Backed by idx_srpp_request_outcome. A list, not findFirst:
     * the service keeps one OUTSTANDING per request, but settling and superseding act on all of
     * them so a stray duplicate can never wedge a booking.
     */
    fun findAllByRequestIdAndOutcome(
        requestId: Long,
        outcome: ProposalOutcome,
    ): List<ServiceRequestPriceProposal>

    /** Most recent proposal whatever its outcome, for the response DTO; id breaks a proposedAt tie. */
    fun findFirstByRequestIdOrderByProposedAtDescIdDesc(requestId: Long): ServiceRequestPriceProposal?
}
