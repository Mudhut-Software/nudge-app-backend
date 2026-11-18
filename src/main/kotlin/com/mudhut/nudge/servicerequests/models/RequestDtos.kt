package com.mudhut.nudge.servicerequests.models

import com.fasterxml.jackson.annotation.JsonInclude
import com.mudhut.nudge.servicerequests.entities.PricingState
import com.mudhut.nudge.servicerequests.entities.ProposalOutcome
import com.mudhut.nudge.servicerequests.entities.ServiceRequest
import com.mudhut.nudge.servicerequests.entities.ServiceRequestPriceProposal
import com.mudhut.nudge.servicerequests.entities.ServiceRequestProposal
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

    @field:Size(max = 2000)
    val accessDirections: String? = null,

    @field:Size(max = 8, message = "At most 8 attachments")
    @field:Valid
    val attachments: List<AttachmentInput>? = null,
)

data class RequestItemInput(
    @field:NotNull
    @field:Positive
    val serviceId: Long? = null,

    /** Units of the item; null means 1. Only items with quantity on accept anything else. */
    @field:Positive
    val quantity: Int? = null,

    @field:Valid
    val picks: List<PickInput> = emptyList(),
)

data class PickInput(
    @field:Positive
    val choiceId: Long,

    @field:Positive
    val quantity: Int = 1,
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

data class ProposeTimePayload(
    @field:NotNull
    val proposedDate: LocalDateTime? = null,

    @field:Size(max = 500)
    val note: String? = null,
)

data class ProposePricePayload(
    @field:NotNull
    @field:Positive
    val amount: BigDecimal? = null,

    @field:NotNull
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO-4217 code")
    val currency: String? = null,

    @field:Size(max = 500)
    val note: String? = null,
)

/** Names the proposal the customer saw, so a replaced price can never be accepted by mistake. */
data class RespondToPricePayload(
    @field:NotNull
    val proposalId: Long? = null,
)

data class RejectPricePayload(
    @field:NotNull
    val proposalId: Long? = null,

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
    val accessDirections: String?,
    val declineReason: String?,
    val cancellationReason: String?,
    /** The most recent proposal, or null when none was ever made. */
    val proposal: ProposalResponse?,
    val attachments: List<AttachmentResponse>,
    val pricing: PricingState,
    /** Sum of item and pick prices; null while any item is unpriced, and null once [agreedPrice] is set. */
    val total: BigDecimal?,
    /** The price the customer accepted; replaces [total] for display and invoicing. */
    val agreedPrice: MoneyDto?,
    /** The most recent price proposal whatever its outcome, or null when none was ever made. */
    val priceProposal: PriceProposalResponse?,
    /** False while a price proposal awaits an answer, or a to-be-confirmed booking has no agreed price. */
    val priceSettled: Boolean,
    val submittedAt: LocalDateTime?,
    val respondedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val cancelledAt: LocalDateTime?,
    val viewedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class ProposalResponse(
    val proposedDate: LocalDateTime,
    val note: String?,
    val outcome: ProposalOutcome,
    val proposedAt: LocalDateTime,
    val responseNote: String?,
) {
    companion object {
        /**
         * Lives here rather than in each service: `ServiceRequestService` and
         * `ProviderRequestService` keep separate `toResponse` implementations, and
         * this is the one part of the payload that must read identically on both
         * sides of the wire.
         */
        fun from(proposal: ServiceRequestProposal) = ProposalResponse(
            proposedDate = requireNotNull(proposal.proposedDate),
            note = proposal.note,
            outcome = proposal.outcome,
            proposedAt = requireNotNull(proposal.proposedAt),
            responseNote = proposal.responseNote,
        )
    }
}

data class MoneyDto(
    val amount: BigDecimal,
    val currency: String,
    val note: String?,
) {
    companion object {
        fun agreedOf(request: ServiceRequest): MoneyDto? {
            val amount = request.agreedPriceAmount ?: return null
            return MoneyDto(amount, requireNotNull(request.agreedPriceCurrency), request.agreedPriceNote)
        }
    }
}

data class PriceProposalResponse(
    val id: Long,
    val amount: BigDecimal,
    val currency: String,
    val note: String?,
    val outcome: ProposalOutcome,
    val proposedAt: LocalDateTime,
    val responseNote: String?,
    val respondedAt: LocalDateTime?,
) {
    companion object {
        fun from(p: ServiceRequestPriceProposal) = PriceProposalResponse(
            id = requireNotNull(p.id),
            amount = requireNotNull(p.amount),
            currency = requireNotNull(p.currency),
            note = p.note,
            outcome = p.outcome,
            proposedAt = requireNotNull(p.proposedAt),
            responseNote = p.responseNote,
            respondedAt = p.respondedAt,
        )
    }
}

data class ServiceRequestItemResponse(
    val serviceId: Long?,
    val title: String,
    val priceAmount: BigDecimal?,
    val priceCurrency: String?,
    val priceIsEstimate: Boolean,
    val estimateReason: String?,
    val quantity: Int,
    val quantityUnit: String?,
    val coverImageUrl: String?,
    val position: Int,
    val picks: List<ItemPickResponse>,
)

data class ItemPickResponse(
    val id: Long,
    /** Null once the choice has been deleted from the catalogue. */
    val choiceId: Long?,
    val groupId: Long?,
    val groupName: String,
    val title: String,
    /** Effective price in the group; null = included. */
    val price: BigDecimal?,
    val quantity: Int,
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

data class DuplicateResponse(
    val request: ServiceRequestResponse,
    val unavailableItems: List<String>,
)
