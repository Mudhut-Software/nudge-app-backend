package com.mudhut.nudge.businesses.publicapi.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.publicapi.models.ExploreLane
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessDetail
import com.mudhut.nudge.businesses.publicapi.models.PublicBusinessSummary
import com.mudhut.nudge.businesses.publicapi.services.PublicBrowseService
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException

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

    @Test
    fun `GET lanes returns 200 anonymously`() {
        whenever(publicBrowseService.lanes()).thenReturn(
            listOf(
                ExploreLane(
                    categoryId = 1L,
                    categoryName = "Catering",
                    businesses = listOf(
                        PublicBusinessSummary(
                            id = 10L,
                            name = "Elite",
                            categoryId = 1L,
                            categoryName = "Catering",
                            address = "Kampala",
                            coverImageUrl = "https://cdn/cover.jpg",
                            serviceCount = 3,
                            packageCount = 1,
                        )
                    )
                )
            )
        )

        mockMvc.perform(get("/api/v1/businesses/public/explore/lanes"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].categoryName").value("Catering"))
            .andExpect(jsonPath("$[0].businesses[0].id").value(10))
            .andExpect(jsonPath("$[0].businesses[0].coverImageUrl").value("https://cdn/cover.jpg"))
    }

    @Test
    fun `GET businesses public list returns 200 anonymously with paged result`() {
        whenever(publicBrowseService.byCategory(eq(1L), any<Pageable>()))
            .thenReturn(
                PageImpl(
                    listOf(
                        PublicBusinessSummary(
                            id = 10L, name = "Elite", categoryId = 1L, categoryName = "Catering",
                            address = "Kampala", coverImageUrl = "x",
                            serviceCount = 1, packageCount = 0,
                        )
                    )
                )
            )

        mockMvc.perform(get("/api/v1/businesses/public?category=1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].id").value(10))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `GET businesses public list returns 400 when category param missing`() {
        mockMvc.perform(get("/api/v1/businesses/public"))
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
            .thenThrow(ResponseStatusException(HttpStatus.NOT_FOUND, "Business not found"))

        mockMvc.perform(get("/api/v1/businesses/public/404"))
            .andExpect(status().isNotFound)
    }
}
