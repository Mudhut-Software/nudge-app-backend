package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.models.DeletionImpactResponse
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.spi.OwnedBusinessClosure
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

/**
 * Deletes an account by anonymising it in place.
 *
 * A real DELETE is impossible: five relations pointing at User are
 * `nullable = false` (Business.owner, Review.customer, Invoice.customer,
 * Message.sender, ServiceRequest.customer), so removing the row means cascading
 * other people's records — a provider's job history, a customer's invoice, half
 * of someone else's conversation. The row stays as a tombstone instead.
 */
@Service
class AccountDeletionService(
    private val userRepository: UserRepository,
    private val closure: OwnedBusinessClosure,
    private val logoutService: LogoutService,
) {

    @Transactional
    fun deleteAccount(email: String, authorizationHeader: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found") }
        val id = requireNotNull(user.id) { "Persisted user has no id" }

        // The order below is load-bearing; see each comment.

        // 1. Cancel live bookings and close the businesses. First, so the
        //    "provider closed" email is true by the time a customer reads it.
        closure.closeAllOwnedBy(id)

        // 2. End the session. Before the email is overwritten — LogoutService
        //    resolves the user by email and would not find them afterwards.
        logoutService.logout(email, authorizationHeader)

        // 3. Anonymise. Last, so every step above could still resolve the user.
        user.isActive = false
        user.username = "Deleted user"
        user.phoneNumber = null
        user.location = null
        user.website = null
        user.avatarUrl = null
        user.avatarPublicId = null
        // Replaced, not blanked: the column is unique and it is the login
        // identity. `.invalid` is reserved by RFC 2606 and can never receive
        // mail, and overwriting frees the real address to register again.
        user.email = "deleted+$id@nudge.invalid"

        userRepository.save(user)
    }

    fun impactFor(email: String): DeletionImpactResponse {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User not found") }
        val summary = closure.previewFor(requireNotNull(user.id))
        return DeletionImpactResponse(
            businessNames = summary.businessNames,
            liveRequestCount = summary.cancelledRequestCount,
        )
    }
}
