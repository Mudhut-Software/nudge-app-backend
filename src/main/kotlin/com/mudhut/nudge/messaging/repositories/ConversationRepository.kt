package com.mudhut.nudge.messaging.repositories

import com.mudhut.nudge.messaging.entities.Conversation
import com.mudhut.nudge.messaging.models.PresenceTarget
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

    /** Every conversation the email participates in, with the OTHER party's email. */
    @Query(
        """
        SELECT new com.mudhut.nudge.messaging.models.PresenceTarget(
            c.id,
            CASE WHEN cust.email = :email THEN mem.email ELSE cust.email END
        )
        FROM Conversation c
        JOIN c.customer cust
        LEFT JOIN c.assignedMember mem
        WHERE cust.email = :email OR mem.email = :email
        """
    )
    fun findPresenceTargets(@Param("email") email: String): List<PresenceTarget>
}
