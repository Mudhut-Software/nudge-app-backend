package com.mudhut.nudge.messaging.controllers

import com.mudhut.nudge.messaging.models.ConversationResponse
import com.mudhut.nudge.messaging.models.ConversationUpdateEvent
import com.mudhut.nudge.messaging.models.ReassignRequest
import com.mudhut.nudge.messaging.models.MessageResponse
import com.mudhut.nudge.messaging.models.SendMessageRequest
import com.mudhut.nudge.messaging.models.StartConversationRequest
import com.mudhut.nudge.messaging.models.StartWithCustomerRequest
import com.mudhut.nudge.messaging.models.UnreadCountResponse
import com.mudhut.nudge.messaging.services.ConversationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ConversationController(
    private val service: ConversationService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @PostMapping("/api/v1/conversations")
    fun startAsCustomer(
        @Valid @RequestBody request: StartConversationRequest,
        authentication: Authentication,
    ): ConversationResponse = service.startAsCustomer(authentication.name, request.businessId!!)

    @PostMapping("/api/v1/businesses/{businessId}/conversations")
    fun startAsMember(
        @PathVariable businessId: Long,
        @Valid @RequestBody request: StartWithCustomerRequest,
        authentication: Authentication,
    ): ConversationResponse = service.startAsMember(authentication.name, businessId, request.customerId!!)

    @GetMapping("/api/v1/conversations")
    fun list(authentication: Authentication): List<ConversationResponse> =
        service.listForUser(authentication.name)

    /** Full business inbox (OWNER/ADMIN only). */
    @GetMapping("/api/v1/businesses/{businessId}/conversations")
    fun listForBusiness(
        @PathVariable businessId: Long,
        authentication: Authentication,
    ): List<ConversationResponse> = service.listForBusiness(authentication.name, businessId)

    @GetMapping("/api/v1/conversations/unread-count")
    fun unreadCount(authentication: Authentication): UnreadCountResponse =
        UnreadCountResponse(service.unreadCount(authentication.name))

    @GetMapping("/api/v1/conversations/{id}/messages")
    fun messages(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "30") size: Int,
        authentication: Authentication,
    ): List<MessageResponse> = service.getMessages(authentication.name, id, size)

    @PostMapping("/api/v1/conversations/{id}/messages")
    fun send(
        @PathVariable id: Long,
        @Valid @RequestBody request: SendMessageRequest,
        authentication: Authentication,
    ): MessageResponse {
        val (message, conversation) = service.send(authentication.name, id, request.body?.trim() ?: "", request.attachments)
        // Live-deliver to each participant's user queue (their WS subscription to /user/queue/messages).
        service.participantEmails(conversation).forEach { email ->
            messagingTemplate.convertAndSendToUser(email, "/queue/messages", message)
        }
        return message
    }

    @PatchMapping("/api/v1/conversations/{id}/assignee")
    fun reassign(
        @PathVariable id: Long,
        @Valid @RequestBody request: ReassignRequest,
        authentication: Authentication,
    ): ConversationResponse {
        val (convo, affected) = service.reassign(authentication.name, id, request.memberUserId!!)
        affected.forEach {
            messagingTemplate.convertAndSendToUser(it, "/queue/conversation-updates", ConversationUpdateEvent("REASSIGNED", id))
        }
        return convo
    }

    @PostMapping("/api/v1/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markRead(@PathVariable id: Long, authentication: Authentication) {
        service.markRead(authentication.name, id)
    }
}
