package com.lycosp.security

import io.quarkus.security.AuthenticationFailedException
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.AuthenticationRequest
import io.quarkus.security.runtime.QuarkusPrincipal
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val POSITIVE_INT = Regex("^[1-9][0-9]*$")

private const val AUTH_ERROR = "auth_error"
private const val MISSING_TOKEN = "missing bearer token"
private const val INVALID_TOKEN = "invalid or expired token"
private const val INVALID_PAYLOAD = "invalid token payload"

@Alternative
@Priority(1)
@ApplicationScoped
class CustomAuthMechanism(
    @ConfigProperty(name = "JWT_SECRET", defaultValue = "twelve-dollar-challenge")
    jwtSecret: String
) : HttpAuthenticationMechanism {

    private class Claims(val userId: Int, val username: String)

    private class InvalidToken(val error: String) : Exception(error, null, false, false)

    private val decoder = Base64.getUrlDecoder()

    private val macTemplate: Mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(jwtSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
    }

    /** One initialized Mac per thread; the signature is still computed for every request (rule 5). */
    private val mac = ThreadLocal.withInitial { macTemplate.clone() as Mac }

    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager
    ): Uni<SecurityIdentity> {
        val authHeader = context.request().getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            context.put(AUTH_ERROR, MISSING_TOKEN)
            return Uni.createFrom().nullItem()
        }

        val claims = try {
            verify(authHeader.substring(7))
        } catch (e: InvalidToken) {
            context.put(AUTH_ERROR, e.error)
            return Uni.createFrom().failure(AuthenticationFailedException(e.error))
        }

        val identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(QuarkusPrincipal(claims.username))
            .addAttribute("userId", claims.userId)
            .addRole("user")
            .build()
        return Uni.createFrom().item(identity)
    }

    private fun verify(token: String): Claims {
        val dot1 = token.indexOf('.')
        val dot2 = if (dot1 < 0) -1 else token.indexOf('.', dot1 + 1)
        if (dot2 < 0 || token.indexOf('.', dot2 + 1) >= 0) throw InvalidToken(INVALID_TOKEN)

        // Signature first: forged tokens are rejected before any JSON parsing.
        val signature = decode(token.substring(dot2 + 1))
        val expected = mac.get().doFinal(token.substring(0, dot2).toByteArray(StandardCharsets.US_ASCII))
        if (!MessageDigest.isEqual(expected, signature)) throw InvalidToken(INVALID_TOKEN)

        val header = parseObject(decode(token.substring(0, dot1)))
        if ((header["alg"] as? JsonPrimitive)?.content != "HS256") throw InvalidToken(INVALID_TOKEN)

        val payload = parseObject(decode(token.substring(dot1 + 1, dot2)))
        val exp = (payload["exp"] as? JsonPrimitive)?.longOrNull
        if (exp != null && Instant.now().epochSecond >= exp) throw InvalidToken(INVALID_TOKEN)

        val userId = (payload["sub"] as? JsonPrimitive)
            ?.takeIf { it.isString && POSITIVE_INT.matches(it.content) }
            ?.content?.toIntOrNull()
        val username = (payload["username"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (userId == null || username == null) throw InvalidToken(INVALID_PAYLOAD)

        return Claims(userId, username)
    }

    private fun decode(part: String): ByteArray = try {
        decoder.decode(part)
    } catch (_: IllegalArgumentException) {
        throw InvalidToken(INVALID_TOKEN)
    }

    private fun parseObject(bytes: ByteArray): JsonObject = try {
        Json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: throw InvalidToken(INVALID_TOKEN)

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(ChallengeData(401, null, null))

    override fun sendChallenge(context: RoutingContext): Uni<Boolean> {
        // With proactive auth off, authenticate() may not have run for this request.
        val errorMsg = context.get<String>(AUTH_ERROR) ?: run {
            val authHeader = context.request().getHeader("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) MISSING_TOKEN else INVALID_TOKEN
        }
        context.response()
            .setStatusCode(401)
            .putHeader("Content-Type", "application/json")
            .end("""{"error":"$errorMsg"}""")
        return Uni.createFrom().item(true)
    }

    override fun getCredentialTypes(): Set<Class<out AuthenticationRequest>> = emptySet()

    override fun getCredentialTransport(context: RoutingContext): Uni<HttpCredentialTransport> =
        Uni.createFrom().nullItem()

    override fun getPriority(): Int = 1000
}
