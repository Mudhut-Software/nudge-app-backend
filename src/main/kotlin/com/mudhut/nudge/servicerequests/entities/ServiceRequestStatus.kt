package com.mudhut.nudge.servicerequests.entities

enum class ServiceRequestStatus {
    DRAFT,
    PENDING,

    /**
     * The provider offered a different date/time and is waiting on the customer.
     * Reached only from PENDING — rescheduling a CONFIRMED booking is a separate
     * feature, deliberately out of scope.
     */
    REVISION_REQUESTED,
    CONFIRMED,
    DECLINED,
    COMPLETED,
    CANCELLED,
}
