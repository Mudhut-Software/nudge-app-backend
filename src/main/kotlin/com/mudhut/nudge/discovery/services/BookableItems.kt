package com.mudhut.nudge.discovery.services

import com.mudhut.nudge.servicesoffered.entities.ServiceOffered
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.models.Bookability
import com.mudhut.nudge.servicesoffered.repositories.ServiceOfferedRepository

/**
 * A business's publicly visible items, oldest first: ACTIVE, sold on their own and bookable.
 * Bookability cannot be expressed in the Explore queries, so counts and covers use this.
 */
internal fun ServiceOfferedRepository.bookableItems(businessId: Long): List<ServiceOffered> =
    findAllByBusinessIdAndStatusAndSoldOnItsOwnTrueOrderByCreatedAtAsc(businessId, ServiceOfferedStatus.ACTIVE)
        .filter { Bookability.blockingGroup(it) == null }
