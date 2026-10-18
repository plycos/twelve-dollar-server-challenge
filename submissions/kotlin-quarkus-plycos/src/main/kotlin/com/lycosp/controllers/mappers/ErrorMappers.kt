package com.lycosp.controllers.mappers

import com.lycosp.ErrorDTO
import io.quarkus.security.AuthenticationCompletionException
import io.quarkus.security.AuthenticationFailedException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.NotAllowedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

class ErrorMappers {
    private fun error(status: Int, message: String): Response =
        Response.status(status).entity(ErrorDTO(message)).build()

    private fun extractAuthMessage(e: Throwable, default: String = "invalid or expired token"): String {
        val knownErrors = setOf("missing bearer token", "invalid or expired token", "invalid token payload")
        return generateSequence(e) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it in knownErrors }
            ?: (e.message?.takeIf { it in knownErrors } ?: default)
    }

    @ServerExceptionMapper
    fun unauthorized(e: UnauthorizedException): Response =
        error(401, "missing bearer token")

    @ServerExceptionMapper
    fun authFailed(e: AuthenticationFailedException): Response =
        error(401, extractAuthMessage(e, "invalid or expired token"))

    @ServerExceptionMapper
    fun authCompletion(e: AuthenticationCompletionException): Response =
        error(401, extractAuthMessage(e, "invalid or expired token"))

    @ServerExceptionMapper
    fun securityException(e: SecurityException): Response =
        error(401, extractAuthMessage(e, "invalid or expired token"))

    @ServerExceptionMapper
    fun notFound(e: NotFoundException): Response = error(404, "not found")

    @ServerExceptionMapper
    fun notAllowed(e: NotAllowedException): Response = error(404, "not found")

    @ServerExceptionMapper
    fun other(e: Throwable): Response = error(500, "internal server error")
}
