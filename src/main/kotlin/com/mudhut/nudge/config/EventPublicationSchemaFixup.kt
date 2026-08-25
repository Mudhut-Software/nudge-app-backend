package com.mudhut.nudge.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Widens `event_publication.serialized_event` from `varchar(255)` to `text`.
 *
 * Spring Modulith persists every `@ApplicationModuleListener` event as JSON in
 * this table, and the insert happens *inside* the transaction that published
 * it. Modulith's own DDL declares the column `TEXT`, but that script ships with
 * the JDBC variant; this project uses `spring-modulith-starter-jpa`, so the
 * schema comes from `JpaEventPublication` instead — whose `serializedEvent`
 * carries a bare `@Column`. Hibernate therefore defaults it to `varchar(255)`.
 *
 * Any event serialising past 255 characters then fails at commit and rolls back
 * the business transaction that caused it. `ServiceRequestStatusChangedEvent`
 * does exactly that: submitting a request returned 500, with the stack trace
 * pointing at `JpaTransactionManager.doCommit` rather than any of our code.
 *
 * `ddl-auto=update` never widens an existing column, and the `test` profile's
 * `create-drop` recreates it at 255 on every run, so neither profile recovers on
 * its own. This runs the one idempotent `ALTER` instead.
 *
 * Staging and production use `validate`/`none` and take their DDL from the
 * deploy notes, so this is deliberately limited to `dev` and `test`.
 */
@Component
@Profile("dev", "test")
class EventPublicationSchemaFixup(
    private val jdbc: JdbcTemplate,
) {
    private val log = LoggerFactory.getLogger(EventPublicationSchemaFixup::class.java)

    private companion object {
        /**
         * Constants are inlined rather than bound as parameters: they are
         * compile-time literals, and it keeps this to the single-argument
         * `queryForList` overload.
         */
        const val TYPE_QUERY = """
            select data_type from information_schema.columns
            where table_name = 'event_publication'
              and column_name = 'serialized_event'
        """

        const val WIDEN =
            "ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE text"
    }

    /**
     * Runs after the context is up, so Hibernate has finished creating or
     * updating the schema. Requests are served later, so no event can be
     * published before this has had its turn.
     */
    @EventListener(ApplicationReadyEvent::class)
    fun widenSerializedEventColumn() {
        try {
            val dataType = jdbc.queryForList(TYPE_QUERY, String::class.java).firstOrNull()

            when {
                dataType == null ->
                    log.debug("No event_publication.serialized_event column found; nothing to widen")

                dataType.equals("text", ignoreCase = true) ->
                    log.debug("event_publication.serialized_event is already text")

                else -> {
                    jdbc.execute(WIDEN)
                    log.info(
                        "Widened event_publication.serialized_event from {} to text — " +
                            "events larger than 255 chars would otherwise roll back the " +
                            "transaction that published them",
                        dataType,
                    )
                }
            }
        } catch (ex: Exception) {
            // A schema convenience must never stop the application from starting.
            // If this fails, large events break at publish time with a clear
            // DataIntegrityViolationException, which is no worse than before.
            log.warn("Could not widen event_publication.serialized_event", ex)
        }
    }
}
