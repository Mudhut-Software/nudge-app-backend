package com.mudhut.nudge.packagesoffered.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedStatus
import com.mudhut.nudge.packagesoffered.entities.PackageOfferedTag
import com.mudhut.nudge.packagesoffered.models.CreatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.models.PackageOfferedResponse
import com.mudhut.nudge.packagesoffered.models.UpdatePackageOfferedRequest
import com.mudhut.nudge.packagesoffered.services.PackagesOfferedService
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

@WebMvcTest(PackageOfferedController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class PackageOfferedControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var packagesService: PackagesOfferedService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun sampleResponse(id: Long = 1L) = PackageOfferedResponse(
        id = id,
        businessId = 10L,
        title = "Combo",
        items = emptyList(),
        priceAmount = BigDecimal("350000.00"),
        priceCurrency = "UGX",
        tag = null,
        validFrom = null,
        validUntil = null,
        status = PackageOfferedStatus.ACTIVE,
        isCurrentlyActive = true,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
    )

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST creates a package and returns 201`() {
        val request = CreatePackageOfferedRequest(
            title = "Combo",
            serviceIds = listOf(10L),
            priceAmount = BigDecimal("350000.00"),
            priceCurrency = "UGX",
        )
        whenever(
            packagesService.createPackage(eq(10L), eq("owner@test.com"), any())
        ).thenReturn(sampleResponse())

        mockMvc.perform(
            post("/api/v1/businesses/10/packages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.priceCurrency").value("UGX"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST returns 400 on missing title`() {
        val payload = """
            {
              "serviceIds": [10],
              "priceAmount": 350000,
              "priceCurrency": "UGX"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/businesses/10/packages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `POST returns 400 on bad currency`() {
        val payload = """
            {
              "title": "Combo",
              "serviceIds": [10],
              "priceAmount": 350000,
              "priceCurrency": "ugx"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/businesses/10/packages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `GET list returns paginated packages`() {
        whenever(
            packagesService.listPackages(eq(10L), eq("owner@test.com"), any<Pageable>(), isNull())
        ).thenReturn(PageImpl(listOf(sampleResponse())))

        mockMvc.perform(get("/api/v1/businesses/10/packages"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `GET list honours status filter`() {
        whenever(
            packagesService.listPackages(
                eq(10L),
                eq("owner@test.com"),
                any<Pageable>(),
                eq(PackageOfferedStatus.ACTIVE),
            )
        ).thenReturn(PageImpl(emptyList()))

        mockMvc.perform(get("/api/v1/businesses/10/packages?status=ACTIVE"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `GET single returns the package`() {
        whenever(packagesService.getPackage(7L, "owner@test.com"))
            .thenReturn(sampleResponse(id = 7L))

        mockMvc.perform(get("/api/v1/packages/7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(7))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `PATCH updates the package`() {
        val request = UpdatePackageOfferedRequest(title = "Renamed", tag = PackageOfferedTag.HOLIDAY_OFFER)
        whenever(packagesService.updatePackage(eq(7L), eq("owner@test.com"), any()))
            .thenReturn(sampleResponse(id = 7L).copy(title = "Renamed", tag = PackageOfferedTag.HOLIDAY_OFFER))

        mockMvc.perform(
            patch("/api/v1/packages/7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.title").value("Renamed"))
            .andExpect(jsonPath("$.tag").value("HOLIDAY_OFFER"))
    }

    @Test
    @WithMockUser(username = "owner@test.com")
    fun `DELETE returns 204`() {
        mockMvc.perform(delete("/api/v1/packages/7"))
            .andExpect(status().isNoContent)
    }
}
