package com.mudhut.nudge.utils.exceptions

import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus

class InvalidStateTransitionException(
    val from: ServiceRequestStatus,
    val to: ServiceRequestStatus,
) : RuntimeException("Cannot transition from $from to $to")
