package com.mudhut.nudge.servicerequests.spi

import com.mudhut.nudge.servicerequests.repositories.ServiceRequestRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class CustomerDirectoryQueryImplTest {
    @Mock private lateinit var repo: ServiceRequestRepository
    @InjectMocks private lateinit var impl: CustomerDirectoryQueryImpl

    @Test
    fun `isCustomerOfBusiness is true when a service request exists for that business and customer`() {
        `when`(repo.existsByBusinessIdAndCustomerId(1L, 9L)).thenReturn(true)
        assertTrue(impl.isCustomerOfBusiness(1L, 9L))
    }

    @Test
    fun `isCustomerOfBusiness is false when no service request links the customer to the business`() {
        `when`(repo.existsByBusinessIdAndCustomerId(1L, 9L)).thenReturn(false)
        assertFalse(impl.isCustomerOfBusiness(1L, 9L))
    }
}
