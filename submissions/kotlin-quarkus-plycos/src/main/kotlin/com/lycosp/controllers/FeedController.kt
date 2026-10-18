package com.lycosp.controllers

import com.lycosp.Feed
import com.lycosp.LikeDTO
import com.lycosp.Post
import com.lycosp.PostDTO
import com.lycosp.controllers.utils.ResponseFactory
import com.lycosp.db.PostRepository
import io.quarkus.security.Authenticated
import io.quarkus.security.identity.SecurityIdentity
import io.smallrye.common.annotation.RunOnVirtualThread
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException
import java.sql.SQLException

@ApplicationScoped
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
class FeedController(
    private val posts: PostRepository,
    private val identity: SecurityIdentity
) {
    @GET
    @Path("/feed")
    fun feed(): Response = ResponseFactory.ok(Feed(posts = posts.feed()))

    @GET
    @Path("/posts/{id}")
    fun getPost(
        @PathParam("id") postId: String
    ): Response {
        val id = postId.toIntOrNull()
        if (id == null || id < 1) {
            return ResponseFactory.badRequest("invalid post id")
        }
        val post = posts.getPost(id) ?: return ResponseFactory.notFound("post not found")
        return ResponseFactory.ok(PostDTO(post))
    }

    @POST
    @Path("/posts")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    fun createPost(
        raw: String
    ): Response {
        val jsonElement = try {
            Json.parseToJsonElement(raw)
        } catch (_: Exception) {
            return ResponseFactory.badRequest("malformed JSON body")
        }
        val jsonObject = jsonElement as? JsonObject
            ?: return ResponseFactory.badRequest("body is required")
        val bodyElement = jsonObject["body"]
            ?: return ResponseFactory.badRequest("body is required")
        val bodyPrimitive = bodyElement as? JsonPrimitive
            ?: return ResponseFactory.badRequest("body is required")
        if (!bodyPrimitive.isString) {
            return ResponseFactory.badRequest("body is required")
        }
        val trimmed = bodyPrimitive.content.trim()
        if (trimmed.isEmpty()) {
            return ResponseFactory.badRequest("body is required")
        }
        if (trimmed.length > 500) {
            return ResponseFactory.badRequest("body must be at most 500 characters")
        }
        val userId = identity.getAttribute<Int>("userId")
        val username = identity.principal.name
        return try {
            val created = posts.createPost(userId, trimmed)
            ResponseFactory.created(
                PostDTO(
                    Post(
                        id = created.id,
                        body = trimmed,
                        author = username,
                        createdAt = created.createdAt,
                        likeCount = 0
                    )
                )
            )
        } catch (_: Exception) {
            ResponseFactory.internalServerError()
        }
    }

    @POST
    @Path("/posts/{id}/like")
    @Authenticated
    fun likePost(
        @PathParam("id") postId: String
    ): Response {
        val id = postId.toIntOrNull()
        if (id == null || id < 1) {
            return ResponseFactory.badRequest("invalid post id")
        }
        val userId = identity.getAttribute<Int>("userId")
        return try {
            val alreadyLiked = posts.like(userId, id) == 0
            val likeDTO = LikeDTO(
                liked = true,
                alreadyLiked = alreadyLiked,
                postId = id
            )
            if (alreadyLiked) ResponseFactory.ok(likeDTO)
            else ResponseFactory.created(likeDTO)
        } catch (e: SQLException) {
            if (isForeignKeyViolation(e)) ResponseFactory.notFound("post not found")
            else ResponseFactory.internalServerError()
        } catch (_: Exception) {
            ResponseFactory.internalServerError()
        }
    }

    private fun isForeignKeyViolation(e: SQLException): Boolean {
        if (e is SQLiteException && e.resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY) return true
        return e.message?.contains("FOREIGN KEY constraint failed") == true
    }
}
