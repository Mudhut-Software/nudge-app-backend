package com.mudhut.nudge.users.services

import com.mudhut.nudge.users.models.SessionResponse
import com.mudhut.nudge.users.repositories.RefreshTokenRepository
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.utils.exceptions.SessionNotFoundException
import com.mudhut.nudge.utils.exceptions.UserNotFoundException
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class SessionService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenService: RefreshTokenService,
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val logoutService: LogoutService,
) {

    fun listFor(email: String, authorizationHeader: String): List<SessionResponse> {
        val user = requireUser(email)
        val currentSession = jwtService.extractSessionId(bearer(authorizationHeader))

        return refreshTokenRepository
            .findAllByUserIdOrderByLastSeenAtDesc(user.id!!)
            .map {
                SessionResponse(
                    sessionId = it.sessionId ?: "",
                    // Rows predating the column, and rows whose user agent was
                    // unrecognised, both read as unknown rather than blank.
                    deviceLabel = it.deviceLabel ?: DeviceLabel.UNKNOWN,
                    lastSeenAt = it.lastSeenAt,
                    current = it.sessionId != null && it.sessionId == currentSession,
                )
            }
    }

    /**
     * End one session.
     *
     * Ownership is the control, not the unguessability of a UUID: without this
     * check any authenticated user could sign out any other user.
     */
    @Transactional
    fun revoke(email: String, sessionId: String) {
        val user = requireUser(email)
        val row = refreshTokenRepository.findBySessionId(sessionId)
            .orElseThrow { SessionNotFoundException("Session not found") }

        if (row.user?.id != user.id) {
            // Same exception as "does not exist", so a caller cannot use the
            // response to discover whether someone else's session id is real.
            throw SessionNotFoundException("Session not found")
        }
        refreshTokenService.deleteBySessionId(sessionId)
    }

    /**
     * End every session, including the caller's own.
     *
     * "Log out everywhere" is unambiguous; "everywhere except here" invites a
     * second guess about what happened, and someone who believes they are
     * compromised wants their own session gone too.
     */
    @Transactional
    fun revokeAll(email: String, authorizationHeader: String) {
        val user = requireUser(email)
        // Blocklists the caller's current access token, which deleteByUserId
        // cannot do on its own.
        logoutService.logout(email, authorizationHeader)
        refreshTokenService.deleteByUserId(user.id!!)
    }

    private fun requireUser(email: String) = userRepository.findByEmail(email)
        .orElseThrow { UserNotFoundException("User not found") }

    private fun bearer(header: String) = header.removePrefix("Bearer ").trim()
}
