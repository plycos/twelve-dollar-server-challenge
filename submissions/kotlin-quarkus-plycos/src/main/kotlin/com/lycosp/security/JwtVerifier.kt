package com.lycosp.security

import com.lycosp.exceptions.TokenException
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jose4j.jwa.AlgorithmConstraints
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jwt.consumer.InvalidJwtException
import org.jose4j.jwt.consumer.JwtConsumerBuilder
import org.jose4j.keys.HmacKey

data class AuthUser(
    val id: Int,
    val username: String
)

private val POSITIVE_INT = Regex("[1-9][0-9]*")

@ApplicationScoped
class JwtVerifier(
    @ConfigProperty(name = "jwt-secret") private var secret: String
) {

    private val consumer = JwtConsumerBuilder()
        .setVerificationKey(HmacKey(secret.toByteArray(Charsets.UTF_8)))
        .setJwsAlgorithmConstraints(
            AlgorithmConstraints.ConstraintType.PERMIT,
            AlgorithmIdentifiers.HMAC_SHA256
        )
        .setRequireExpirationTime()
        .setRequireSubject()
        .setAllowedClockSkewInSeconds(0)
        .build()

    fun verify(token: String): AuthUser {
        val claims = try {
            consumer.processToClaims(token)
        } catch (_: InvalidJwtException) {
            throw TokenException("invalid or expired jwt")
        }
        val sub = claims.getClaimValue("sub") as? String
        val username = claims.getClaimValue("username") as? String
        val id = sub?.takeIf { POSITIVE_INT.matches(it) }?.toIntOrNull()

        if (id == null || username == null) throw TokenException("invalid token payload")
        return AuthUser(id, username)
    }
}
