package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRequestPriceProposalRepository : JpaRepository<ServiceRequestPriceProposal, Long> {

    /** The one price awaiting the customer's answer, if any. Backed by idx_srpp_request_outcome. */
    fun findFirstByRequestIdAndOutcome(
        requestId: Long,
        outcome: ProposalOutcome,
    ): ServiceRequestPriceProposal?

    /** Most recent proposal whatever its outcome, for the response DTO; id breaks a proposedAt tie. */
    fun findFirstByRequestIdOrderByProposedAtDescIdDesc(requestId: Long): ServiceRequestPriceProposal?
}
