package com.mudhut.nudge.users.models

import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole

data class UserResponse(
    val id: Long?,
    val email: String?,
    val phoneNumber: String?,
    val role: UserRole?,
    val isEmailVerified: Boolean,
    val isPhoneVerified: Boolean,
    val isActive: Boolean
) {
    companion object {
        fun from(user: User): UserResponse {
            return UserResponse(
                id = user.id,
                email = user.email,
                phoneNumber = user.phoneNumber,
                role = user.role,
                isEmailVerified = user.isEmailVerified,
                isPhoneVerified = user.isPhoneVerified,
                isActive = user.isActive
            )
        }
    }
}
