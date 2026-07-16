package com.mudhut.nudge.messaging.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.messaging.simp.user.SimpSession
import org.springframework.messaging.simp.user.SimpUser
import org.springframework.messaging.simp.user.SimpUserRegistry

class PresenceServiceTest {
    private val registry: SimpUserRegistry = mock()
    private val sut = PresenceService(registry)

    private fun session(id: String): SimpSession = mock<SimpSession>().also { whenever(it.id).thenReturn(id) }
    private fun userWith(vararg sessionIds: String): SimpUser {
        // Build the sessions (each does its own stubbing) BEFORE stubbing user.sessions —
        // a nested whenever() mid-stub throws UnfinishedStubbingException.
        val sessions = sessionIds.map { session(it) }.toSet()
        return mock<SimpUser>().also { whenever(it.sessions).thenReturn(sessions) }
    }

    @Test
    fun `offline when the user is not in the registry`() {
        whenever(registry.getUser("a@e.com")).thenReturn(null)
        assertThat(sut.isOnline("a@e.com")).isFalse()
    }

    @Test
    fun `online when the user has a live session`() {
        val user = userWith("s1")
        whenever(registry.getUser("a@e.com")).thenReturn(user)
        assertThat(sut.isOnline("a@e.com")).isTrue()
    }

    @Test
    fun `blank or null email is always offline`() {
        assertThat(sut.isOnline(null)).isFalse()
        assertThat(sut.isOnline("  ")).isFalse()
    }

    @Test
    fun `excluding the only session reports offline`() {
        val user = userWith("s1")
        whenever(registry.getUser("a@e.com")).thenReturn(user)
        assertThat(sut.isOnline("a@e.com", excludingSessionId = "s1")).isFalse()
    }

    @Test
    fun `excluding one session but another remains reports online`() {
        val user = userWith("s1", "s2")
        whenever(registry.getUser("a@e.com")).thenReturn(user)
        assertThat(sut.isOnline("a@e.com", excludingSessionId = "s1")).isTrue()
    }
}
