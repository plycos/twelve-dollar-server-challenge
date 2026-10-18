package com.lycosp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthCheck(
    val status: String,
    val db: String,
    @SerialName("uptime_s") val uptimeS: Long? = null,
    val error: String? = null
)

@Serializable
data class Feed(
    val posts: List<Post> = emptyList()
)

@Serializable
data class Post(
    val id: Int,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    val author: String,
    @SerialName("like_count") val likeCount: Int
)

@Serializable
data class PostDTO(
    val post: Post
)

@Serializable
data class ErrorDTO(
    val error: String
)

@Serializable
data class PostBody(
    val body: String
)

@Serializable
data class LikeDTO(
    val liked: Boolean,
    @SerialName("already_liked") val alreadyLiked: Boolean,
    @SerialName("post_id") val postId: Int
)
