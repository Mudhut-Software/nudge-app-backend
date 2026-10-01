package com.mudhut.nudge.users.entities

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var token: String? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    /**
     * Identifies one sign-in, stable across access-token rotation.
     *
     * Nullable because rows written before concurrent sessions existed have
     * none; those fall back to delete-by-user on logout.
     */
    @Column(name = "session_id", length = 64)
    var sessionId: String? = null,

    /** Raw header, kept so a better label can be derived later without losing the source. */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    var userAgent: String? = null,

    /**
     * Derived once, at login, by [com.mudhut.nudge.users.services.DeviceLabel].
     *
     * Stored rather than parsed on read: improving the heuristic later must not
     * silently rewrite what a user was already shown.
     */
    @Column(name = "device_label", length = 100)
    var deviceLabel: String? = null,

    /** Set at login, moved forward on each refresh. */
    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null,

    @Column(nullable = false)
    var expiryDate: Instant? = null
) {
    companion object {
        fun builder() = Builder()
    }

    class Builder {
        private var id: Long? = null
        private var token: String? = null
        private var expiryDate: Instant? = null
        private var user: User? = null
        private var sessionId: String? = null
        private var userAgent: String? = null
        private var deviceLabel: String? = null
        private var lastSeenAt: Instant? = null

        fun id(id: Long?) = apply { this.id = id }
        fun token(token: String?) = apply { this.token = token }
        fun expiryDate(expiryDate: Instant?) = apply { this.expiryDate = expiryDate }
        fun user(user: User?) = apply { this.user = user }
        fun sessionId(sessionId: String?) = apply { this.sessionId = sessionId }
        fun userAgent(userAgent: String?) = apply { this.userAgent = userAgent }
        fun deviceLabel(deviceLabel: String?) = apply { this.deviceLabel = deviceLabel }
        fun lastSeenAt(lastSeenAt: Instant?) = apply { this.lastSeenAt = lastSeenAt }

        // Argument order must match the constructor: sessionId, then the three
        // new fields, then expiryDate.
        fun build() = RefreshToken(id, token, user, sessionId, userAgent, deviceLabel, lastSeenAt, expiryDate)
    }
}
