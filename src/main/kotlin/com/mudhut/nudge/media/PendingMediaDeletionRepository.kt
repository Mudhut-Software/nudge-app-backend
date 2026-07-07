package com.mudhut.nudge.media

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PendingMediaDeletionRepository : JpaRepository<PendingMediaDeletion, Long> {
    fun findAllByStatus(status: PendingMediaDeletion.Status): List<PendingMediaDeletion>
}
