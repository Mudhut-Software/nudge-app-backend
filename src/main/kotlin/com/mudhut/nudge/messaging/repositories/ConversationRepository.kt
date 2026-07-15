package com.mudhut.nudge.messaging.repositories

import com.mudhut.nudge.messaging.entities.Conversation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ConversationRepository : JpaRepository<Conversation, Long> {

    fun findByCustomerIdAndBusinessId(customerId: Long, businessId: Long): Optional<Conversation>

    @Query(
        """
        SELECT c FROM Conversation c
        WHERE c.customer.id = :userId OR c.assignedMember.id = :userId
        ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC
        """
    )
    fun findForUser(@Param("userId") userId: Long): List<Conversation>

    fun countByBusinessIdAndAssignedMemberId(businessId: Long, assignedMemberId: Long): Long
}
