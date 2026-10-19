package com.lycosp.post.dto

import kotlinx.serialization.Serializable
import com.lycosp.post.dao.PostDAO

@Serializable
data class FeedDTO(
    val posts: List<PostDAO>
)
