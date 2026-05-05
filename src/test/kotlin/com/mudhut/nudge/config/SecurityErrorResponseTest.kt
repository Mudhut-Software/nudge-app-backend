package com.mudhut.nudge.config

import com.mudhut.nudge.businesses.controllers.BusinessCategoryController
import com.mudhut.nudge.businesses.services.BusinessCategoryService
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(BusinessCategoryController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class
)
@AutoConfigureMockMvc
class SecurityErrorResponseTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessCategoryService: BusinessCategoryService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Test
    fun `unauthenticated request returns 401 with AUTHENTICATION_ERROR JSON body`() {
        mockMvc.perform(
            post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Test"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = ["BASIC_USER"])
    fun `authenticated user without role returns 403 with AUTHORIZATION_ERROR JSON body`() {
        mockMvc.perform(
            post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Test"}""")
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.code").value("AUTHORIZATION_ERROR"))
            .andExpect(jsonPath("$.message").exists())
    }
}
