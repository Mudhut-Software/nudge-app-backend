package com.mudhut.nudge.config

import com.mudhut.nudge.users.services.AccessTokenBlocklistService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class BlocklistCleanupJobTest {

    @Mock private lateinit var blocklistService: AccessTokenBlocklistService
    @InjectMocks private lateinit var job: BlocklistCleanupJob

    @Test
    fun `purge delegates to AccessTokenBlocklistService`() {
        `when`(blocklistService.purgeExpired()).thenReturn(0)
        job.purge()
        verify(blocklistService).purgeExpired()
    }
}
