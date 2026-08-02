package com.mudhut.nudge.tasks.spi

import java.time.LocalDateTime

/** A read-only summary of a job (service request), supplied by the servicerequests module. */
data class JobSummary(
    val requestId: Long,
    val title: String,
    val requestedDate: LocalDateTime?,
    val status: String,
)
