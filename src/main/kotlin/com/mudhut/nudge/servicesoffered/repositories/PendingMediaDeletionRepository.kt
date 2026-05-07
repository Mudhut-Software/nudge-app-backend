package com.mudhut.nudge.servicesoffered.repositories

import com.mudhut.nudge.servicesoffered.entities.PendingMediaDeletion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PendingMediaDeletionRepository : JpaRepository<PendingMediaDeletion, Long> {
    fun findAllByStatus(status: PendingMediaDeletion.Status): List<PendingMediaDeletion>
}
