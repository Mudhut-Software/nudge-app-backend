package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.servicesoffered.entities.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.repositories.PendingMediaDeletionRepository
import com.mudhut.nudge.utils.exceptions.MediaDeletionException
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class MediaCleanupJob(
    private val pendingRepository: PendingMediaDeletionRepository,
    private val mediaService: MediaService,
) {
    private val logger = LoggerFactory.getLogger(MediaCleanupJob::class.java)

    companion object {
        const val MAX_ATTEMPTS = 5
    }

    @Scheduled(cron = "\${nudge.media-cleanup.cron:0 0 3 1 * *}")
    @SchedulerLock(
        name = "drainPendingMediaDeletions",
        lockAtMostFor = "PT30M",
        lockAtLeastFor = "PT1M",
    )
    fun drain() {
        val pending = pendingRepository.findAllByStatus(PendingMediaDeletion.Status.PENDING)
        if (pending.isEmpty()) return

        var completed = 0
        var retried = 0
        var parked = 0

        for (row in pending) {
            val publicId = row.publicId ?: continue
            try {
                mediaService.destroy(publicId)
                row.status = PendingMediaDeletion.Status.COMPLETED
                row.lastAttemptAt = LocalDateTime.now()
                row.lastError = null
                pendingRepository.save(row)
                completed++
            } catch (e: MediaDeletionException) {
                row.attempts += 1
                row.lastAttemptAt = LocalDateTime.now()
                row.lastError = e.message
                if (row.attempts >= MAX_ATTEMPTS) {
                    row.status = PendingMediaDeletion.Status.FAILED
                    parked++
                } else {
                    retried++
                }
                pendingRepository.save(row)
            }
        }

        logger.info(
            "MediaCleanupJob drained {} rows (completed={}, retried={}, parked={})",
            pending.size, completed, retried, parked,
        )
    }
}
