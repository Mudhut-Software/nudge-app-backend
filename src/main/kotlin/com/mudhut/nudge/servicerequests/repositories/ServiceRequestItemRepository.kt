package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ServiceRequestItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceRequestItemRepository : JpaRepository<ServiceRequestItem, Long>
