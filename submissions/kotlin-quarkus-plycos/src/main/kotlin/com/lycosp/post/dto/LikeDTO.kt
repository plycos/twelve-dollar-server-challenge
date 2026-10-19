package com.lycosp.post.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LikeDTO(
    val liked: Boolean,
    @SerialName("already_liked") val alreadyLiked: Boolean,
    @SerialName("post_id") val postId: Int
)
