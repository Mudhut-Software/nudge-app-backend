package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.models.UpdateCategoryRequest
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import com.mudhut.nudge.utils.exceptions.CategoryNotFoundException
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(BusinessCategoryController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class)
@AutoConfigureMockMvc
class BusinessCategoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessCategoryService: BusinessCategoryService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    // Kotlin-safe wrapper for Mockito.any() to avoid null issues
    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun testCreateCategory_Success() {
        val request = CreateCategoryRequest(
            name = "Healthcare",
            description = "Healthcare services"
        )

        val response = CategoryResponse(
            id = 1L,
            name = "Healthcare",
            description = "Healthcare services",
            parentId = null,
            isActive = true,
            hasChildren = false
        )

        Mockito.`when`(businessCategoryService.createCategory(anyObject()))
            .thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Healthcare"))
    }

    @Test
    @WithMockUser(roles = ["BASIC_USER"])
    fun testCreateCategory_ForbiddenForBasicUser() {
        val request = CreateCategoryRequest(name = "Healthcare")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    @WithMockUser
    fun testGetTopLevelCategories_Success() {
        val categories = listOf(
            CategoryResponse(1L, "Healthcare", null, null, true, true),
            CategoryResponse(2L, "Home Services", null, null, true, false)
        )

        Mockito.`when`(businessCategoryService.getTopLevelCategories()).thenReturn(categories)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("Healthcare"))
    }

    @Test
    @WithMockUser
    fun testGetSubcategories_Success() {
        val subcategories = listOf(
            CategoryResponse(3L, "E-Pharmacy", "Online pharmacy", 1L, true, false)
        )

        Mockito.`when`(businessCategoryService.getSubcategories(1L)).thenReturn(subcategories)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories/1/subcategories"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("E-Pharmacy"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].parentId").value(1))
    }

    // --- GET by ID tests ---

    @Test
    @WithMockUser
    fun testGetCategoryById_Success() {
        val response = CategoryResponse(1L, "Healthcare", "Health services", null, true, true)

        Mockito.`when`(businessCategoryService.getCategoryById(1L)).thenReturn(response)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories/1"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Healthcare"))
    }

    @Test
    @WithMockUser
    fun testGetCategoryById_NotFound() {
        Mockito.`when`(businessCategoryService.getCategoryById(99L))
            .thenThrow(CategoryNotFoundException("Category not found with id: 99"))

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/categories/99"))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    // --- UPDATE tests ---

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun testUpdateCategory_Success() {
        val request = UpdateCategoryRequest(name = "Updated Name", description = "Updated desc")
        val response = CategoryResponse(1L, "Updated Name", "Updated desc", null, true, false)

        Mockito.`when`(businessCategoryService.updateCategory(Mockito.eq(1L), anyObject()))
            .thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Updated Name"))
    }

    @Test
    @WithMockUser(roles = ["BASIC_USER"])
    fun testUpdateCategory_ForbiddenForBasicUser() {
        val request = UpdateCategoryRequest(name = "Updated Name")

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun testUpdateCategory_NotFound() {
        Mockito.`when`(businessCategoryService.updateCategory(Mockito.eq(99L), anyObject()))
            .thenThrow(CategoryNotFoundException("Category not found with id: 99"))

        val request = UpdateCategoryRequest(name = "New Name")

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/categories/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    // --- DELETE tests ---

    @Test
    @WithMockUser(roles = ["SUPER_ADMIN"])
    fun testDeleteCategory_Success() {
        Mockito.doNothing().`when`(businessCategoryService).deleteCategory(1L)

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/categories/1"))
            .andExpect(MockMvcResultMatchers.status().isNoContent)
    }

    @Test
    @WithMockUser(roles = ["BASIC_USER"])
    fun testDeleteCategory_ForbiddenForBasicUser() {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/categories/1"))
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["ADMIN"])
    fun testDeleteCategory_NotFound() {
        Mockito.doThrow(CategoryNotFoundException("Category not found with id: 99"))
            .`when`(businessCategoryService).deleteCategory(99L)

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/categories/99"))
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }
}
