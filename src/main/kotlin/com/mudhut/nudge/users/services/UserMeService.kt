package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.spi.UserBusinessMembershipQuery
import com.mudhut.nudge.media.PendingMediaDeletion
import com.mudhut.nudge.media.PendingMediaDeletionRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.models.ChangePasswordRequest
import com.mudhut.nudge.users.models.UpdateUserRequest
import com.mudhut.nudge.users.models.UserResponse
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.UserAlreadyExistsException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserMeService(
    private val userRepository: UserRepository,
    private val membershipQuery: UserBusinessMembershipQuery,
    private val pendingMediaDeletionRepository: PendingMediaDeletionRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional
    fun changePassword(email: String, request: ChangePasswordRequest) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found") }
        require(passwordEncoder.matches(request.currentPassword, user.password)) {
            "Current password is incorrect"
        }
        user.password = passwordEncoder.encode(request.newPassword)
        userRepository.save(user)
    }

    @Transactional
    fun updateMe(email: String, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found") }

        request.username?.let { applyUsername(user, it) }
        request.location?.let { user.location = it.ifBlank { null } }
        request.website?.let { user.website = it.ifBlank { null } }
        applyAvatar(user, request)

        userRepository.save(user)

        val memberships = user.id?.let { membershipQuery.findActiveMembershipsFor(it) }.orEmpty()
        return UserResponse.from(user, memberships)
    }

    private fun applyUsername(user: User, next: String) {
        if (next == user.username) return
        if (userRepository.existsByUsername(next)) {
            throw UserAlreadyExistsException("Username already taken: $next")
        }
        user.username = next
    }

    private fun applyAvatar(user: User, request: UpdateUserRequest) {
        // Only act when avatarUrl is explicitly present (null = leave as-is).
        val newUrl = request.avatarUrl ?: return
        val nextAvatarUrl = newUrl.ifBlank { null }
        if (nextAvatarUrl == user.avatarUrl) return

        // Replacing an existing avatar — queue the old Cloudinary publicId for cleanup
        // by the MediaCleanupJob, same pattern as ServiceOffered cover replacement.
        user.avatarPublicId
            ?.takeIf { it.isNotBlank() }
            ?.let { oldPid ->
                pendingMediaDeletionRepository.save(
                    PendingMediaDeletion(
                        publicId = oldPid,
                        status = PendingMediaDeletion.Status.PENDING,
                    )
                )
            }

        user.avatarUrl = nextAvatarUrl
        user.avatarPublicId = request.avatarPublicId?.ifBlank { null }
    }
}
