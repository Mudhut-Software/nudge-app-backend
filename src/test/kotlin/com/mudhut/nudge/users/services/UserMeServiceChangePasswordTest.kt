package com.mudhut.nudge.users.services

import com.mudhut.nudge.media.PendingMediaDeletionRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.models.ChangePasswordRequest
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.spi.UserBusinessMembershipQuery
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

class UserMeServiceChangePasswordTest {
    private val userRepo: UserRepository = mock()
    private val membershipQuery: UserBusinessMembershipQuery = mock()
    private val pendingMediaDeletionRepo: PendingMediaDeletionRepository = mock()
    private val passwordEncoder: PasswordEncoder = mock()
    private val sut = UserMeService(userRepo, membershipQuery, pendingMediaDeletionRepo, passwordEncoder)

    private fun user() = User(
        id = 1L, username = "u", email = "u@e.com",
        phoneNumber = null, password = "ENC(old)", role = UserRole.BASIC_USER, isActive = true,
    )

    @Test
    fun `changePassword encodes and saves when current matches`() {
        whenever(userRepo.findByEmail("u@e.com")).thenReturn(Optional.of(user()))
        whenever(passwordEncoder.matches("old-pass", "ENC(old)")).thenReturn(true)
        whenever(passwordEncoder.encode("new-pass-1")).thenReturn("ENC(new)")
        whenever(userRepo.save(any<User>())).thenAnswer { it.arguments[0] as User }

        sut.changePassword("u@e.com", ChangePasswordRequest("old-pass", "new-pass-1"))

        verify(userRepo).save(check<User> { assertThat(it.password).isEqualTo("ENC(new)") })
    }

    @Test
    fun `changePassword rejects a wrong current password`() {
        whenever(userRepo.findByEmail("u@e.com")).thenReturn(Optional.of(user()))
        whenever(passwordEncoder.matches("wrong", "ENC(old)")).thenReturn(false)

        assertThatThrownBy { sut.changePassword("u@e.com", ChangePasswordRequest("wrong", "new-pass-1")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
