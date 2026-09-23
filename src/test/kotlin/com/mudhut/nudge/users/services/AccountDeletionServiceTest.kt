package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.spi.ClosureSummary
import com.mudhut.nudge.users.spi.OwnedBusinessClosure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class AccountDeletionServiceTest {

    private val userRepository: UserRepository = mock()
    private val closure: OwnedBusinessClosure = mock()
    private val logoutService: LogoutService = mock()
    private val sut = AccountDeletionService(userRepository, closure, logoutService)

    private fun alice() = User(
        id = 1L,
        username = "Alice",
        email = "alice@example.com",
        phoneNumber = "+256700000000",
        password = "hashed",
        role = UserRole.BASIC_USER,
        isActive = true,
        location = "Kampala",
        website = "https://alice.dev",
        avatarUrl = "https://cdn/a.jpg",
    )

    @Test
    fun `strips every piece of personal data and deactivates the account`() {
        val user = alice()
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(closure.closeAllOwnedBy(1L)).thenReturn(ClosureSummary(emptyList(), 0))

        sut.deleteAccount("alice@example.com", "Bearer token123")

        assertFalse(user.isActive)
        assertEquals("Deleted user", user.username)
        assertNull(user.phoneNumber)
        assertNull(user.location)
        assertNull(user.website)
        assertNull(user.avatarUrl)
    }

    @Test
    fun `replaces the email with an unreachable tombstone, releasing the real one`() {
        val user = alice()
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(closure.closeAllOwnedBy(1L)).thenReturn(ClosureSummary(emptyList(), 0))

        sut.deleteAccount("alice@example.com", "Bearer token123")

        // .invalid is reserved by RFC 2606 and can never receive mail. Replacing
        // rather than blanking also frees the real address to register again,
        // because the column is unique.
        assertEquals("deleted+1@nudge.invalid", user.email)
    }

    @Test
    fun `closes the businesses and ends the session before the email is overwritten`() {
        val user = alice()
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user))
        whenever(userRepository.save(any<User>())).thenAnswer { it.arguments[0] as User }
        whenever(closure.closeAllOwnedBy(1L)).thenReturn(ClosureSummary(listOf("SparkleClean"), 2))

        sut.deleteAccount("alice@example.com", "Bearer token123")

        // LogoutService resolves the user by email, so it must run while the real
        // address is still on the row. Ordering is the whole test.
        inOrder(closure, logoutService, userRepository).apply {
            verify(closure).closeAllOwnedBy(1L)
            verify(logoutService).logout(eq("alice@example.com"), eq("Bearer token123"))
            verify(userRepository).save(any<User>())
        }
    }

    @Test
    fun `reports what deletion would destroy without changing anything`() {
        val user = alice()
        whenever(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user))
        whenever(closure.previewFor(1L)).thenReturn(ClosureSummary(listOf("SparkleClean"), 3))

        val impact = sut.impactFor("alice@example.com")

        assertEquals(listOf("SparkleClean"), impact.businessNames)
        assertEquals(3, impact.liveRequestCount)
        verify(userRepository, never()).save(any<User>())
    }
}
