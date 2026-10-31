package com.mudhut.nudge.utils.exceptions

import com.mudhut.nudge.businesses.controllers.BusinessCategoryController
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * A URL no controller maps must read as a client mistake (404), not a server
 * fault (500). Spring raises `NoResourceFoundException` for it, which used to
 * fall through to the catch-all handler.
 */
@WebMvcTest(BusinessCategoryController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class
)
@AutoConfigureMockMvc
class UnknownRouteResponseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessCategoryService: BusinessCategoryService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Test
    @WithMockUser(username = "user@test.com", roles = ["BASIC_USER"])
    fun `unknown GET path returns 404 with NOT_FOUND_ERROR JSON body`() {
        mockMvc.perform(get("/api/v1/does-not-exist"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND_ERROR"))
            .andExpect(jsonPath("$.message").value("No endpoint GET /api/v1/does-not-exist"))
    }

    @Test
    fun `unknown path under a public prefix returns 404, not 500`() {
        // `/api/v1/auth/**` is permitAll, so this reaches MVC without a user —
        // the exact request that surfaced the bug.
        mockMvc.perform(post("/api/v1/auth/sessions"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("NOT_FOUND_ERROR"))
    }
}
