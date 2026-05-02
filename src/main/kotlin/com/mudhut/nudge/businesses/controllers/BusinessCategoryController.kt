package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/categories")
class BusinessCategoryController(
    private val categoryService: BusinessCategoryService
) {

    @PostMapping
    fun createCategory(@Valid @RequestBody request: CreateCategoryRequest): ResponseEntity<CategoryResponse> {
        val category = categoryService.createCategory(request)
        return ResponseEntity(category, HttpStatus.CREATED)
    }

    @GetMapping
    fun getTopLevelCategories(
        @PageableDefault(size = 20, sort = ["name"], direction = Sort.Direction.ASC) pageable: Pageable,
        @RequestParam(required = false) search: String?
    ): ResponseEntity<Page<CategoryResponse>> {
        return ResponseEntity.ok(categoryService.getTopLevelCategories(pageable, search))
    }

    @GetMapping("/{id}")
    fun getCategoryById(@PathVariable id: Long): ResponseEntity<CategoryResponse> {
        return ResponseEntity.ok(categoryService.getCategoryById(id))
    }

    @GetMapping("/{id}/subcategories")
    fun getSubcategories(@PathVariable id: Long): ResponseEntity<List<CategoryResponse>> {
        return ResponseEntity.ok(categoryService.getSubcategories(id))
    }

    @PutMapping("/{id}")
    fun updateCategory(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCategoryRequest
    ): ResponseEntity<CategoryResponse> {
        return ResponseEntity.ok(categoryService.updateCategory(id, request))
    }

    @DeleteMapping("/{id}")
    fun deleteCategory(@PathVariable id: Long): ResponseEntity<Void> {
        categoryService.deleteCategory(id)
        return ResponseEntity.noContent().build()
    }
}
