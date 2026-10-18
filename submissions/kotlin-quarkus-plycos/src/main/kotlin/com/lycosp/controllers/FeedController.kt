package com.lycosp.controllers

import com.lycosp.Feed
import com.lycosp.LikeDTO
import com.lycosp.Post
import com.lycosp.PostDTO
import com.lycosp.controllers.utils.ResponseFactory
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
import java.sql.ResultSet
import java.sql.SQLException
import javax.sql.DataSource

@ApplicationScoped
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
class FeedController(
    private val dataSource: DataSource,
    private val identity: SecurityIdentity
) {
    private companion object {
        const val SQL_POST_BASE =
            "SELECT p.id, p.body, p.created_at, u.username, (SELECT COUNT(*) FROM likes l WHERE l.post_id = p.id) AS like_count FROM posts p JOIN users u ON u.id = p.user_id"
        const val SQL_FEED = "$SQL_POST_BASE ORDER BY p.created_at DESC, p.id DESC LIMIT 20"
        const val SQL_GET_POST = "$SQL_POST_BASE WHERE p.id = ?"
        const val SQL_CREATE_POST = "INSERT INTO posts (user_id, body) VALUES (?, ?) RETURNING id, body, created_at"
        const val SQL_LIKE_POST =
            "INSERT INTO likes (user_id, post_id) VALUES (?, ?) ON CONFLICT (user_id, post_id) DO NOTHING"

        @JvmStatic
        private fun formatTimestamp(ts: String): String {
            val dotIndex = ts.indexOf('.')
            val zIndex = ts.lastIndexOf('Z')
            if (dotIndex in 1..<zIndex) {
                val decimalPart = ts.substring(dotIndex + 1, zIndex)
                if (decimalPart.length > 3) {
                    return ts.substring(0, dotIndex + 1) + decimalPart.substring(0, 3) + "Z"
                }
            }
            return ts
        }

        @JvmStatic
        private fun postFromResultSet(rs: ResultSet): Post {
            return Post(
                id = rs.getInt(1),
                body = rs.getString(2),
                createdAt = formatTimestamp(rs.getString(3)),
                author = rs.getString(4),
                likeCount = rs.getInt(5)
            )
        }
    }

    @GET
    @Path("/feed")
    fun feed(): Response {
        dataSource.connection.use { conn ->
            conn.prepareStatement(SQL_FEED).executeQuery().use { rs ->
                val posts = ArrayList<Post>()
                while (rs.next()) posts.add(postFromResultSet(rs))
                return ResponseFactory.ok(
                    Feed(
                        posts = posts
                    )
                )
            }
        }
    }

    @GET
    @Path("/posts/{id}")
    fun getPost(
        @PathParam("id") postId: String
    ): Response {
        val id = postId.toIntOrNull()
        if (id == null || id < 1) {
            return ResponseFactory.badRequest("invalid post id")
        }
        dataSource.connection.use { conn ->
            conn.prepareStatement(SQL_GET_POST).use { stmt ->
                stmt.setInt(1, id)
                stmt.executeQuery().use { rs ->
                    return if (rs.next()) ResponseFactory.ok(PostDTO(postFromResultSet(rs)))
                    else ResponseFactory.notFound("post not found")
                }
            }
        }
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
        val bodyText = bodyPrimitive.content
        val trimmed = bodyText.trim()
        if (trimmed.isEmpty()) {
            return ResponseFactory.badRequest("body is required")
        }
        if (trimmed.length > 500) {
            return ResponseFactory.badRequest("body must be at most 500 characters")
        }
        val userId = identity.getAttribute<Int>("userId")
        val username = identity.principal.name
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(SQL_CREATE_POST).use { stmt ->
                    stmt.setInt(1, userId)
                    stmt.setString(2, trimmed)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        return ResponseFactory.created(
                            PostDTO(
                                Post(
                                    id = rs.getInt(1),
                                    body = rs.getString(2),
                                    author = username,
                                    createdAt = formatTimestamp(rs.getString(3)),
                                    likeCount = 0
                                )
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            return ResponseFactory.internalServerError()
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
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(SQL_LIKE_POST).use { stmt ->
                    stmt.setInt(1, userId)
                    stmt.setInt(2, id)
                    val alreadyLiked = stmt.executeUpdate() == 0
                    val likeDTO = LikeDTO(
                        liked = true,
                        alreadyLiked = alreadyLiked,
                        postId = id
                    )
                    return if (alreadyLiked) ResponseFactory.ok(likeDTO)
                    else ResponseFactory.created(likeDTO)
                }
            }
        } catch (_: SQLException) {
            return ResponseFactory.notFound("post not found")
        }
    }
}
