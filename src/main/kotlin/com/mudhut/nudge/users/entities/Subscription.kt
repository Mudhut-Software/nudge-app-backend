package com.mudhut.nudge.users.entities

import jakarta.persistence.*
import java.time.Instant

/**
 * One tier-period in an account's history. Append-only: a user may cancel and
 * resubscribe repeatedly, and each of those is a fact worth keeping — when they
 * first paid, how long they stayed, and what they were entitled to on the date a
 * dispute refers to.
 *
 * Exactly one row per user has a null [endedAt]; that is the current one.
 */
@Entity
@Table(
    name = "subscription",
    indexes = [Index(name = "idx_sub_user_ended", columnList = "user_id, ended_at")],
)
class Subscription(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var tier: SubscriptionTier = SubscriptionTier.FREE,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant? = null,

    /** When this paid period lapses. Null for FREE, which is open-ended. */
    @Column(name = "current_period_end")
    var currentPeriodEnd: Instant? = null,

    /**
     * When the user asked to stop renewing. Setting it does **not** end the
     * subscription — they keep the tier until [currentPeriodEnd].
     */
    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    /** When it actually ended. Null marks the current row. */
    @Column(name = "ended_at")
    var endedAt: Instant? = null,
)
