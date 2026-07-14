package com.mudhut.nudge.messaging.repositories

import com.mudhut.nudge.messaging.entities.Message
import com.mudhut.nudge.messaging.entities.SenderSide
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface MessageRepository : JpaRepository<Message, Long> {

    fun findByConversationIdOrderBySentAtDesc(conversationId: Long, pageable: Pageable): List<Message>

    fun countByConversationIdAndSenderSideAndSentAtAfter(
        conversationId: Long,
        senderSide: SenderSide,
        after: LocalDateTime,
    ): Long

    fun countByConversationIdAndSenderSide(conversationId: Long, senderSide: SenderSide): Long

    /** Total unread across all of a user's conversations (for the sidebar badge). */
    @Query(
        """
        SELECT COUNT(m) FROM Message m JOIN m.conversation c
        WHERE (c.customer.id = :userId
                 AND m.senderSide = com.mudhut.nudge.messaging.entities.SenderSide.BUSINESS
                 AND (c.customerLastReadAt IS NULL OR m.sentAt > c.customerLastReadAt))
           OR (c.assignedMember.id = :userId
                 AND m.senderSide = com.mudhut.nudge.messaging.entities.SenderSide.CUSTOMER
                 AND (c.memberLastReadAt IS NULL OR m.sentAt > c.memberLastReadAt))
        """
    )
    fun totalUnreadForUser(@Param("userId") userId: Long): Long
}
