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

@Alternative
@Priority(1)
@ApplicationScoped
class CustomAuthMechanism(
    @ConfigProperty(name = "JWT_SECRET", defaultValue = "twelve-dollar-challenge")
    jwtSecret: String
) : HttpAuthenticationMechanism {

    private val secretKeySpec =
        SecretKeySpec(
            jwtSecret.toByteArray(StandardCharsets.UTF_8),
            "HmacSHA256"
        )

    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager
    ): Uni<SecurityIdentity> {
        val authHeader = context.request().getHeader("Authorization") ?: return Uni.createFrom().nullItem()

        if (!authHeader.startsWith("Bearer ")) {
            context.put("auth_error", "missing bearer token")
            return Uni.createFrom().nullItem()
        }

        val token = authHeader.substring(7)
        val parts = token.split('.')
        if (parts.size != 3) {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        val decoder = Base64.getUrlDecoder()
        val headerBytes = try {
            decoder.decode(parts[0])
        } catch (_: Exception) {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        val payloadBytes = try {
            decoder.decode(parts[1])
        } catch (_: Exception) {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        val signatureBytes = try {
            decoder.decode(parts[2])
        } catch (_: Exception) {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        val headerJson = try {
            Json.parseToJsonElement(headerBytes.toString(StandardCharsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        }

        val alg = (headerJson?.get("alg") as? JsonPrimitive)?.content
        if (alg != "HS256") {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        val dataToSign = (parts[0] + "." + parts[1]).toByteArray(StandardCharsets.US_ASCII)
        val expectedSignature = try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            mac.doFinal(dataToSign)
        } catch (_: Exception) {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        if (!MessageDigest.isEqual(expectedSignature, signatureBytes)) {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        val payloadJson = try {
            Json.parseToJsonElement(payloadBytes.toString(StandardCharsets.UTF_8)) as? JsonObject
        } catch (_: Exception) {
            null
        }

        if (payloadJson == null) {
            context.put("auth_error", "invalid or expired token")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
        }

        val exp = (payloadJson["exp"] as? JsonPrimitive)?.longOrNull
        if (exp != null) {
            val now = Instant.now().epochSecond
            if (now >= exp) {
                context.put("auth_error", "invalid or expired token")
                return Uni.createFrom().failure(AuthenticationFailedException("invalid or expired token"))
            }
        }

        val subPrimitive = payloadJson["sub"] as? JsonPrimitive
        val usernamePrimitive = payloadJson["username"] as? JsonPrimitive

        val subStr = if (subPrimitive != null && subPrimitive.isString) subPrimitive.content else null
        val usernameStr =
            if (usernamePrimitive != null && usernamePrimitive.isString) usernamePrimitive.content else null

        val userId = if (subStr != null && POSITIVE_INT.matches(subStr)) subStr.toIntOrNull() else null

        if (userId == null || usernameStr == null) {
            context.put("auth_error", "invalid token payload")
            return Uni.createFrom().failure(AuthenticationFailedException("invalid token payload"))
        }

        val identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(QuarkusPrincipal(usernameStr))
            .addAttribute("userId", userId)
            .addRole("user")
            .build()

        return Uni.createFrom().item(identity)
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> {
        return Uni.createFrom().item(ChallengeData(401, "Content-Type", "application/json"))
    }

    override fun sendChallenge(context: RoutingContext): Uni<Boolean> {
        val errorMsg = context.get<String>("auth_error") ?: run {
            val authHeader = context.request().getHeader("Authorization")
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                "missing bearer token"
            } else {
                "invalid or expired token"
            }
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
