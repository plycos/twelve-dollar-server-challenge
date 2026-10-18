package com.lycosp.controllers.mappers

import com.lycosp.ErrorDTO
import com.lycosp.exceptions.TokenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.NotAllowedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.core.Response
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

class ErrorMappers {
    private fun error(status: Int, message: String): Response =
        Response.status(status).entity(ErrorDTO(message)).build()

    @ServerExceptionMapper
    fun unauthorized(e: UnauthorizedException) = error(401, "missing bearer token")

    @ServerExceptionMapper
    fun authFailed(e: SecurityException): Response {
        val tokenError = generateSequence<Throwable>(e) { it.cause }
            .filterIsInstance<TokenException>()
            .firstOrNull()?.error
        return error(401, tokenError ?: "invalid or expired token")
    }

    @ServerExceptionMapper
    fun notFound(e: NotFoundException) = error(404, "not found")

    @ServerExceptionMapper
    fun notAllowed(e: NotAllowedException) = error(404, "not found")

    @ServerExceptionMapper
    fun other(e: Throwable): Response {
        return error(500, "internal server error")
    }
}
