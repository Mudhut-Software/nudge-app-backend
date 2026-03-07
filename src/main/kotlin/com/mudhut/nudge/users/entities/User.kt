package com.mudhut.nudge.users.entities

import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @field:NotBlank
    @field:Email
    @Column(unique = true)
    var email: String? = null,

    @field:NotBlank
    @field:Size(min = 8, max = 120)
    var password: String? = null,

    @Column(unique = true)
    var phoneNumber: String? = null,

    @Enumerated(EnumType.STRING)
    var role: UserRole? = null,

    var isEmailVerified: Boolean = false,

    var isPhoneVerified: Boolean = false,

    var isActive: Boolean = true,

    @CreationTimestamp
    var createdAt: LocalDateTime? = null,

    @UpdateTimestamp
    var updatedAt: LocalDateTime? = null,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "permission")
    var permissions: MutableSet<String> = mutableSetOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as User
        return id == other.id && email == other.email
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (email?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "User(id=$id, email='$email', phoneNumber='$phoneNumber', role=$role, " +
                "isEmailVerified=$isEmailVerified, isPhoneVerified=$isPhoneVerified, isActive=$isActive)"
    }
}
