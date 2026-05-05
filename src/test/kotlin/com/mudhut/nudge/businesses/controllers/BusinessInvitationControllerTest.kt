package com.mudhut.nudge.businesses.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.entities.InvitationStatus
import com.mudhut.nudge.businesses.models.InvitationResponse
import com.mudhut.nudge.businesses.models.InviteMemberRequest
import com.mudhut.nudge.businesses.services.BusinessInvitationService
import com.mudhut.nudge.config.JsonAccessDeniedHandler
import com.mudhut.nudge.config.JsonAuthenticationEntryPoint
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

@WebMvcTest(BusinessInvitationController::class)
@Import(SecurityConfig::class, PassThroughJwtFilterConfig::class, JsonAuthenticationEntryPoint::class, JsonAccessDeniedHandler::class)
@AutoConfigureMockMvc
class BusinessInvitationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var invitationService: BusinessInvitationService

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
    fun testSendInvitation_Success() {
        val request = InviteMemberRequest(
            email = "newuser@test.com",
            role = BusinessRole.STAFF
        )

        val response = InvitationResponse(
            id = 1L, businessId = 1L, businessName = "Test Biz",
            inviterEmail = "admin@test.com", inviteeEmail = "newuser@test.com",
            role = BusinessRole.STAFF, status = InvitationStatus.PENDING,
            expiryDate = LocalDateTime.now().plusDays(7), createdAt = LocalDateTime.now()
        )

        Mockito.`when`(invitationService.sendInvitation(
            Mockito.eq(1L), anyObject(), Mockito.anyString()
        )).thenReturn(response)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.inviteeEmail").value("newuser@test.com"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("PENDING"))
    }

    @Test
    @WithMockUser(username = "admin@test.com")
    fun testSendInvitation_OwnerRoleRejected() {
        val request = InviteMemberRequest(
            email = "newuser@test.com",
            role = BusinessRole.OWNER
        )

        Mockito.`when`(invitationService.sendInvitation(
            Mockito.eq(1L), anyObject(), Mockito.anyString()
        )).thenThrow(IllegalArgumentException("Cannot invite with OWNER role"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/businesses/1/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @WithMockUser(username = "invitee@test.com")
    fun testAcceptInvitation_Success() {
        Mockito.`when`(invitationService.acceptInvitation(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(GeneralRequestResponse("Invitation accepted"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/invitations/some-token/accept")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Invitation accepted"))
    }

    @Test
    @WithMockUser(username = "invitee@test.com")
    fun testDeclineInvitation_Success() {
        Mockito.`when`(invitationService.declineInvitation(Mockito.anyString(), Mockito.anyString()))
            .thenReturn(GeneralRequestResponse("Invitation declined"))

        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/invitations/some-token/decline")
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Invitation declined"))
    }

    @Test
    @WithMockUser(username = "user@test.com")
    fun testGetMyInvitations_Success() {
        val invitations = listOf(
            InvitationResponse(
                id = 1L, businessId = 1L, businessName = "Test Biz",
                inviterEmail = "admin@test.com", inviteeEmail = "user@test.com",
                role = BusinessRole.STAFF, status = InvitationStatus.PENDING,
                expiryDate = LocalDateTime.now().plusDays(7), createdAt = LocalDateTime.now()
            )
        )

        Mockito.`when`(invitationService.getMyInvitations(Mockito.anyString()))
            .thenReturn(invitations)

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/invitations/my"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
    }
}
