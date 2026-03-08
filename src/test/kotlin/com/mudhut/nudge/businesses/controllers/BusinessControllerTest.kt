package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.models.BusinessResponse
import com.mudhut.nudge.businesses.models.CreateBusinessRequest
import com.mudhut.nudge.businesses.services.BusinessService
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

@WebMvcTest(BusinessController::class)
@Import(SecurityConfig::class, JwtAuthenticationFilter::class)
@AutoConfigureMockMvc
class BusinessControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessService: BusinessService

    @MockitoBean
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @MockitoBean
    private lateinit var envConfig: EnvConfig

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testCreateBusiness_Success() {
        val request = CreateBusinessRequest(
            name = "Test Pharmacy",
            categoryId = 1L,
            serviceArea = "Kampala"
        )

        val response = BusinessResponse(
            id = 1L,
            name = "Test Pharmacy",
            description = null,
            ownerId = 1L,
            ownerEmail = "owner@test.com",
            categoryId = 1L,
            categoryName = "Healthcare",
            phone = null,
            email = null,
            logoUrl = null,
            address = null,
            serviceArea = "Kampala",
            status = BusinessStatus.ACTIVE
        )

        Mockito.`when`(businessService.createBusiness(anyObject(), Mockito.anyString()))
            .thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Test Pharmacy"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.serviceArea").value("Kampala"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testGetBusiness_Success() {
        val response = BusinessResponse(
            id = 1L,
            name = "Test Pharmacy",
            description = null,
            ownerId = 1L,
            ownerEmail = "owner@test.com",
            categoryId = 1L,
            categoryName = "Healthcare",
            phone = null,
            email = null,
            logoUrl = null,
            address = null,
            serviceArea = "Kampala",
            status = BusinessStatus.ACTIVE
        )

        Mockito.`when`(businessService.getBusinessById(1L)).thenReturn(response)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Test Pharmacy"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testGetMyBusinesses_Success() {
        val businesses = listOf(
            BusinessResponse(
                id = 1L, name = "Biz 1", description = null, ownerId = 1L,
                ownerEmail = "owner@test.com", categoryId = 1L, categoryName = "Healthcare",
                phone = null, email = null, logoUrl = null, address = null,
                serviceArea = "Kampala", status = BusinessStatus.ACTIVE
            )
        )

        Mockito.`when`(businessService.getMyBusinesses(Mockito.anyString())).thenReturn(businesses)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/my"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
    }

    @Test
    fun testCreateBusiness_Unauthenticated() {
        val request = CreateBusinessRequest(
            name = "Test Pharmacy",
            categoryId = 1L,
            serviceArea = "Kampala"
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }
}
