package com.mudhut.nudge.businesses.entities

import com.mudhut.nudge.users.entities.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "business_members",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "business_id"])]
)
class BusinessMember(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @Enumerated(EnumType.STRING)
    var role: BusinessRole? = null,

    var isActive: Boolean = true,

    @CreationTimestamp
    var joinedAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
)
