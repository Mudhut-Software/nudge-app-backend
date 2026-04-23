package com.mudhut.nudge.users.models

import com.mudhut.nudge.businesses.entities.BusinessMember
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole

data class UserResponse(
    val id: Long?,
    val email: String?,
    val username: String?,
    val phoneNumber: String?,
    val role: UserRole?,
    val isEmailVerified: Boolean,
    val isPhoneVerified: Boolean,
    val isActive: Boolean,
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
                businesses = memberships.map { UserBusinessSummary.from(it) }
            )
        }
    }
}
