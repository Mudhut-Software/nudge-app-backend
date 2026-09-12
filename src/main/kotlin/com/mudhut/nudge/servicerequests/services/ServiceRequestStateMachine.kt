package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CANCELLED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CONFIRMED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DECLINED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.NO_SHOW
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.PENDING
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.REVISION_REQUESTED
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException

object ServiceRequestStateMachine {

    private val allowed: Map<ServiceRequestStatus, Set<ServiceRequestStatus>> = mapOf(
        DRAFT to setOf(PENDING),
        PENDING to setOf(DRAFT, CONFIRMED, DECLINED, CANCELLED, REVISION_REQUESTED),
        // Accept confirms, reject declines, and either side may still cancel.
        // Both outcomes are terminal: the customer cannot counter-propose.
        REVISION_REQUESTED to setOf(CONFIRMED, DECLINED, CANCELLED),
        // NO_SHOW is the customer's answer to the completion prompt 24h after
        // the service time: the provider never marked it done and nobody cancelled.
        CONFIRMED to setOf(COMPLETED, CANCELLED, NO_SHOW),
        DECLINED to emptySet(),
        COMPLETED to emptySet(),
        CANCELLED to emptySet(),
        NO_SHOW to emptySet(),
    )

    fun requireTransition(current: ServiceRequestStatus, target: ServiceRequestStatus) {
        val targets = allowed[current].orEmpty()
        if (target !in targets) {
            throw InvalidStateTransitionException(current, target)
        }
    }
}
