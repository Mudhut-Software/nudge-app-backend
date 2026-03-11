package com.mudhut.nudge.users.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "verification_tokens")
class VerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var token: String? = null,

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    var expiryDate: LocalDateTime? = null,

    var used: Boolean = false
) {
    constructor(token: String, user: User) : this(
        token = token,
        user = user,
        expiryDate = LocalDateTime.now().plusHours(24)
    )
}
