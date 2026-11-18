package com.mudhut.nudge.servicesoffered.repositories

import com.mudhut.nudge.servicesoffered.entities.GroupChoice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface GroupChoiceRepository : JpaRepository<GroupChoice, Long> {
    /** Every choice that links the given item into some group (of any parent item). */
    fun findAllByItemId(itemId: Long): List<GroupChoice>
}
