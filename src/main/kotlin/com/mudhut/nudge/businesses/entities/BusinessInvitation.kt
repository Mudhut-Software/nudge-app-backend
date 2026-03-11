package com.mudhut.nudge.businesses.entities

import com.mudhut.nudge.users.entities.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "business_invitations")
class BusinessInvitation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    var business: Business? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_id", nullable = false)
    var inviter: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id")
    var invitee: User? = null,

    @field:NotBlank
    var email: String? = null,

    @Enumerated(EnumType.STRING)
    var role: BusinessRole? = null,

    @Enumerated(EnumType.STRING)
    var status: InvitationStatus = InvitationStatus.PENDING,

    @Column(unique = true)
    var token: String? = null,

    var expiryDate: LocalDateTime? = null,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null
) {
    companion object {
        const val EXPIRY_DAYS = 7L
    }

    fun isExpired(): Boolean = expiryDate?.isBefore(LocalDateTime.now()) ?: true
}
