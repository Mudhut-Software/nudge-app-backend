package com.mudhut.nudge.businesses.services

import com.mudhut.nudge.businesses.entities.Business
import com.mudhut.nudge.businesses.entities.BusinessPhoneNumber
import com.mudhut.nudge.businesses.entities.BusinessRole
import com.mudhut.nudge.businesses.repositories.BusinessPhoneNumberRepository
import com.mudhut.nudge.businesses.repositories.BusinessRepository
import com.mudhut.nudge.utils.exceptions.BusinessNotFoundException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.*

@ExtendWith(MockitoExtension::class)
class BusinessPhoneNumberServiceTest {

    @Mock
    private lateinit var businessPhoneNumberRepository: BusinessPhoneNumberRepository

    @Mock
    private lateinit var businessRepository: BusinessRepository

    @Mock
    private lateinit var businessService: BusinessService

    @InjectMocks
    private lateinit var phoneNumberService: BusinessPhoneNumberService

    @Test
    fun `addPhoneNumber adds number successfully`() {
        val business = Business(id = 1L, name = "Test Biz")
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(business))
        `when`(businessPhoneNumberRepository.countByBusinessId(1L)).thenReturn(2L)
        `when`(businessPhoneNumberRepository.existsByBusinessIdAndPhoneNumber(1L, "+256700000000")).thenReturn(false)
        `when`(businessPhoneNumberRepository.save(any())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as BusinessPhoneNumber
            entity.id = 10L
            entity
        }

        val result = phoneNumberService.addPhoneNumber(1L, "+256700000000", "admin@test.com")

        assertEquals("+256700000000", result.phoneNumber)
        verify(businessService).requireRole(1L, "admin@test.com", BusinessRole.ADMIN)
    }

    @Test
    fun `addPhoneNumber throws when max reached`() {
        val business = Business(id = 1L, name = "Test Biz")
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(business))
        `when`(businessPhoneNumberRepository.countByBusinessId(1L)).thenReturn(5L)

        assertThrows<IllegalArgumentException> {
            phoneNumberService.addPhoneNumber(1L, "+256700000000", "admin@test.com")
        }
    }

    @Test
    fun `addPhoneNumber throws when duplicate`() {
        val business = Business(id = 1L, name = "Test Biz")
        `when`(businessRepository.findById(1L)).thenReturn(Optional.of(business))
        `when`(businessPhoneNumberRepository.countByBusinessId(1L)).thenReturn(2L)
        `when`(businessPhoneNumberRepository.existsByBusinessIdAndPhoneNumber(1L, "+256700000000")).thenReturn(true)

        assertThrows<IllegalArgumentException> {
            phoneNumberService.addPhoneNumber(1L, "+256700000000", "admin@test.com")
        }
    }

    @Test
    fun `addPhoneNumber throws when business not found`() {
        `when`(businessRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<BusinessNotFoundException> {
            phoneNumberService.addPhoneNumber(99L, "+256700000000", "admin@test.com")
        }
    }

    @Test
    fun `removePhoneNumber removes successfully`() {
        val business = Business(id = 1L, name = "Test Biz")
        val phoneNumber = BusinessPhoneNumber(id = 10L, phoneNumber = "+256700000000", business = business)
        `when`(businessPhoneNumberRepository.findById(10L)).thenReturn(Optional.of(phoneNumber))

        phoneNumberService.removePhoneNumber(1L, 10L, "admin@test.com")

        verify(businessService).requireRole(1L, "admin@test.com", BusinessRole.ADMIN)
        verify(businessPhoneNumberRepository).delete(phoneNumber)
    }

    @Test
    fun `removePhoneNumber throws when phone number not found`() {
        `when`(businessPhoneNumberRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<IllegalArgumentException> {
            phoneNumberService.removePhoneNumber(1L, 99L, "admin@test.com")
        }
    }

    @Test
    fun `removePhoneNumber throws when phone number belongs to different business`() {
        val otherBusiness = Business(id = 2L, name = "Other Biz")
        val phoneNumber = BusinessPhoneNumber(id = 10L, phoneNumber = "+256700000000", business = otherBusiness)
        `when`(businessPhoneNumberRepository.findById(10L)).thenReturn(Optional.of(phoneNumber))

        assertThrows<IllegalArgumentException> {
            phoneNumberService.removePhoneNumber(1L, 10L, "admin@test.com")
        }
    }
}
