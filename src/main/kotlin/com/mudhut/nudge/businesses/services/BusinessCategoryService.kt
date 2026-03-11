package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.utils.exceptions.CategoryNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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

    fun getCategoryById(id: Long): CategoryResponse {
        val category = categoryRepository.findById(id)
            .orElseThrow { CategoryNotFoundException("Category not found with id: $id") }
        return toResponse(category)
    }

    @Transactional
    fun updateCategory(id: Long, request: UpdateCategoryRequest): CategoryResponse {
        val category = categoryRepository.findById(id)
            .orElseThrow { CategoryNotFoundException("Category not found with id: $id") }

        request.name?.let { newName ->
            if (categoryRepository.existsByNameAndIdNot(newName, id)) {
                throw IllegalArgumentException("Category with name '$newName' already exists")
            }
            category.name = newName
        }

        request.description?.let { category.description = it }

        request.isActive?.let { category.isActive = it }

        if (request.parentId != null) {
            if (request.parentId == id) {
                throw IllegalArgumentException("A category cannot be its own parent")
            }
            val newParent = categoryRepository.findById(request.parentId)
                .orElseThrow { CategoryNotFoundException("Parent category not found with id: ${request.parentId}") }
            if (isDescendant(newParent, category)) {
                throw IllegalArgumentException("Cannot set parent to a descendant category (circular reference)")
            }
            category.parent = newParent
        }

        val saved = categoryRepository.save(category)
        return toResponse(saved)
    }

    @Transactional
    fun deleteCategory(id: Long) {
        val category = categoryRepository.findById(id)
            .orElseThrow { CategoryNotFoundException("Category not found with id: $id") }

        category.isActive = false
        categoryRepository.save(category)

        if (category.children.isNotEmpty()) {
            category.children.forEach { it.isActive = false }
            categoryRepository.saveAll(category.children)
        }
    }

    private fun isDescendant(potentialDescendant: BusinessCategory, ancestor: BusinessCategory): Boolean {
        var current: BusinessCategory? = potentialDescendant
        while (current != null) {
            if (current.id == ancestor.id) return true
            current = current.parent
        }
        return false
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
