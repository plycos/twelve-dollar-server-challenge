package com.lycosp.post

import com.lycosp.post.dto.FeedDTO
import com.lycosp.post.dto.LikeDTO
import com.lycosp.post.dto.PostDTO
import com.lycosp.utils.toPositiveIntOrNull
import io.quarkus.security.Authenticated
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.jboss.resteasy.reactive.RestResponse
import org.jboss.resteasy.reactive.RestResponse.Status.CREATED
import org.jboss.resteasy.reactive.RestResponse.Status.OK

@ApplicationScoped
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
class PostController(
    private val service: PostService
) {

    @GET
    @Path("/feed")
    fun feed(): FeedDTO = service.feed()

    @GET
    @Path("/posts/{id}")
    fun get(
        @PathParam("id") rawPostId: String
    ): PostDTO {
        val postId = rawPostId.toPositiveIntOrNull() ?: throw BadRequestException("invalid post id")
        return service.get(postId)
    }

    @POST
    @Path("/posts")
    @Authenticated
    fun create(rawBody: String): RestResponse<PostDTO> {
        val element = try {
            Json.parseToJsonElement(rawBody)
        } catch (e: SerializationException) {
            throw BadRequestException("malformed JSON body", e)
        }

        val parsed = try {
            Json.decodeFromJsonElement<CreatePostDTO>(element)
        } catch (e: SerializationException) {
            throw BadRequestException("body is required", e)
        }

        return RestResponse.status(CREATED, service.create(parsed.body))
    }

    @POST
    @Path("/posts/{id}/like")
    @Authenticated
    fun like(
        @PathParam("id") rawPostId: String
    ): RestResponse<LikeDTO> {
        val postId = rawPostId.toPositiveIntOrNull() ?: throw BadRequestException("invalid post id")
        val liked = service.like(postId)
        val alreadyLiked = liked.alreadyLiked

        return RestResponse
            .status(
                if (alreadyLiked) OK else CREATED,
                liked
            )
    }

    @Serializable
    private data class CreatePostDTO(
        val body: String
    )
}
