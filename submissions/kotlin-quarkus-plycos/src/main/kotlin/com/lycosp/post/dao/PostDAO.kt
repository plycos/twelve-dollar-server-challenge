package com.lycosp.post.dao

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostDAO(
    val id: Int,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    val author: String,
    @SerialName("like_count") val likes: Int
)
