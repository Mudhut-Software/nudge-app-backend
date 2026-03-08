package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.models.CategoryResponse
import com.mudhut.nudge.businesses.models.CreateCategoryRequest
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import com.mudhut.nudge.config.EnvConfig
import com.mudhut.nudge.config.JwtAuthenticationFilter
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.JwtService
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
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@AutoConfigureMockMvc
class BusinessCategoryControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessCategoryService: BusinessCategoryService

    @MockitoBean
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @MockitoBean
    private lateinit var envConfig: EnvConfig

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
}
