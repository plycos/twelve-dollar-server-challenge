package com.lycosp.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.MissingClaimException
import com.lycosp.utils.toPositiveIntOrNull
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

    private fun missingToken(e: Exception? = null) = NotAuthorizedException("missing bearer token", e, "Bearer")
    private fun badToken(e: Exception? = null) = NotAuthorizedException("invalid or expired token", e, "Bearer")
    private fun invalidToken(e: Exception? = null) = NotAuthorizedException("invalid token payload", e, "Bearer")

    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager
    ): Uni<SecurityIdentity> {
        val authorization = context.request().getHeader("Authorization")
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw missingToken()

        val token = authorization.substring(7)
        val decodedJwt = try {
            verifier.verify(token)
        } catch (e: MissingClaimException) {
            throw invalidToken(e)
        } catch (e: Exception) {
            throw badToken(e)
        }

        val userId = decodedJwt.subject.toPositiveIntOrNull() ?: throw invalidToken()
        val username = decodedJwt.getClaim("username").asString() ?: throw invalidToken()

        val identity = QuarkusSecurityIdentity.builder()
            .setPrincipal(QuarkusPrincipal(username))
            .addAttribute("userId", userId)
            .build()

        return Uni.createFrom().item(identity)
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(ChallengeData(UNAUTHORIZED, "WWW-Authenticate", "Bearer"))
}
