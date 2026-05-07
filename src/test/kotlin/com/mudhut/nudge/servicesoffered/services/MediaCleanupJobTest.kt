package com.mudhut.nudge.servicesoffered.services

import com.mudhut.nudge.servicesoffered.entities.PendingMediaDeletion
import com.mudhut.nudge.servicesoffered.repositories.PendingMediaDeletionRepository
import com.mudhut.nudge.utils.exceptions.MediaDeletionException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class MediaCleanupJobTest {

    @Mock
    private lateinit var pendingRepository: PendingMediaDeletionRepository

    @Mock
    private lateinit var mediaService: MediaService

    @InjectMocks
    private lateinit var job: MediaCleanupJob

    private fun pending(id: Long, publicId: String, attempts: Int = 0) =
        PendingMediaDeletion(id = id, publicId = publicId, attempts = attempts)

    @Test
    fun `drain marks rows COMPLETED on successful destroy`() {
        val rows = listOf(
            pending(1L, "nudge/images/a"),
            pending(2L, "nudge/images/b"),
        )
        whenever(pendingRepository.findAllByStatus(PendingMediaDeletion.Status.PENDING)).thenReturn(rows)

        job.drain()

        verify(mediaService).destroy("nudge/images/a")
        verify(mediaService).destroy("nudge/images/b")

        val captor = argumentCaptor<PendingMediaDeletion>()
        verify(pendingRepository, org.mockito.Mockito.times(2)).save(captor.capture())
        assertTrue(captor.allValues.all { it.status == PendingMediaDeletion.Status.COMPLETED })
    }

    @Test
    fun `drain bumps attempts and stays PENDING on a transient failure`() {
        val row = pending(1L, "nudge/images/a", attempts = 0)
        whenever(pendingRepository.findAllByStatus(PendingMediaDeletion.Status.PENDING)).thenReturn(listOf(row))
        whenever(mediaService.destroy(eq("nudge/images/a")))
            .thenThrow(MediaDeletionException("transient: connection refused"))

        job.drain()

        val captor = argumentCaptor<PendingMediaDeletion>()
        verify(pendingRepository).save(captor.capture())
        assertEquals(PendingMediaDeletion.Status.PENDING, captor.firstValue.status)
        assertEquals(1, captor.firstValue.attempts)
        assertNotNull(captor.firstValue.lastAttemptAt)
        assertEquals("transient: connection refused", captor.firstValue.lastError)
    }

    @Test
    fun `drain parks the row as FAILED after 5 attempts`() {
        val row = pending(1L, "nudge/images/a", attempts = 4)
        whenever(pendingRepository.findAllByStatus(PendingMediaDeletion.Status.PENDING)).thenReturn(listOf(row))
        whenever(mediaService.destroy(eq("nudge/images/a")))
            .thenThrow(MediaDeletionException("still failing"))

        job.drain()

        val captor = argumentCaptor<PendingMediaDeletion>()
        verify(pendingRepository).save(captor.capture())
        assertEquals(PendingMediaDeletion.Status.FAILED, captor.firstValue.status)
        assertEquals(5, captor.firstValue.attempts)
    }

    @Test
    fun `drain does nothing when there are no PENDING rows`() {
        whenever(pendingRepository.findAllByStatus(PendingMediaDeletion.Status.PENDING)).thenReturn(emptyList())

        job.drain()

        verify(mediaService, never()).destroy(any())
        verify(pendingRepository, never()).save(any())
    }

    @Test
    fun `drain parks the row immediately on IllegalArgumentException without consuming retries`() {
        val row = pending(1L, "nudge/badprefix/x", attempts = 0)
        whenever(pendingRepository.findAllByStatus(PendingMediaDeletion.Status.PENDING)).thenReturn(listOf(row))
        whenever(mediaService.destroy(eq("nudge/badprefix/x")))
            .thenThrow(IllegalArgumentException("Refusing to delete publicId 'nudge/badprefix/x'"))

        job.drain()

        val captor = argumentCaptor<PendingMediaDeletion>()
        verify(pendingRepository).save(captor.capture())
        assertEquals(PendingMediaDeletion.Status.FAILED, captor.firstValue.status)
        assertEquals(1, captor.firstValue.attempts)
        assertNotNull(captor.firstValue.lastAttemptAt)
        assertTrue(captor.firstValue.lastError!!.contains("Refusing to delete"))
    }

    @Test
    fun `drain continues processing other rows when one row throws`() {
        val poison = pending(1L, "nudge/badprefix/x")
        val good = pending(2L, "nudge/images/good")
        whenever(pendingRepository.findAllByStatus(PendingMediaDeletion.Status.PENDING))
            .thenReturn(listOf(poison, good))
        whenever(mediaService.destroy(eq("nudge/badprefix/x")))
            .thenThrow(IllegalArgumentException("bad prefix"))
        // mediaService.destroy("nudge/images/good") returns normally (default Mockito void mock).

        job.drain()

        // Both rows should have been saved — the poison-pill must not abort the loop.
        verify(pendingRepository, org.mockito.Mockito.times(2)).save(any<PendingMediaDeletion>())
        // Specifically the good row should be marked COMPLETED.
        val captor = argumentCaptor<PendingMediaDeletion>()
        verify(pendingRepository, org.mockito.Mockito.times(2)).save(captor.capture())
        val saved = captor.allValues
        assertEquals(PendingMediaDeletion.Status.FAILED, saved.find { it.publicId == "nudge/badprefix/x" }!!.status)
        assertEquals(PendingMediaDeletion.Status.COMPLETED, saved.find { it.publicId == "nudge/images/good" }!!.status)
    }
}
