package com.mudhut.nudge.servicerequests.repositories

import com.mudhut.nudge.servicerequests.entities.ServiceRequestItemPick
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ServiceRequestItemPickRepository : JpaRepository<ServiceRequestItemPick, Long> {

    /** Releases picks' pointers to choices about to be deleted; their snapshots stay. */
    @Modifying
    @Query("UPDATE ServiceRequestItemPick p SET p.choice = NULL WHERE p.choice.id IN :choiceIds")
    fun nullifyChoiceReference(@Param("choiceIds") choiceIds: Collection<Long>): Int
}
