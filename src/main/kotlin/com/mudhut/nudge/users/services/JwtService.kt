package com.mudhut.nudge.users.services

import com.mudhut.nudge.config.EnvConfig
import com.mudhut.nudge.users.entities.User
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SecurityException
import org.slf4j.LoggerFactory
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.Key
import java.util.*

@Service
class JwtService(
    private val envConfig: EnvConfig
) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    fun generateToken(user: User): String {
        val claims = mutableMapOf<String, Any>(
            "id" to user.id!!,
            "role" to user.role!!.name,
        )
        return createToken(claims, user.email!!, envConfig.accessTokenExpiryInMillis)
    }

    private fun createToken(claims: Map<String, Any>, subject: String, expirationMs: Int): String {
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(subject)
            .setId(UUID.randomUUID().toString())
            .setIssuedAt(Date(System.currentTimeMillis()))
            .setExpiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact()
    }

    private fun getSigningKey(): Key {
        val keyBytes = envConfig.jwtSecret!!.toByteArray(StandardCharsets.UTF_8)
        return Keys.hmacShaKeyFor(keyBytes)
    }

    fun extractUsername(token: String): String =
        extractClaim(token, Claims::getSubject)

    fun extractJti(token: String): String? = try {
        extractClaim(token, Claims::getId)
    } catch (e: Exception) {
        null
    }

    fun extractExpiration(token: String): Date =
        extractClaim(token, Claims::getExpiration)

    fun <T> extractClaim(token: String, claimsResolver: (Claims) -> T): T {
        val claims = extractAllClaims(token)
        return claimsResolver(claims)
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .body
    }

    private fun isTokenExpired(token: String): Boolean =
        extractExpiration(token).before(Date())

    fun validateToken(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return username == userDetails.username && !isTokenExpired(token)
    }

    fun validateToken(token: String): Boolean {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token)
            return true
        } catch (e: SecurityException) {
            logger.error("Invalid JWT signature: {}", e.message)
        } catch (e: MalformedJwtException) {
            logger.error("Invalid JWT token: {}", e.message)
        } catch (e: ExpiredJwtException) {
            logger.error("JWT token is expired: {}", e.message)
        } catch (e: UnsupportedJwtException) {
            logger.error("JWT token is unsupported: {}", e.message)
        } catch (e: IllegalArgumentException) {
            logger.error("JWT claims string is empty: {}", e.message)
        }
        return false
    }
}
