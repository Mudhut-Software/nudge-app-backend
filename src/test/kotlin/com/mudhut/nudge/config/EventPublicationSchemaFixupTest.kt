package com.mudhut.nudge.config

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate

class EventPublicationSchemaFixupTest {

    private val jdbc = mock<JdbcTemplate>()
    private val fixup = EventPublicationSchemaFixup(jdbc)

    private fun columnReportedAs(dataType: String?) {
        whenever(jdbc.queryForList(any<String>(), eq(String::class.java)))
            .thenReturn(if (dataType == null) emptyList() else listOf(dataType))
    }

    @Test
    fun `widens the column when it is still varchar`() {
        columnReportedAs("character varying")

        fixup.widenSerializedEventColumn()

        verify(jdbc).execute(
            "ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE text"
        )
    }

    @Test
    fun `does nothing when the column is already text`() {
        columnReportedAs("text")

        fixup.widenSerializedEventColumn()

        verify(jdbc, never()).execute(any<String>())
    }

    @Test
    fun `does nothing when the table does not exist`() {
        columnReportedAs(null)

        fixup.widenSerializedEventColumn()

        verify(jdbc, never()).execute(any<String>())
    }

    @Test
    fun `a failing ALTER does not stop the application from starting`() {
        columnReportedAs("character varying")
        whenever(jdbc.execute(any<String>())).thenThrow(RuntimeException("permission denied"))

        // The absence of a thrown exception is the assertion: this runs on
        // ApplicationReadyEvent, and a convenience must not take down startup.
        fixup.widenSerializedEventColumn()
    }

    @Test
    fun `a failing lookup does not stop the application from starting`() {
        whenever(jdbc.queryForList(any<String>(), eq(String::class.java)))
            .thenThrow(RuntimeException("no such catalog"))

        fixup.widenSerializedEventColumn()

        verify(jdbc, never()).execute(any<String>())
    }
}
