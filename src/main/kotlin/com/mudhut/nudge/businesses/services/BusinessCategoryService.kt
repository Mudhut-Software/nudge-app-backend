package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import org.springframework.stereotype.Service

@Service
class BusinessCategoryService(
    private val categoryRepository: BusinessCategoryRepository
) {

    fun createCategory(request: CreateCategoryRequest): CategoryResponse {
        if (categoryRepository.existsByName(request.name!!)) {
            throw IllegalArgumentException("Category with name '${request.name}' already exists")
        }

        val category = BusinessCategory().apply {
            name = request.name
            description = request.description
        }

        if (request.parentId != null) {
            val parent = categoryRepository.findById(request.parentId!!)
                .orElseThrow { IllegalArgumentException("Parent category not found with id: ${request.parentId}") }
            category.parent = parent
        }

        val saved = categoryRepository.save(category)
        return toResponse(saved)
    }

    fun getTopLevelCategories(): List<CategoryResponse> {
        return categoryRepository.findByParentIsNullAndIsActiveTrue()
            .map { toResponse(it) }
    }

    fun getSubcategories(parentId: Long): List<CategoryResponse> {
        return categoryRepository.findByParentIdAndIsActiveTrue(parentId)
            .map { toResponse(it) }
    }

    private fun toResponse(category: BusinessCategory): CategoryResponse {
        return CategoryResponse(
            id = category.id!!,
            name = category.name!!,
            description = category.description,
            parentId = category.parent?.id,
            isActive = category.isActive,
            hasChildren = category.children.isNotEmpty()
        )
    }
}
