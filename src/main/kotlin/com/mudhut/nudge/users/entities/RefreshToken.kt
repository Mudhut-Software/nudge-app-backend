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

        fun id(id: Long?) = apply { this.id = id }
        fun token(token: String?) = apply { this.token = token }
        fun expiryDate(expiryDate: Instant?) = apply { this.expiryDate = expiryDate }
        fun user(user: User?) = apply { this.user = user }
        fun sessionId(sessionId: String?) = apply { this.sessionId = sessionId }

        // Argument order must match the constructor: sessionId sits between user
        // and expiryDate.
        fun build() = RefreshToken(id, token, user, sessionId, expiryDate)
    }
}
