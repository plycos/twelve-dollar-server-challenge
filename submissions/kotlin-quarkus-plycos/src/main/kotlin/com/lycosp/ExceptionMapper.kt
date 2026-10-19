package com.lycosp

import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.InternalServerErrorException
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.NotFoundException
import kotlinx.serialization.Serializable
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.Status.*
import org.jboss.resteasy.reactive.server.ServerExceptionMapper

@Serializable
data class ErrorDTO(
    val error: String
)

class ExceptionMapper {

    private typealias ErrorResponse = RestResponse<ErrorDTO>

    @ServerExceptionMapper
    fun badRequest(e: BadRequestException): ErrorResponse =
        RestResponse.status(BAD_REQUEST, ErrorDTO(e.message ?: "bad request"))

    @ServerExceptionMapper
    fun unauthorized(e: NotAuthorizedException): ErrorResponse =
        RestResponse.status(UNAUTHORIZED, ErrorDTO(e.message ?: "unauthorized"))

    @ServerExceptionMapper
    fun notFound(e: NotFoundException): ErrorResponse =
        RestResponse.status(NOT_FOUND, ErrorDTO("not found"))

    @ServerExceptionMapper
    fun postNotFound(e: PostNotFound): ErrorResponse =
        RestResponse.status(NOT_FOUND, ErrorDTO(e.message))

    @ServerExceptionMapper
    fun internalServerError(e: InternalServerErrorException): ErrorResponse =
        RestResponse.status(INTERNAL_SERVER_ERROR, ErrorDTO("internal server error"))

    @ServerExceptionMapper
    fun exception(e: Exception): ErrorResponse =
        RestResponse.status(INTERNAL_SERVER_ERROR, ErrorDTO("internal server error"))
}
