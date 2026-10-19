package com.lycosp.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.MissingClaimException
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.runtime.QuarkusPrincipal
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.ext.web.RoutingContext
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Alternative
import jakarta.ws.rs.NotAuthorizedException
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.resteasy.reactive.RestResponse.StatusCode.UNAUTHORIZED
import com.lycosp.utils.toPositiveIntOrNull

@Alternative
@Priority(1)
@ApplicationScoped
class JwtAuthMechanism(
    @ConfigProperty(name = "jwt-secret") secret: String
) : HttpAuthenticationMechanism {

    private val alg = Algorithm.HMAC256(secret)
    private val verifier =
        JWT.require(alg)
            .withClaimPresence("username")
            .build()

    private fun missingToken(e: Exception? = null) = NotAuthorizedException("missing bearer token", e)
    private fun badToken(e: Exception? = null) = NotAuthorizedException("invalid or expired token", e)
    private fun invalidToken(e: Exception? = null) = NotAuthorizedException("invalid token payload", e)

    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager
    ): Uni<SecurityIdentity> {
        val authorization = context.request().getHeader("Authorization")
        if (authorization == null || !authorization.startsWith("Bearer "))
            return Uni.createFrom().failure(missingToken())

        val token = authorization.substring(7)
        val decodedJwt = try {
            verifier.verify(token)
        } catch (e: MissingClaimException) {
            return Uni.createFrom().failure(invalidToken(e))
        } catch (e: Exception) {
            return Uni.createFrom().failure(badToken(e))
        }

        val userId = decodedJwt.subject.toPositiveIntOrNull() ?: return Uni.createFrom().failure(invalidToken())
        val username = decodedJwt.getClaim("username").asString() ?: return Uni.createFrom().failure(invalidToken())

        val identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(QuarkusPrincipal(username))
            .addAttribute("userId", userId)
            .build()

        return Uni.createFrom().item(identity)
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(ChallengeData(UNAUTHORIZED))
}
