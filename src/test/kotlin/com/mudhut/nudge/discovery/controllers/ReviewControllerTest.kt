package com.mudhut.nudge.discovery.controllers

import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.discovery.models.ReviewResponse
import com.mudhut.nudge.discovery.services.ReviewService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

@WebMvcTest(ReviewController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class ReviewControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: ReviewService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Test
    fun `GET reviews is public`() {
        whenever(service.list(eq(10L), any<Pageable>())).thenReturn(PageImpl(emptyList()))
        mockMvc.perform(get("/api/v1/businesses/10/reviews"))
            .andExpect(status().isOk)
    }

    @Test
    fun `GET reviews-me is authenticated`() {
        mockMvc.perform(get("/api/v1/businesses/10/reviews/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(username = "u1@e.com")
    fun `POST submits a review`() {
        whenever(service.submit(eq("u1@e.com"), eq(10L), any()))
            .thenReturn(ReviewResponse(1L, 5, "great", "Alice", LocalDateTime.now()))
        mockMvc.perform(
            post("/api/v1/businesses/10/reviews").contentType(MediaType.APPLICATION_JSON)
                .content("""{"rating":5,"comment":"great"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rating").value(5))
    }
}
