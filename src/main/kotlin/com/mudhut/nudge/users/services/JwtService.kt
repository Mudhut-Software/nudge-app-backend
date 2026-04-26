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
import java.time.Instant
import java.util.Date
import java.util.UUID

@Service
class JwtService(
    private val envConfig: EnvConfig
) {
    private val logger = LoggerFactory.getLogger(JwtService::class.java)

    // ------- token issuance -------

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

    // ------- parse-once primitive -------

    /**
     * Parse and verify the token in one shot. Returns null on any parse/signature/expiry
     * failure (and logs the reason). Callers that need to inspect multiple claims should
     * call this once and pass the resulting Claims to the extractors below — avoids
     * re-parsing and re-verifying the HMAC for every claim read.
     */
    fun parseClaims(token: String): Claims? = try {
        Jwts.parserBuilder()
            .setSigningKey(getSigningKey())
            .build()
            .parseClaimsJws(token)
            .body
    } catch (e: SecurityException) {
        logger.error("Invalid JWT signature: {}", e.message); null
    } catch (e: MalformedJwtException) {
        logger.error("Invalid JWT token: {}", e.message); null
    } catch (e: ExpiredJwtException) {
        logger.error("JWT token is expired: {}", e.message); null
    } catch (e: UnsupportedJwtException) {
        logger.error("JWT token is unsupported: {}", e.message); null
    } catch (e: IllegalArgumentException) {
        logger.error("JWT claims string is empty: {}", e.message); null
    }

    // ------- Claims-taking accessors (no parsing) -------

    fun extractUsername(claims: Claims): String = claims.subject

    fun extractJti(claims: Claims): String? = claims.id

    fun extractExpiration(claims: Claims): Instant = claims.expiration.toInstant()

    // ------- token-taking convenience (delegate to parse + accessor) -------

    fun extractUsername(token: String): String = extractUsername(parseClaimsOrThrow(token))

    fun extractJti(token: String): String? = parseClaims(token)?.let(::extractJti)

    fun extractExpiration(token: String): Instant = extractExpiration(parseClaimsOrThrow(token))

    private fun parseClaimsOrThrow(token: String): Claims = parseClaims(token)
        ?: throw JwtException("Failed to parse JWT")

    // ------- legacy signature-only validator (kept for backward compatibility, currently unused) -------

    fun validateToken(token: String): Boolean = parseClaims(token) != null
}
