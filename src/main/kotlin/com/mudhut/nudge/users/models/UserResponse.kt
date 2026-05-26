package com.mudhut.nudge.users.models

import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import java.time.LocalDateTime

data class UserResponse(
    val id: Long?,
    val email: String?,
    val username: String?,
    val phoneNumber: String?,
    val role: UserRole?,
    val isEmailVerified: Boolean,
    val isPhoneVerified: Boolean,
    val isActive: Boolean,
    val location: String? = null,
    val website: String? = null,
    val avatarUrl: String? = null,
    val avatarPublicId: String? = null,
    val createdAt: LocalDateTime? = null,
    val businesses: List<UserBusinessSummary> = emptyList()
) {
    companion object {
        fun from(user: User, memberships: List<BusinessMember> = emptyList()): UserResponse {
            return UserResponse(
                id = user.id,
                email = user.email,
                username = user.username,
                phoneNumber = user.phoneNumber,
                role = user.role,
                isEmailVerified = user.isEmailVerified,
                isPhoneVerified = user.isPhoneVerified,
                isActive = user.isActive,
                location = user.location,
                website = user.website,
                avatarUrl = user.avatarUrl,
                avatarPublicId = user.avatarPublicId,
                createdAt = user.createdAt,
                businesses = memberships.map { UserBusinessSummary.from(it) }
            )
        }
    }
}
