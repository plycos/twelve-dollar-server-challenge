package com.lycosp.post

import com.lycosp.PostNotFound
import com.lycosp.post.dto.FeedDTO
import com.lycosp.post.dto.LikeDTO
import com.lycosp.post.dto.PostDTO
import io.quarkus.security.identity.SecurityIdentity
import jakarta.enterprise.context.ApplicationScoped
import jakarta.ws.rs.BadRequestException

@ApplicationScoped
class PostService(
    private val identity: SecurityIdentity,
    private val repository: PostRepository
) {

    fun feed(): FeedDTO = FeedDTO(repository.feed())

    fun get(postId: Int): PostDTO =
        PostDTO(repository.get(postId) ?: throw PostNotFound())

    fun create(body: String): PostDTO {
        val username = identity.principal.name
        val userId = identity.getAttribute<Int>("userId")
        val trimmed = body.trim()
        if (trimmed.isEmpty()) throw BadRequestException("body is required")
        if (trimmed.length > 500) throw BadRequestException("body must be at most 500 characters")
        return PostDTO(
            repository.create(
                userId = userId,
                username = username,
                body = trimmed
            )
        )
    }

    fun like(postId: Int): LikeDTO {
        val userId = identity.getAttribute<Int>("userId")
        val alreadyLiked = !repository.like(userId, postId)
        return LikeDTO(
            liked = true,
            alreadyLiked = alreadyLiked,
            postId = postId
        )
    }
}
