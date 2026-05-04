package com.mudhut.nudge.services.repositories

import com.mudhut.nudge.services.entities.ServiceImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceImageRepository : JpaRepository<ServiceImage, Long>
