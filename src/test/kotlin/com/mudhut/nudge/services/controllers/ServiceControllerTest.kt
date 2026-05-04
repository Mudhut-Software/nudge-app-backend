package com.mudhut.nudge.services.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.services.entities.PriceMode
import com.mudhut.nudge.services.entities.ServiceStatus
import com.mudhut.nudge.services.models.CreateServiceRequest
import com.mudhut.nudge.services.models.MediaInput
import com.mudhut.nudge.services.models.MediaResponse
import com.mudhut.nudge.services.models.ServiceResponse
import com.mudhut.nudge.services.models.UpdateServiceRequest
import com.mudhut.nudge.services.services.BusinessOfferingService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDateTime

@WebMvcTest(ServiceController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class)
@AutoConfigureMockMvc
class ServiceControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var offeringService: BusinessOfferingService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun sampleResponse(id: Long = 1L) = ServiceResponse(
        id = id,
        businessId = 10L,
        title = "Sofa cleaning",
        description = null,
        coverImage = MediaResponse(url = "u", publicId = "nudge/services/u"),
        priceMode = PriceMode.FIXED,
        priceAmount = BigDecimal("50000.00"),
        priceCurrency = "UGX",
        priceUnit = null,
        status = ServiceStatus.ACTIVE,
        galleryImages = emptyList(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST creates a service and returns 201`() {
        val request = CreateServiceRequest(
            title = "Sofa cleaning",
            description = null,
            coverImage = MediaInput("u", "nudge/services/u"),
            priceMode = PriceMode.FIXED,
            priceAmount = BigDecimal("50000.00"),
            priceCurrency = "UGX",
            priceUnit = null,
            galleryImages = emptyList()
        )
        whenever(
            offeringService.createService(eq(10L), eq("owner@test.com"), any())
        ).thenReturn(sampleResponse())

        mockMvc.perform(
            post("/api/v1/businesses/10/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.priceMode").value("FIXED"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST returns 400 on missing title`() {
        val payload = """
            {
              "coverImage": {"url":"u","publicId":"nudge/services/u"},
              "priceMode": "QUOTE"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/businesses/10/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST returns 400 when publicId does not start with nudge slash services`() {
        val request = CreateServiceRequest(
            title = "Sofa cleaning",
            description = null,
            coverImage = MediaInput("u", "wrong/prefix/u"),
            priceMode = PriceMode.QUOTE,
            priceAmount = null,
            priceCurrency = null,
            priceUnit = null,
            galleryImages = emptyList()
        )
        mockMvc.perform(
            post("/api/v1/businesses/10/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `GET list returns paginated services`() {
        whenever(
            offeringService.listServices(eq(10L), eq("owner@test.com"), any<Pageable>(), isNull())
        ).thenReturn(PageImpl(listOf(sampleResponse())))

        mockMvc.perform(get("/api/v1/businesses/10/services"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `GET list honours status filter`() {
        whenever(
            offeringService.listServices(
                eq(10L),
                eq("owner@test.com"),
                any<Pageable>(),
                eq(ServiceStatus.ACTIVE)
            )
        ).thenReturn(PageImpl(emptyList()))

        mockMvc.perform(get("/api/v1/businesses/10/services?status=ACTIVE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `GET single returns the service`() {
        whenever(offeringService.getService(7L, "owner@test.com")).thenReturn(sampleResponse(id = 7L))

        mockMvc.perform(get("/api/v1/services/7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `PATCH updates the service`() {
        val request = UpdateServiceRequest(title = "Renamed")
        whenever(
            offeringService.updateService(eq(7L), eq("owner@test.com"), any())
        ).thenReturn(sampleResponse(id = 7L).copy(title = "Renamed"))

        mockMvc.perform(
            patch("/api/v1/services/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Renamed"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `DELETE returns 204`() {
        mockMvc.perform(delete("/api/v1/services/7"))
            .andExpect(status().isNoContent)
    }
}
