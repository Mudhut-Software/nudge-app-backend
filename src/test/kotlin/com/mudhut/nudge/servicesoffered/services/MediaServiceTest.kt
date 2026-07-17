package com.mudhut.nudge.servicesoffered.services

import com.cloudinary.Cloudinary
import com.cloudinary.Uploader
import com.mudhut.nudge.utils.exceptions.MediaDeletionException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class MediaServiceTest {

    @Mock
    private lateinit var cloudinary: Cloudinary

    @Mock
    private lateinit var uploader: Uploader

    @InjectMocks
    private lateinit var mediaService: MediaService

    @Test
    fun `destroy returns silently when Cloudinary reports ok`() {
        whenever(cloudinary.uploader()).thenReturn(uploader)
        whenever(uploader.destroy(eq("nudge/images/abc"), any<Map<String, Any>>()))
            .thenReturn(mapOf("result" to "ok"))

        mediaService.destroy("nudge/images/abc")

        verify(uploader).destroy(eq("nudge/images/abc"), any<Map<String, Any>>())
    }

    @Test
    fun `destroy returns silently when Cloudinary reports not found`() {
        whenever(cloudinary.uploader()).thenReturn(uploader)
        whenever(uploader.destroy(eq("nudge/images/missing"), any<Map<String, Any>>()))
            .thenReturn(mapOf("result" to "not found"))

        mediaService.destroy("nudge/images/missing")
        // No exception expected.
    }

    @Test
    fun `destroy throws when Cloudinary returns an unexpected result`() {
        whenever(cloudinary.uploader()).thenReturn(uploader)
        whenever(uploader.destroy(eq("nudge/images/abc"), any<Map<String, Any>>()))
            .thenReturn(mapOf("result" to "error"))

        assertThrows<MediaDeletionException> {
            mediaService.destroy("nudge/images/abc")
        }
    }

    @Test
    fun `destroy throws when the SDK throws`() {
        whenever(cloudinary.uploader()).thenReturn(uploader)
        whenever(uploader.destroy(eq("nudge/images/abc"), any<Map<String, Any>>()))
            .thenThrow(RuntimeException("connection refused"))

        val ex = assertThrows<MediaDeletionException> {
            mediaService.destroy("nudge/images/abc")
        }
        assertTrue(ex.message!!.contains("connection refused"))
    }

    @Test
    fun `destroy rejects publicIds outside the allowed roots without calling Cloudinary`() {
        assertThrows<IllegalArgumentException> {
            mediaService.destroy("foreign/folder/asset")
        }
        // Cloudinary should not have been touched.
        verify(uploader, org.mockito.Mockito.never()).destroy(any<String>(), any<Map<String, Any>>())
    }

    @Test
    fun `destroy passes resource_type=video for a nudge slash videos publicId`() {
        whenever(cloudinary.uploader()).thenReturn(uploader)
        whenever(uploader.destroy(eq("nudge/videos/clip"), any<Map<String, Any>>()))
            .thenReturn(mapOf("result" to "ok"))

        mediaService.destroy("nudge/videos/clip")

        val captor = argumentCaptor<Map<String, Any>>()
        verify(uploader).destroy(eq("nudge/videos/clip"), captor.capture())
        assertEquals("video", captor.firstValue["resource_type"])
    }

    @Test
    fun `destroy passes resource_type=image for a nudge slash images publicId`() {
        whenever(cloudinary.uploader()).thenReturn(uploader)
        whenever(uploader.destroy(eq("nudge/images/photo"), any<Map<String, Any>>()))
            .thenReturn(mapOf("result" to "ok"))

        mediaService.destroy("nudge/images/photo")

        val captor = argumentCaptor<Map<String, Any>>()
        verify(uploader).destroy(eq("nudge/images/photo"), captor.capture())
        assertEquals("image", captor.firstValue["resource_type"])
    }
}
