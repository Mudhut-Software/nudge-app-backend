package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.models.BusinessMemberResponse
import com.mudhut.nudge.businesses.models.UpdateMemberRoleRequest
import com.mudhut.nudge.businesses.services.BusinessMemberService
import com.mudhut.nudge.config.PassThroughJwtFilterConfig
import com.mudhut.nudge.config.SecurityConfig
import com.mudhut.nudge.users.services.helpers.NudgeUserDetailsService
import com.mudhut.nudge.utils.models.GeneralRequestResponse
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
import java.time.LocalDateTime

@WebMvcTest(BusinessMemberController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class)
@AutoConfigureMockMvc
class BusinessMemberControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var businessMemberService: BusinessMemberService

    @MockitoBean
    private lateinit var userDetailsService: NudgeUserDetailsService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun <T> anyObject(): T {
        Mockito.any<T>()
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testGetMembers_Success() {
        val members = listOf(
            BusinessMemberResponse(
                id = 1L, userId = 1L, userEmail = "owner@test.com",
                businessId = 1L, role = BusinessRole.OWNER, isActive = true,
                joinedAt = LocalDateTime.now()
            )
        )

        Mockito.`when`(businessMemberService.getMembers(Mockito.eq(1L), Mockito.anyString()))
            .thenReturn(members)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/businesses/1/members"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].role").value("OWNER"))
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testUpdateMemberRole_Success() {
        val request = UpdateMemberRoleRequest(role = BusinessRole.MANAGER)
        val response = BusinessMemberResponse(
            id = 2L, userId = 2L, userEmail = "staff@test.com",
            businessId = 1L, role = BusinessRole.MANAGER, isActive = true,
            joinedAt = LocalDateTime.now()
        )

        Mockito.`when`(businessMemberService.updateMemberRole(
            Mockito.eq(1L), Mockito.eq(2L), anyObject(), Mockito.anyString()
        )).thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.put("/api/v1/businesses/1/members/2/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.role").value("MANAGER"))
    }

    @Test
    @WithMockUser(username = "member@test.com")
    fun testLeaveBusiness_Success() {
        Mockito.`when`(businessMemberService.leaveBusiness(Mockito.eq(1L), Mockito.anyString()))
            .thenReturn(GeneralRequestResponse("Successfully left the business"))

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/v1/businesses/1/members/me"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Successfully left the business"))
    }
}
