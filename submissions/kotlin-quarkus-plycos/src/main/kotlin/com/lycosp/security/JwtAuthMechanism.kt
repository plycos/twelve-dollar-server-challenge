package com.lycosp.security

import io.quarkus.security.AuthenticationFailedException
import io.quarkus.security.identity.IdentityProviderManager
import io.quarkus.security.identity.SecurityIdentity
import io.quarkus.security.identity.request.AuthenticationRequest
import io.quarkus.security.runtime.QuarkusPrincipal
import io.quarkus.security.runtime.QuarkusSecurityIdentity
import io.quarkus.vertx.http.runtime.security.ChallengeData
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism
import io.smallrye.mutiny.Uni
import io.vertx.core.http.HttpHeaders
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class JwtAuthMechanism(
    private val verifier: JwtVerifier
) : HttpAuthenticationMechanism {
    override fun authenticate(
        context: RoutingContext,
        identityProviderManager: IdentityProviderManager
    ): Uni<SecurityIdentity> {
        val header = context.request().getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith("Bearer ")) {
            return Uni.createFrom().nullItem()
        }

        return try {
            val user = verifier.verify(header.removePrefix("Bearer ").trim())
            Uni.createFrom().item(
                QuarkusSecurityIdentity.builder()
                    .setPrincipal(QuarkusPrincipal(user.username))
                    .addAttribute("userId", user.id)
                    .build()
            )
        } catch (e: AuthenticationFailedException) {
            Uni.createFrom().failure(e)
        }
    }

    override fun getChallenge(context: RoutingContext): Uni<ChallengeData> =
        Uni.createFrom().item(ChallengeData(401, "WWW-Authenticate", "Bearer"))

    override fun getCredentialTypes(): Set<Class<out AuthenticationRequest>> = emptySet()
}