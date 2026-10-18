package com.lycosp.controllers.utils

import com.lycosp.ErrorDTO
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.ResponseBuilder

class ResponseFactory {
    companion object {

        fun ok(entity: Any? = null): Response =
            Response.ok().entity(entity).build()

        fun created(entity: Any? = null): Response =
            Response.status(Response.Status.CREATED).entity(entity).build()

        private fun errorResponse(message: String): ResponseBuilder =
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(
                    ErrorDTO(
                        error = message
                    )
                )

        fun badRequest(message: String): Response =
            errorResponse(message)
                .status(Response.Status.BAD_REQUEST)
                .build()

        fun unauthorized(message: String): Response =
            errorResponse(message)
                .status(Response.Status.UNAUTHORIZED)
                .build()

        fun notFound(message: String): Response =
            errorResponse(message)
                .status(Response.Status.NOT_FOUND)
                .build()

        fun internalServerError(message: String = "internal server error"): Response =
            errorResponse(message).build()

        fun internalServerError(entity: Any): Response =
            Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(entity)
                .build()
    }
}
