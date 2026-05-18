package com.mudhut.nudge.servicerequests.models

import com.fasterxml.jackson.annotation.JsonInclude
import com.mudhut.nudge.servicerequests.entities.ServiceRequestStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDateTime

data class CreateRequestPayload(
    @field:NotNull
    val businessId: Long?,

    @field:Size(min = 1, message = "At least one item is required")
    @field:Valid
    val items: List<RequestItemInput> = emptyList(),
)

data class UpdateRequestPayload(
    @field:Valid
    val items: List<RequestItemInput>? = null,
    val requestedDate: LocalDateTime? = null,

    @field:Size(max = 500)
    val serviceLocation: String? = null,

    val serviceLatitude: Double? = null,
    val serviceLongitude: Double? = null,

    @field:Size(max = 2000)
    val note: String? = null,

    @field:Size(max = 8, message = "At most 8 attachments")
    @field:Valid
    val attachments: List<AttachmentInput>? = null,
)

data class RequestItemInput(
    @field:Positive
    val serviceId: Long? = null,

    @field:Positive
    val packageId: Long? = null,
)

data class AttachmentInput(
    @field:NotNull
    val url: String?,

    @field:NotNull
    @field:Pattern(regexp = "^nudge/(images|videos)/.+", message = "publicId must look like nudge/images/... or nudge/videos/...")
    val publicId: String?,

    @field:Pattern(regexp = "^(image|video)$")
    val kind: String?,
)

data class CancelRequestPayload(
    @field:Size(max = 500)
    val reason: String? = null,
)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ServiceRequestResponse(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val customerEmail: String,
    val customerPhone: String?,
    val businessId: Long,
    val businessName: String,
    val status: ServiceRequestStatus,
    val items: List<ServiceRequestItemResponse>,
    val requestedDate: LocalDateTime?,
    val serviceLocation: String?,
    val serviceLatitude: Double?,
    val serviceLongitude: Double?,
    val note: String?,
    val attachments: List<AttachmentResponse>,
    val submittedAt: LocalDateTime?,
    val respondedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val viewedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class ServiceRequestItemResponse(
    val kind: String,
    val serviceId: Long?,
    val packageId: Long?,
    val title: String,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val coverImageUrl: String?,
    val position: Int,
)

data class AttachmentResponse(
    val id: Long,
    val url: String,
    val publicId: String,
    val kind: String,
    val position: Int,
)

data class UnreadCountResponse(val count: Long)
