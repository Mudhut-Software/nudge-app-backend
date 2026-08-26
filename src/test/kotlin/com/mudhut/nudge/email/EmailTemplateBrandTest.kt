package com.mudhut.nudge.email

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.core.io.support.PathMatchingResourcePatternResolver

/**
 * Every outbound email must carry the current brand accent.
 *
 * The palette moved from red to teal on the frontend, but these templates are
 * server-rendered and no build step touches them — so they silently kept the
 * retired `#E42313` long after the app itself had stopped using it. A customer
 * opening a verification email got the old brand.
 *
 * Generated per template rather than asserted in one pass, so a failure names
 * the offending file instead of reporting that "some template" is wrong. New
 * templates are picked up automatically.
 */
class EmailTemplateBrandTest {

    private companion object {
        /** The current accent. Mirrors `--color-brand` in the frontend's index.css. */
        const val BRAND = "#0F766E"

        /** The pre-teal brand. Must not appear anywhere. */
        const val RETIRED = "E42313"
    }

    private fun templates() = PathMatchingResourcePatternResolver()
        .getResources("classpath*:templates/emails/*.html")
        .map { it.filename!! to it.inputStream.bufferedReader().readText() }

    @TestFactory
    fun `no email template carries the retired red`() = templates().map { (name, html) ->
        DynamicTest.dynamicTest(name) {
            assertFalse(
                html.contains(RETIRED, ignoreCase = true),
                "$name still uses the retired brand red — swap it for $BRAND",
            )
        }
    }

    @TestFactory
    fun `every email template carries the brand accent`() = templates().map { (name, html) ->
        DynamicTest.dynamicTest(name) {
            assertTrue(
                html.contains(BRAND, ignoreCase = true),
                "$name has no $BRAND anywhere — an unbranded email is a phishing-looking email",
            )
        }
    }

    /**
     * Thymeleaf passes plain `<!-- -->` comments through into the sent message,
     * so a note left for the next developer is delivered to the recipient. That
     * actually happened in request-status.html. Parser-level `<!--/* */-->`
     * comments are stripped instead.
     *
     * Only flags comments that read like notes-to-self; the short structural
     * markers (`<!-- Footer -->`, `<!-- Brand bar -->`) are harmless and
     * genuinely useful when editing these by hand.
     */
    @TestFactory
    fun `no template leaks a developer note to recipients`() = templates().map { (name, html) ->
        DynamicTest.dynamicTest(name) {
            val leaked = Regex("<!--(?!/\\*)(.*?)-->", RegexOption.DOT_MATCHES_ALL)
                .findAll(html)
                .map { it.groupValues[1].trim() }
                .filter { it.length > 60 || it.contains('\n') }
                .toList()

            assertTrue(
                leaked.isEmpty(),
                "$name would ship these comments to recipients — use <!--/* */--> instead: $leaked",
            )
        }
    }
}
