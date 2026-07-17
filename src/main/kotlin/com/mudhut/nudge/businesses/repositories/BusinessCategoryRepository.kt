package com.mudhut.nudge.businesses.repositories

import com.mudhut.nudge.businesses.entities.BusinessCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BusinessCategoryRepository : JpaRepository<BusinessCategory, Long> {
    fun findByParentIsNullAndIsActiveTrue(pageable: Pageable): Page<BusinessCategory>

    fun findByParentIsNullAndIsActiveTrueAndNameContainingIgnoreCase(
        name: String,
        pageable: Pageable
    ): Page<BusinessCategory>

    fun findByParentIdAndIsActiveTrue(parentId: Long): List<BusinessCategory>
    fun existsByName(name: String): Boolean
    fun existsByNameAndIdNot(name: String, id: Long): Boolean
}
