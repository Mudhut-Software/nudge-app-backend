package com.mudhut.nudge.users.repositories

import com.mudhut.nudge.users.entities.Subscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface SubscriptionRepository : JpaRepository<Subscription, Long> {

    /** The current subscription: the one that has not ended. */
    fun findByUserIdAndEndedAtIsNull(userId: Long): Optional<Subscription>

    /**
     * The account's first-ever subscription, which anchors the quota month.
     * Deliberately not the current one — see SubscriptionService.quotaAnchorFor.
     */
    fun findFirstByUserIdOrderByStartedAtAsc(userId: Long): Optional<Subscription>
}
