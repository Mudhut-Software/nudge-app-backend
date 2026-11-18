package com.mudhut.nudge.servicesoffered.controllers

import tools.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.servicesoffered.entities.ServiceOfferedStatus
import com.mudhut.nudge.servicesoffered.models.CreateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.models.MediaInput
import com.mudhut.nudge.servicesoffered.models.MediaResponse
import com.mudhut.nudge.servicesoffered.models.ServiceOfferedResponse
import com.mudhut.nudge.servicesoffered.models.UpdateServiceOfferedRequest
import com.mudhut.nudge.servicesoffered.services.ServicesOfferedService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
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

@WebMvcTest(ServiceOfferedController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class ServiceOfferedControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var offeringService: ServicesOfferedService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun sampleResponse(id: Long = 1L) = ServiceOfferedResponse(
        id = id,
        businessId = 10L,
        title = "Sofa cleaning",
        description = null,
        coverImage = MediaResponse(url = "u", publicId = "nudge/images/u"),
        priceAmount = BigDecimal("50000.00"),
        priceCurrency = "UGX",
        priceIsEstimate = false,
        estimateReason = null,
        quantityEnabled = false,
        quantityUnit = null,
        quantityMin = 1,
        quantityMax = null,
        soldOnItsOwn = true,
        status = ServiceOfferedStatus.ACTIVE,
        tag = null,
        validFrom = null,
        validUntil = null,
        isCurrentlyActive = true,
        galleryImages = emptyList(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST creates a service and returns 201`() {
        val request = CreateServiceOfferedRequest(
            title = "Sofa cleaning",
            description = null,
            coverImage = MediaInput("u", "nudge/images/u"),
            priceAmount = BigDecimal("50000.00"),
            priceCurrency = "UGX",
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
            .andExpect(jsonPath("$.priceAmount").value(50000.00))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST services without coverImage returns 201`() {
        whenever(
            offeringService.createService(eq(10L), eq("owner@test.com"), any())
        ).thenReturn(sampleResponse().copy(coverImage = null))

        mockMvc.perform(
            post("/api/v1/businesses/10/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title": "Wedding cake"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.coverImage").doesNotExist())
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `GET list passes q to the service`() {
        whenever(
            offeringService.listServices(eq(10L), eq("owner@test.com"), any<Pageable>(), isNull(), eq("wed"))
        ).thenReturn(PageImpl(emptyList()))

        mockMvc.perform(get("/api/v1/businesses/10/services?q=wed"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST returns 400 on missing title`() {
        val payload = """
            {
              "coverImage": {"url":"u","publicId":"nudge/images/u"}
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
    fun `POST returns 400 when publicId is not under nudge slash images or nudge slash videos`() {
        val request = CreateServiceOfferedRequest(
            title = "Sofa cleaning",
            description = null,
            coverImage = MediaInput("u", "wrong/prefix/u"),
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
            offeringService.listServices(eq(10L), eq("owner@test.com"), any<Pageable>(), isNull(), isNull())
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
                eq(ServiceOfferedStatus.ACTIVE),
                isNull()
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
        val request = UpdateServiceOfferedRequest(title = "Renamed")
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
