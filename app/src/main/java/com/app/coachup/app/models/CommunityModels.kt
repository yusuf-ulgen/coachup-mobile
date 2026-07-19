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

@Serializable
data class CommunityComment(
    @SerialName("id")          val id: String,
    @SerialName("post_id")     val postId: String,
    @SerialName("author_id")   val authorId: String,
    @SerialName("content")     val content: String,
    @SerialName("created_at")  val createdAt: String
)

@Serializable
data class CommunityCommentInsert(
    @SerialName("post_id")     val postId: String,
    @SerialName("author_id")   val authorId: String,
    @SerialName("content")     val content: String
)

data class CommunityCommentUi(
    val comment: CommunityComment,
    val authorName: String,
    val authorAvatarUrl: String? = null
)

@Serializable
data class CommunityPoll(
    @SerialName("id")          val id: String,
    @SerialName("post_id")     val postId: String,
    @SerialName("question")     val question: String,
    @SerialName("created_at")  val createdAt: String
)

@Serializable
data class CommunityPollOption(
    @SerialName("id")          val id: String,
    @SerialName("poll_id")     val pollId: String,
    @SerialName("option_text") val optionText: String,
    @SerialName("created_at")  val createdAt: String
)

@Serializable
data class CommunityPollVote(
    @SerialName("poll_id")     val pollId: String,
    @SerialName("option_id")   val optionId: String,
    @SerialName("user_id")     val userId: String,
    @SerialName("created_at")  val createdAt: String = ""
)

@Serializable
data class CommunityPollVoteInsert(
    @SerialName("poll_id")     val pollId: String,
    @SerialName("option_id")   val optionId: String,
    @SerialName("user_id")     val userId: String
)

data class CommunityPollUi(
    val poll: CommunityPoll,
    val options: List<CommunityPollOptionUi>,
    val myVoteOptionId: String?,
    val totalVotes: Int
)

data class CommunityPollOptionUi(
    val option: CommunityPollOption,
    val voteCount: Int,
    val percentage: Int
)

/** UI model with author display + like state + comments + poll */
data class CommunityPostUi(
    val post: CommunityPost,
    val authorName: String,
    val authorAvatarUrl: String? = null,
    val likeCount: Int,
    val likedByMe: Boolean,
    val commentCount: Int = 0,
    val poll: CommunityPollUi? = null
)
