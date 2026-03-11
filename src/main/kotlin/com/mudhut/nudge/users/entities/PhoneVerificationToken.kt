package com.mudhut.nudge.users.entities

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "phone_verification_tokens")
class PhoneVerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var code: String? = null,

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    var expiryDate: LocalDateTime? = null,

    var used: Boolean = false
) {
    constructor(code: String, user: User) : this(
        code = code,
        user = user,
        expiryDate = LocalDateTime.now().plusMinutes(15)
    )
}
