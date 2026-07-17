package com.mudhut.nudge.messaging.services

import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.stereotype.Service

/**
 * Online-presence lookup backed by Spring's [SimpUserRegistry], which tracks connected STOMP
 * users by Principal name (the user's email). In-memory / per-instance — correct for the
 * current single-instance backend; multi-instance presence is a later (broker-relay) slice.
 */
@Service
class PresenceService(private val userRegistry: SimpUserRegistry) {

    /** True when [email] has at least one live STOMP session, optionally ignoring one session id. */
    fun isOnline(email: String?, excludingSessionId: String? = null): Boolean {
        if (email.isNullOrBlank()) return false
        val user = userRegistry.getUser(email) ?: return false
        return user.sessions.any { it.id != excludingSessionId }
    }
}
