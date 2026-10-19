package com.lycosp.post.dto

import com.lycosp.post.dao.PostDAO
import kotlinx.serialization.Serializable

@Serializable
data class FeedDTO(
    val posts: List<PostDAO>
)
