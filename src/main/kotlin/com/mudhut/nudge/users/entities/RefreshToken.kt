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

        fun id(id: Long?) = apply { this.id = id }
        fun token(token: String?) = apply { this.token = token }
        fun expiryDate(expiryDate: Instant?) = apply { this.expiryDate = expiryDate }
        fun user(user: User?) = apply { this.user = user }

        fun build() = RefreshToken(id, token, user, expiryDate)
    }
}
