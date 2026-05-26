package com.mudhut.nudge.users.services

import com.mudhut.nudge.email.IEmailService
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.repositories.PhoneVerificationTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.repositories.VerificationTokenRepository
import com.mudhut.nudge.utils.UrlService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.thymeleaf.TemplateEngine
import org.thymeleaf.spring6.SpringTemplateEngine
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver

class VerificationServiceTest {

    private val verificationTokenRepository: VerificationTokenRepository = mock()
    private val phoneVerificationTokenRepository: PhoneVerificationTokenRepository = mock()
    private val userRepository: UserRepository = mock()
    private val emailService: IEmailService = mock()
    private val urlService: UrlService = mock()

    // Real Thymeleaf engine pointing at the same templates Spring will resolve in prod.
    // Uses SpringTemplateEngine so the Spring dialect (SpEL) is wired in — the base
    // TemplateEngine would fall back to OGNL, which isn't on the test classpath.
    private val templateEngine: TemplateEngine = SpringTemplateEngine().apply {
        setTemplateResolver(ClassLoaderTemplateResolver().apply {
            prefix = "templates/"
            suffix = ".html"
            characterEncoding = "UTF-8"
            isCacheable = false
        })
    }

    private val sut = VerificationService(
        verificationTokenRepository,
        phoneVerificationTokenRepository,
        userRepository,
        emailService,
        urlService,
        templateEngine,
    )

    @Test
    fun `sendVerificationEmail renders the branded template and dispatches HTML + plain text`() {
        val user = User(id = 1L, email = "alice@example.com", username = "alice")
        val token = "raw-token-uuid"
        whenever(urlService.buildUrlWithParam(eq("/verify-email"), eq("token"), eq(token)))
            .thenReturn("https://nudge.example.com/verify-email?token=raw-token-uuid")

        sut.sendVerificationEmail(user, token)

        val subjectCaptor = argumentCaptor<String>()
        val htmlCaptor = argumentCaptor<String>()
        val textCaptor = argumentCaptor<String>()
        verify(emailService).sendHtmlEmail(
            eq("alice@example.com"),
            subjectCaptor.capture(),
            htmlCaptor.capture(),
            textCaptor.capture(),
        )

        // Subject reads like a CTA, not the old generic line.
        assertEquals("Confirm your email to get started", subjectCaptor.firstValue)

        // HTML body carries the brand + the rendered values from the Context.
        val html = htmlCaptor.firstValue
        assertTrue(html.contains("Nudge"), "html should brand 'Nudge'")
        assertTrue(html.contains("#E42313"), "html should use brand red")
        assertTrue(html.contains("alice"), "html should greet the user by username")
        assertTrue(
            html.contains("https://nudge.example.com/verify-email?token=raw-token-uuid"),
            "html should embed the verification URL",
        )

        // Plain-text fallback is non-empty and includes the same URL + a friendly greeting.
        val text = textCaptor.firstValue
        assertNotNull(text)
        assertTrue(text.contains("alice"))
        assertTrue(text.contains("https://nudge.example.com/verify-email?token=raw-token-uuid"))
    }

    @Test
    fun `sendVerificationEmail falls back to 'there' when the user has no username`() {
        val user = User(id = 2L, email = "anon@example.com", username = null)
        whenever(urlService.buildUrlWithParam(eq("/verify-email"), eq("token"), eq("t")))
            .thenReturn("https://nudge.example.com/verify-email?token=t")

        sut.sendVerificationEmail(user, "t")

        val htmlCaptor = argumentCaptor<String>()
        val textCaptor = argumentCaptor<String>()
        verify(emailService).sendHtmlEmail(
            eq("anon@example.com"),
            eq("Confirm your email to get started"),
            htmlCaptor.capture(),
            textCaptor.capture(),
        )
        assertTrue(htmlCaptor.firstValue.contains("Hi <span>there</span>"))
        assertTrue(textCaptor.firstValue.contains("Hi there,"))
    }
}
