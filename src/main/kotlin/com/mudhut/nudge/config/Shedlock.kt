package com.mudhut.nudge.config

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Maps the table that ShedLock writes to. The app never reads or writes
 * this entity directly — it exists so Hibernate's ddl-auto=update creates
 * the schema, since this project has no migration tooling.
 */
@Entity
@Table(name = "shedlock")
class Shedlock(
    @Id
    @Column(nullable = false, length = 64)
    var name: String? = null,

    @Column(name = "lock_until", nullable = false)
    var lockUntil: Instant? = null,

    @Column(name = "locked_at", nullable = false)
    var lockedAt: Instant? = null,

    @Column(name = "locked_by", nullable = false, length = 255)
    var lockedBy: String? = null,
)
