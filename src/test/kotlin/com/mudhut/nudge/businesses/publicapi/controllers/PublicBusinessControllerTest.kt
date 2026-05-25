package com.mudhut.nudge.businesses.publicapi.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.publicapi.models.BusinessSort
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessDetail
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessSummary
import com.mudhut.nudge.businesses.publicapi.services.PublicBrowseService
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(PublicBusinessController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class PublicBusinessControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var publicBrowseService: PublicBrowseService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    private fun summary(id: Long = 10L, distanceKm: Double? = null) = PublicBusinessSummary(
        id = id, name = "Elite", categoryId = 1L, categoryName = "Catering",
        address = "Kampala", coverImageUrl = "x",
        serviceCount = 1, packageCount = 0, distanceKm = distanceKm,
    )

    @Test
    fun `GET list defaults sort to POPULAR when omitted`() {
        whenever(publicBrowseService.list(eq(null), eq(BusinessSort.POPULAR), eq(null), eq(null), any<Pageable>()))
            .thenReturn(PageImpl(listOf(summary())))

        mockMvc.perform(get("/api/v1/businesses/public"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(10))

        verify(publicBrowseService).list(eq(null), eq(BusinessSort.POPULAR), eq(null), eq(null), any<Pageable>())
    }

    @Test
    fun `GET list with category and sort=newest passes both through`() {
        whenever(publicBrowseService.list(eq(1L), eq(BusinessSort.NEWEST), eq(null), eq(null), any<Pageable>()))
            .thenReturn(PageImpl(listOf(summary())))

        mockMvc.perform(get("/api/v1/businesses/public?category=1&sort=NEWEST"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(10))

        verify(publicBrowseService).list(eq(1L), eq(BusinessSort.NEWEST), eq(null), eq(null), any<Pageable>())
    }

    @Test
    fun `GET list with sort=nearest and lat lng returns 200 with distanceKm`() {
        whenever(publicBrowseService.list(eq(null), eq(BusinessSort.NEAREST), eq(0.31), eq(32.58), any<Pageable>()))
            .thenReturn(PageImpl(listOf(summary(distanceKm = 2.4))))

        mockMvc.perform(get("/api/v1/businesses/public?sort=NEAREST&lat=0.31&lng=32.58"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].distanceKm").value(2.4))
    }

    @Test
    fun `GET list with sort=nearest but missing lat lng returns 400`() {
        mockMvc.perform(get("/api/v1/businesses/public?sort=NEAREST"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET list with sort=nearest but only lat returns 400`() {
        mockMvc.perform(get("/api/v1/businesses/public?sort=NEAREST&lat=0.31"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET businesses public detail returns 200 anonymously`() {
        whenever(publicBrowseService.detail(10L)).thenReturn(
            PublicBusinessDetail(
                id = 10L, name = "Elite", description = null, logoUrl = null,
                categoryId = 1L, categoryName = "Catering",
                address = "Kampala",
                phoneNumbers = listOf("+256700000000"),
                email = "elite@example.com",
                serviceAreas = listOf("Kampala"),
                coverImageUrl = "https://cdn/cover.jpg",
                services = emptyList(),
                packages = emptyList(),
            )
        )

        mockMvc.perform(get("/api/v1/businesses/public/10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.name").value("Elite"))
            .andExpect(jsonPath("$.phoneNumbers[0]").value("+256700000000"))
    }

    @Test
    fun `GET businesses public detail returns 404 when service throws`() {
        whenever(publicBrowseService.detail(404L))
            .thenThrow(BusinessNotFoundException("Business not found"))

        mockMvc.perform(get("/api/v1/businesses/public/404"))
            .andExpect(status().isNotFound)
    }
}
