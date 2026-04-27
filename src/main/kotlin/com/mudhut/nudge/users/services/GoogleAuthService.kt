package com.mudhut.nudge.users.services

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.mudhut.nudge.businesses.repositories.BusinessMemberRepository
import com.mudhut.nudge.users.entities.User
import com.mudhut.nudge.users.entities.UserRole
import com.mudhut.nudge.users.models.AuthResponse
import com.mudhut.nudge.users.models.UserResponse
import com.mudhut.nudge.users.repositories.UserRepository
import com.mudhut.nudge.users.services.helpers.UsernameGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleAuthService(
    private val verifier: GoogleIdTokenVerifier,
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val refreshTokenService: RefreshTokenService,
    private val businessMemberRepository: BusinessMemberRepository,
    private val usernameGenerator: UsernameGenerator,
    @Value("\${nudge.google.client-id:}") private val clientId: String
) {

    @Transactional
    fun authenticate(idToken: String): AuthResponse {
        if (clientId.isBlank()) {
            throw IllegalStateException("Google sign-in is not configured")
        }

        val verified = try {
            verifier.verify(idToken)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Google ID token", e)
        } ?: throw IllegalArgumentException("Invalid Google ID token")

        val payload = verified.payload
        val googleId = payload.subject
            ?: throw IllegalArgumentException("Google ID token missing subject")
        val email = payload.email
            ?: throw IllegalArgumentException("Google ID token missing email")
        if (payload.emailVerified != true) {
            throw IllegalStateException("Google account email is not verified")
        }

        val user = userRepository.findByGoogleId(googleId).orElseGet {
            userRepository.findByEmail(email)
                .map { existing -> linkExistingUser(existing, googleId) }
                .orElseGet { createNewGoogleUser(googleId, email) }
        }

        val memberships = businessMemberRepository.findByUserIdAndIsActiveTrue(user.id!!)
        val accessToken = jwtService.generateToken(user)
        val refreshToken = refreshTokenService.createRefreshToken(user)

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .user(UserResponse.from(user, memberships))
            .build()
    }

    private fun linkExistingUser(user: User, googleId: String): User {
        user.googleId = googleId
        user.isEmailVerified = true
        user.isActive = true
        return userRepository.save(user)
    }

    private fun createNewGoogleUser(googleId: String, email: String): User {
        val user = User(
            email = email,
            username = usernameGenerator.fromEmail(email),
            password = null,
            phoneNumber = null,
            googleId = googleId,
            role = UserRole.BASIC_USER,
            isEmailVerified = true,
            isPhoneVerified = false,
            isActive = true
        )
        return userRepository.save(user)
    }
}
