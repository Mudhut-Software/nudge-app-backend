package com.mudhut.nudge.users.services

import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.servicesoffered.entities.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.repositories.PendingMediaDeletionRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.models.UpdateUserRequest
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.UserAlreadyExistsException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class UserMeServiceTest {

    private val userRepository: UserRepository = mock()
    private val businessMemberRepository: BusinessMemberRepository = mock()
    private val pendingMediaDeletionRepository: PendingMediaDeletionRepository = mock()

    private val sut = UserMeService(userRepository, businessMemberRepository, pendingMediaDeletionRepository)

    private fun existingUser(
        id: Long = 1L,
        email: String = "alice@example.com",
        username: String = "alice",
        location: String? = null,
        website: String? = null,
        avatarUrl: String? = null,
        avatarPublicId: String? = null,
    ) = User(
        id = id,
        email = email,
        username = username,
        location = location,
        website = website,
        avatarUrl = avatarUrl,
        avatarPublicId = avatarPublicId,
    ).also {
        whenever(userRepository.findByEmail(email)).thenReturn(Optional.of(it))
        whenever(userRepository.save(any<User>())).thenAnswer { invocation -> invocation.arguments[0] }
        whenever(businessMemberRepository.findByUserIdAndIsActiveTrue(id)).thenReturn(emptyList())
    }

    @Test
    fun `updateMe persists only the fields present in the request`() {
        val user = existingUser(location = "Kampala")

        val response = sut.updateMe("alice@example.com", UpdateUserRequest(website = "https://alice.dev"))

        // username/location untouched, website updated.
        assertEquals("alice", response.username)
        assertEquals("Kampala", response.location)
        assertEquals("https://alice.dev", response.website)
    }

    @Test
    fun `updateMe clears a field when given an empty string`() {
        existingUser(location = "Kampala", website = "https://alice.dev")

        val response = sut.updateMe("alice@example.com", UpdateUserRequest(website = ""))

        assertEquals("Kampala", response.location)
        assertNull(response.website)
    }

    @Test
    fun `updateMe rejects a duplicate username with UserAlreadyExistsException`() {
        existingUser(username = "alice")
        whenever(userRepository.existsByUsername("bob")).thenReturn(true)

        assertThrows(UserAlreadyExistsException::class.java) {
            sut.updateMe("alice@example.com", UpdateUserRequest(username = "bob"))
        }
    }

    @Test
    fun `updateMe accepts the same username without checking uniqueness`() {
        existingUser(username = "alice")

        val response = sut.updateMe("alice@example.com", UpdateUserRequest(username = "alice"))

        assertEquals("alice", response.username)
        verify(userRepository, never()).existsByUsername(any())
    }

    @Test
    fun `updateMe queues the previous avatar publicId for cleanup on replacement`() {
        existingUser(
            avatarUrl = "https://cdn/old.jpg",
            avatarPublicId = "nudge/avatars/old-pid",
        )

        sut.updateMe(
            "alice@example.com",
            UpdateUserRequest(
                avatarUrl = "https://cdn/new.jpg",
                avatarPublicId = "nudge/avatars/new-pid",
            ),
        )

        val captor = argumentCaptor<PendingMediaDeletion>()
        verify(pendingMediaDeletionRepository).save(captor.capture())
        assertEquals("nudge/avatars/old-pid", captor.firstValue.publicId)
        assertEquals(PendingMediaDeletion.Status.PENDING, captor.firstValue.status)
    }

    @Test
    fun `updateMe does not enqueue cleanup when the avatar is unchanged`() {
        existingUser(
            avatarUrl = "https://cdn/same.jpg",
            avatarPublicId = "nudge/avatars/same-pid",
        )

        sut.updateMe(
            "alice@example.com",
            UpdateUserRequest(
                avatarUrl = "https://cdn/same.jpg",
                avatarPublicId = "nudge/avatars/same-pid",
            ),
        )

        verify(pendingMediaDeletionRepository, never()).save(any<PendingMediaDeletion>())
    }

    @Test
    fun `updateMe clears the avatar when given an empty url and queues the old publicId for cleanup`() {
        existingUser(
            avatarUrl = "https://cdn/old.jpg",
            avatarPublicId = "nudge/avatars/old-pid",
        )

        val response = sut.updateMe(
            "alice@example.com",
            UpdateUserRequest(avatarUrl = "", avatarPublicId = ""),
        )

        assertNull(response.avatarUrl)
        assertNull(response.avatarPublicId)
        verify(pendingMediaDeletionRepository).save(any<PendingMediaDeletion>())
    }

    @Test
    fun `updateMe throws UserNotFoundException for an unknown caller`() {
        whenever(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty())

        assertThrows(UserNotFoundException::class.java) {
            sut.updateMe("ghost@example.com", UpdateUserRequest(username = "ghost"))
        }
    }

    @Test
    fun `updateMe ignores null fields entirely`() {
        existingUser(location = "Kampala", website = "https://alice.dev", avatarUrl = "https://cdn/a.jpg")

        val response = sut.updateMe("alice@example.com", UpdateUserRequest())

        // Everything stays put.
        assertEquals("alice", response.username)
        assertEquals("Kampala", response.location)
        assertEquals("https://alice.dev", response.website)
        assertEquals("https://cdn/a.jpg", response.avatarUrl)
        verify(pendingMediaDeletionRepository, never()).save(any<PendingMediaDeletion>())
    }

    @Test
    fun `updateMe returns the membership list with the response`() {
        val user = existingUser(id = 42L)
        // Empty memberships list from beforeEach — assert response carries the empty list, not null.
        whenever(businessMemberRepository.findByUserIdAndIsActiveTrue(eq(42L))).thenReturn(emptyList())

        val response = sut.updateMe("alice@example.com", UpdateUserRequest(location = "Mengo"))

        assertEquals(emptyList<Any>(), response.businesses)
        assertEquals("Mengo", response.location)
    }
}
