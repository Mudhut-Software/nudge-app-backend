package com.mudhut.nudge.config

import com.mudhut.nudge.users.services.AccessTokenBlocklistService
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BlocklistCleanupJob(
    private val blocklistService: AccessTokenBlocklistService,
) {
    private val logger = LoggerFactory.getLogger(BlocklistCleanupJob::class.java)

    @Scheduled(fixedRate = 3_600_000) // 1 hour
    @SchedulerLock(name = "purgeExpiredBlocklist", lockAtMostFor = "PT55M", lockAtLeastFor = "PT55M")
    fun purge() {
        val deleted = blocklistService.purgeExpired()
        if (deleted > 0) {
            logger.info("Purged {} expired access-token blocklist rows", deleted)
        }
    }
}
