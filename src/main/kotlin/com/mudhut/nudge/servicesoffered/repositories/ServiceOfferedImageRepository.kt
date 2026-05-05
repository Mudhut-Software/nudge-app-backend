package com.mudhut.nudge.servicesoffered.repositories

import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedImage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ServiceOfferedImageRepository : JpaRepository<ServiceOfferedImage, Long>
