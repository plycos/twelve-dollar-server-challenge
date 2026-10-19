package com.lycosp

import jakarta.ws.rs.ClientErrorException
import org.jboss.resteasy.reactive.RestResponse.StatusCode.UNAUTHORIZED

data class PostNotFound(
    override val message: String = "post not found"
) : ClientErrorException(UNAUTHORIZED)
