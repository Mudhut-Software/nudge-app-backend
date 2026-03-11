package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.BusinessCategory
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
import com.mudhut.nudge.businesses.repositories.BusinessCategoryRepository
import com.mudhut.nudge.utils.exceptions.CategoryNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class BusinessCategoryServiceTest {

    @Mock
    private lateinit var categoryRepository: BusinessCategoryRepository

    @InjectMocks
    private lateinit var categoryService: BusinessCategoryService

    @Test
    fun `getCategoryById returns category when found`() {
        val category = BusinessCategory(id = 1L, name = "Healthcare", description = "Health services")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))

        val result = categoryService.getCategoryById(1L)

        assertEquals(1L, result.id)
        assertEquals("Healthcare", result.name)
    }

    @Test
    fun `getCategoryById throws CategoryNotFoundException when not found`() {
        `when`(categoryRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<CategoryNotFoundException> {
            categoryService.getCategoryById(99L)
        }
    }

    @Test
    fun `updateCategory updates name and description`() {
        val category = BusinessCategory(id = 1L, name = "Old Name", description = "Old desc")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))
        `when`(categoryRepository.existsByNameAndIdNot("New Name", 1L)).thenReturn(false)
        `when`(categoryRepository.save(any())).thenAnswer { it.arguments[0] }

        val request = UpdateCategoryRequest(name = "New Name", description = "New desc")
        val result = categoryService.updateCategory(1L, request)

        assertEquals("New Name", result.name)
        assertEquals("New desc", result.description)
    }

    @Test
    fun `updateCategory throws when new name already exists`() {
        val category = BusinessCategory(id = 1L, name = "Old Name")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))
        `when`(categoryRepository.existsByNameAndIdNot("Taken Name", 1L)).thenReturn(true)

        val request = UpdateCategoryRequest(name = "Taken Name")

        assertThrows<IllegalArgumentException> {
            categoryService.updateCategory(1L, request)
        }
    }

    @Test
    fun `updateCategory prevents setting parent to self`() {
        val category = BusinessCategory(id = 1L, name = "Cat")
        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))

        val request = UpdateCategoryRequest(parentId = 1L)

        assertThrows<IllegalArgumentException> {
            categoryService.updateCategory(1L, request)
        }
    }

    @Test
    fun `updateCategory prevents circular reference`() {
        val parent = BusinessCategory(id = 1L, name = "Parent")
        val child = BusinessCategory(id = 2L, name = "Child", parent = parent)
        parent.children = mutableListOf(child)

        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(parent))
        `when`(categoryRepository.findById(2L)).thenReturn(Optional.of(child))

        val request = UpdateCategoryRequest(parentId = 2L)

        assertThrows<IllegalArgumentException> {
            categoryService.updateCategory(1L, request)
        }
    }

    @Test
    fun `updateCategory allows reparenting to valid parent`() {
        val category = BusinessCategory(id = 1L, name = "Cat")
        val newParent = BusinessCategory(id = 3L, name = "New Parent")

        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(category))
        `when`(categoryRepository.findById(3L)).thenReturn(Optional.of(newParent))
        `when`(categoryRepository.save(any())).thenAnswer { it.arguments[0] }

        val request = UpdateCategoryRequest(parentId = 3L)
        val result = categoryService.updateCategory(1L, request)

        assertEquals(3L, result.parentId)
    }

    @Test
    fun `deleteCategory soft deletes category and cascades to children`() {
        val child1 = BusinessCategory(id = 2L, name = "Child1", isActive = true)
        val child2 = BusinessCategory(id = 3L, name = "Child2", isActive = true)
        val parent = BusinessCategory(id = 1L, name = "Parent", isActive = true)
        parent.children = mutableListOf(child1, child2)
        child1.parent = parent
        child2.parent = parent

        `when`(categoryRepository.findById(1L)).thenReturn(Optional.of(parent))

        categoryService.deleteCategory(1L)

        assertFalse(parent.isActive)
        assertFalse(child1.isActive)
        assertFalse(child2.isActive)
        verify(categoryRepository).save(parent)
        verify(categoryRepository).saveAll(listOf(child1, child2))
    }

    @Test
    fun `deleteCategory throws when category not found`() {
        `when`(categoryRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<CategoryNotFoundException> {
            categoryService.deleteCategory(99L)
        }
    }
}
