package com.mudhut.nudge.users.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "revoked_access_tokens",
    indexes = [Index(name = "idx_revoked_access_tokens_expires_at", columnList = "expires_at")]
)
class RevokedAccessToken(
    @Id
    @Column(nullable = false, length = 64)
    var jti: String? = null,

    @Column(name = "user_id", nullable = false)
    var userId: Long? = null,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant? = null,

    @Column(name = "revoked_at", nullable = false)
    var revokedAt: Instant? = null,
)
