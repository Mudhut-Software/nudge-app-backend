package com.mudhut.nudge.servicesoffered.services

import com.cloudinary.Cloudinary
import com.mudhut.nudge.servicesoffered.models.MediaInputConstants
import com.mudhut.nudge.utils.exceptions.MediaDeletionException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class MediaService(
    private val cloudinary: Cloudinary,
) {
    private val logger = LoggerFactory.getLogger(MediaService::class.java)

    private val publicIdPattern = Regex(MediaInputConstants.PUBLIC_ID_PATTERN)

    /**
     * Deletes the asset identified by [publicId] from Cloudinary.
     *
     * - Validates the publicId is under the allowed media-type roots before talking to Cloudinary.
     * - Treats Cloudinary's "ok" and "not found" results as success (the asset is gone either way).
     * - Wraps any other outcome in [MediaDeletionException] so callers can surface or retry.
     */
    fun destroy(publicId: String) {
        require(publicIdPattern.matches(publicId)) {
            "Refusing to delete publicId '$publicId': outside the allowed media-type roots"
        }

        val resourceType = if (publicId.startsWith("nudge/videos/")) "video" else "image"
        val response: Map<*, *> = try {
            cloudinary.uploader().destroy(publicId, mapOf<String, Any>("resource_type" to resourceType))
        } catch (e: Exception) {
            throw MediaDeletionException("Cloudinary destroy threw for '$publicId': ${e.message}")
        }

        val result = response["result"] as? String
        when (result) {
            "ok", "not found" -> logger.debug("Cloudinary destroy '{}' -> {}", publicId, result)
            else -> throw MediaDeletionException(
                "Cloudinary destroy '$publicId' returned unexpected result: $result"
            )
        }
    }
}
