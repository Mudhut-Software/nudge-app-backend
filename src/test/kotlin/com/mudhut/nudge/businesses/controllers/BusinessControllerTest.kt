package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessStatus
import com.mudhut.nudge.businesses.models.BusinessResponse
import com.mudhut.nudge.businesses.models.AddPhoneNumberRequest
import com.mudhut.nudge.businesses.models.CreateBusinessRequest
import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.services.BusinessPhoneNumberService
import com.mudhut.nudge.businesses.services.BusinessService
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

@WebMvcTest(BusinessController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class)
@AutoConfigureMockMvc
class BusinessControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessService: BusinessService

    @MockitoBean
    private lateinit var businessPhoneNumberService: BusinessPhoneNumberService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    private fun sampleResponse(
        id: Long = 1L,
        name: String = "Test Pharmacy",
        serviceAreas: List<String> = listOf("Kampala"),
        latitude: Double? = null,
        longitude: Double? = null
    ) = BusinessResponse(
        id = id,
        name = name,
        description = null,
        ownerId = 1L,
        ownerEmail = "owner@test.com",
        categoryId = 1L,
        categoryName = "Healthcare",
        phoneNumbers = emptyList(),
        email = null,
        logoUrl = null,
        address = null,
        latitude = latitude,
        longitude = longitude,
        serviceAreas = serviceAreas,
        status = BusinessStatus.ACTIVE
    )

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testCreateBusiness_Success() {
        val request = CreateBusinessRequest(
            name = "Test Pharmacy",
            categoryId = 1L,
            serviceAreas = listOf("Kampala", "Entebbe"),
            latitude = 0.3476,
            longitude = 32.5825
        )

        val response = sampleResponse(
            serviceAreas = listOf("Kampala", "Entebbe"),
            latitude = 0.3476,
            longitude = 32.5825
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
            .andExpect(MockMvcResultMatchers.jsonPath("$.serviceAreas[0]").value("Kampala"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.serviceAreas[1]").value("Entebbe"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.latitude").value(0.3476))
            .andExpect(MockMvcResultMatchers.jsonPath("$.longitude").value(32.5825))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testGetBusiness_Success() {
        val response = sampleResponse()

        Mockito.`when`(businessService.getBusinessById(1L)).thenReturn(response)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("Test Pharmacy"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun testGetMyBusinesses_Success() {
        val businesses = listOf(sampleResponse(id = 1L, name = "Biz 1"))

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
            serviceAreas = listOf("Kampala")
        )

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testAddPhoneNumber_Success() {
        val request = AddPhoneNumberRequest(phoneNumber = "+256700000000")
        val business = Business(id = 1L, name = "Test Biz")
        val saved = BusinessPhoneNumber(id = 10L, phoneNumber = "+256700000000", business = business)

        Mockito.`when`(businessPhoneNumberService.addPhoneNumber(
            Mockito.anyLong(), anyObject(), Mockito.anyString()
        )).thenReturn(saved)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/phone-numbers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(10))
            .andExpect(MockMvcResultMatchers.jsonPath("$.phoneNumber").value("+256700000000"))
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testRemovePhoneNumber_Success() {
        Mockito.doNothing().`when`(businessPhoneNumberService)
            .removePhoneNumber(Mockito.eq(1L), Mockito.eq(10L), Mockito.anyString())

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/businesses/1/phone-numbers/10"))
            .andExpect(MockMvcResultMatchers.status().isNoContent)
    }

    @Test
    fun testAddPhoneNumber_Unauthenticated() {
        val request = AddPhoneNumberRequest(phoneNumber = "+256700000000")

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/phone-numbers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }
}
