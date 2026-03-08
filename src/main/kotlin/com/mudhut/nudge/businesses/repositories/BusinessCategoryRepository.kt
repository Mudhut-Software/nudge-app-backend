package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessCategoryRepository : JpaRepository<BusinessCategory, Long> {
    fun findByParentIsNullAndIsActiveTrue(): List<BusinessCategory>
    fun findByParentIdAndIsActiveTrue(parentId: Long): List<BusinessCategory>
    fun existsByName(name: String): Boolean
}
