package com.mudhut.nudge.businesses.controllers

import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import jakarta.validation.Valid
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
    fun getTopLevelCategories(): ResponseEntity<List<CategoryResponse>> {
        return ResponseEntity.ok(categoryService.getTopLevelCategories())
    }

    @GetMapping("/{id}/subcategories")
    fun getSubcategories(@PathVariable id: Long): ResponseEntity<List<CategoryResponse>> {
        return ResponseEntity.ok(categoryService.getSubcategories(id))
    }
}
