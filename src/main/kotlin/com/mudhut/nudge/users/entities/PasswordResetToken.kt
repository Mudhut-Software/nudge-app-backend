package com.mudhut.nudge.users.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var token: String? = null,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    var expiryDate: LocalDateTime? = null,

    var used: Boolean = false
) {
    constructor(token: String, user: User) : this(
        token = token,
        user = user,
        expiryDate = LocalDateTime.now().plusHours(1)
    )
}
