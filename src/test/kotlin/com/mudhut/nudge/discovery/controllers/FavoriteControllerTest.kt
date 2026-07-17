package com.mudhut.nudge.discovery.controllers

import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.discovery.models.PublicBusinessSummary
import com.mudhut.nudge.discovery.services.FavoriteService
import com.mudhut.nudge.users.services.NudgeUserDetailsService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(FavoriteController::class)
@Import(
    SecurityConfig::class,
    PassThroughJwtFilterConfig::class,
    JsonAuthenticationEntryPoint::class,
    JsonAccessDeniedHandler::class,
)
@AutoConfigureMockMvc
class FavoriteControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var service: FavoriteService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Test
    @WithMockUser(username = "a@e.com")
    fun `PUT favorite returns 204 and adds`() {
        mockMvc.perform(put("/api/v1/businesses/10/favorite"))
            .andExpect(status().isNoContent)
        verify(service).addFavorite(eq("a@e.com"), eq(10L))
    }

    @Test
    @WithMockUser(username = "a@e.com")
    fun `DELETE favorite returns 204 and removes`() {
        mockMvc.perform(delete("/api/v1/businesses/10/favorite"))
            .andExpect(status().isNoContent)
        verify(service).removeFavorite(eq("a@e.com"), eq(10L))
    }

    @Test
    @WithMockUser(username = "a@e.com")
    fun `GET favorites returns the saved businesses`() {
        whenever(service.listFavorites(eq("a@e.com")))
            .thenReturn(listOf(PublicBusinessSummary(10L, "Biz", 2L, "Catering", null, null, 3, null)))
        mockMvc.perform(get("/api/v1/users/me/favorites"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(10))
            .andExpect(jsonPath("$[0].serviceCount").value(3))
    }
}
