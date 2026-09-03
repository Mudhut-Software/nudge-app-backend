package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequestProposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRequestProposalRepository : JpaRepository<ServiceRequestProposal, Long> {

    /**
     * The one proposal still awaiting a reply, if any.
     *
     * Backed by idx_srp_request_outcome. `findFirst` rather than `find` because
     * the schema permits more than one row even though the service does not.
     */
    fun findFirstByRequestIdAndOutcome(
        requestId: Long,
        outcome: ProposalOutcome,
    ): ServiceRequestProposal?

    /** Most recent proposal whatever its outcome, for the response DTO. */
    fun findFirstByRequestIdOrderByProposedAtDesc(requestId: Long): ServiceRequestProposal?
}
