package com.mudhut.nudge.utils.exceptions

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus

class InvalidStateTransitionException(
    val from: ServiceRequestStatus,
    val to: ServiceRequestStatus,
    message: String = "Cannot transition from $from to $to",
) : RuntimeException(message)
