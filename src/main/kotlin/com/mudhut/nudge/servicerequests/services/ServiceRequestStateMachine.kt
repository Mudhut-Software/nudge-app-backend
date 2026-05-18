package com.mudhut.nudge.servicerequests.services

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CANCELLED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.COMPLETED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.CONFIRMED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DECLINED
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.DRAFT
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus.PENDING
import com.mudhut.nudge.utils.exceptions.InvalidStateTransitionException

object ServiceRequestStateMachine {

    private val allowed: Map<ServiceRequestStatus, Set<ServiceRequestStatus>> = mapOf(
        DRAFT to setOf(PENDING),
        PENDING to setOf(DRAFT, CONFIRMED, DECLINED, CANCELLED),
        CONFIRMED to setOf(COMPLETED, CANCELLED),
        DECLINED to emptySet(),
        COMPLETED to emptySet(),
        CANCELLED to emptySet(),
    )

    fun requireTransition(current: ServiceRequestStatus, target: ServiceRequestStatus) {
        val targets = allowed[current].orEmpty()
        if (target !in targets) {
            throw InvalidStateTransitionException(current, target)
        }
    }
}
