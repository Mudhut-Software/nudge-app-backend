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

    /**
     * A confirmed booking whose service window passed with no communication from
     * anybody: the provider never marked it complete, neither side cancelled, and
     * the customer — asked 24 hours later — said it did not happen.
     *
     * Records an unsuccessful scheduling, not a grievance. Terminal, and carries
     * no reason: the defining condition is the absence of communication.
     */
    NO_SHOW,
    CANCELLED,
}
