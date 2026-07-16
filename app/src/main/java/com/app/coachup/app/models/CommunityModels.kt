package com.app.coachup.app.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommunitySettings(
    @SerialName("id") val id: String? = null,
    @SerialName("scope") val scope: String,
    @SerialName("gym_id") val gymId: String? = null,
    @SerialName("general_enabled") val generalEnabled: Boolean = true,
    @SerialName("gym_feed_enabled") val gymFeedEnabled: Boolean = true
)

@Serializable
data class CommunityGroup(
    @SerialName("id") val id: String,
    @SerialName("scope") val scope: String,
    @SerialName("gym_id") val gymId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class CommunityGroupMember(
    @SerialName("id") val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("role") val role: String = "member",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class CommunityPost(
    @SerialName("id") val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("scope") val scope: String,
    @SerialName("gym_id") val gymId: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = ""
)

@Serializable
data class CommunityPostInsert(
    @SerialName("author_id") val authorId: String,
    @SerialName("scope") val scope: String,
    @SerialName("gym_id") val gymId: String? = null,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class CommunityGroupInsert(
    @SerialName("scope") val scope: String,
    @SerialName("gym_id") val gymId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("is_active") val isActive: Boolean = true
)

@Serializable
data class CommunityGroupMemberInsert(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("role") val role: String = "member"
)

@Serializable
data class CommunityLike(
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String
)

/** UI model with author display + like state */
data class CommunityPostUi(
    val post: CommunityPost,
    val authorName: String,
    val likeCount: Int,
    val likedByMe: Boolean
)
