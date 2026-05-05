package com.mudhut.nudge.servicesoffered.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "pending_media_deletions")
class PendingMediaDeletion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "public_id", nullable = false)
    var publicId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: Status = Status.PENDING,

    @Column(nullable = false)
    var attempts: Int = 0,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,

    @Column(name = "last_attempt_at")
    var lastAttemptAt: LocalDateTime? = null,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,
) {
    enum class Status { PENDING, COMPLETED, FAILED }
}
