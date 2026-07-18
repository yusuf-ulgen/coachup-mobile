package com.app.coachup.app.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.app.coachup.app.config.SupabaseConfig.client
import com.app.coachup.app.models.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Member community feed (Salon / Genel) + subgroups + admin toggles.
 *
 * Tables: community_settings, community_groups, community_group_members,
 * community_posts, community_likes. Bucket: community-images.
 */
object CommunityService {

    private const val STORAGE_BUCKET = "community-images"

    // -------------------------------------------------------------------------
    // Settings
    // -------------------------------------------------------------------------

    suspend fun fetchGlobalSettings(): CommunitySettings {
        return try {
            client.postgrest["community_settings"]
                .select {
                    filter { eq("scope", "global") }
                    limit(1)
                }
                .decodeList<CommunitySettings>()
                .firstOrNull()
                ?: CommunitySettings(scope = "global", generalEnabled = true, gymFeedEnabled = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CommunitySettings(scope = "global", generalEnabled = true, gymFeedEnabled = true)
        }
    }

    suspend fun fetchGymSettings(gymId: String): CommunitySettings {
        return try {
            client.postgrest["community_settings"]
                .select {
                    filter {
                        eq("scope", "gym")
                        eq("gym_id", gymId)
                    }
                    limit(1)
                }
                .decodeList<CommunitySettings>()
                .firstOrNull()
                ?: CommunitySettings(scope = "gym", gymId = gymId, generalEnabled = true, gymFeedEnabled = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CommunitySettings(scope = "gym", gymId = gymId, generalEnabled = true, gymFeedEnabled = true)
        }
    }

    suspend fun upsertGlobalSettings(generalEnabled: Boolean, gymFeedEnabled: Boolean) {
        val existing = fetchGlobalSettings()
        if (existing.id != null) {
            client.postgrest["community_settings"].update(
                mapOf(
                    "general_enabled" to generalEnabled,
                    "gym_feed_enabled" to gymFeedEnabled
                )
            ) {
                filter { eq("id", existing.id) }
            }
        } else {
            client.postgrest["community_settings"].insert(
                CommunitySettings(
                    scope = "global",
                    generalEnabled = generalEnabled,
                    gymFeedEnabled = gymFeedEnabled
                )
            )
        }
    }

    suspend fun upsertGymSettings(gymId: String, gymFeedEnabled: Boolean) {
        val existing = fetchGymSettings(gymId)
        if (existing.id != null) {
            client.postgrest["community_settings"].update(
                mapOf("gym_feed_enabled" to gymFeedEnabled)
            ) {
                filter { eq("id", existing.id) }
            }
        } else {
            client.postgrest["community_settings"].insert(
                CommunitySettings(
                    scope = "gym",
                    gymId = gymId,
                    generalEnabled = true,
                    gymFeedEnabled = gymFeedEnabled
                )
            )
        }
    }

    // -------------------------------------------------------------------------
    // Access helpers
    // -------------------------------------------------------------------------

    suspend fun canAccessGymFeed(userId: String, gymId: String?): Boolean {
        if (gymId.isNullOrBlank()) return false
        val global = fetchGlobalSettings()
        if (!global.gymFeedEnabled) return false
        val gymSettings = fetchGymSettings(gymId)
        if (!gymSettings.gymFeedEnabled) return false
        if (!MembershipService.isMembershipActive(userId)) return false
        val membership = MembershipService.fetchCurrentMembership(userId) ?: return false
        val membershipGym = membership.plan?.gymId
        // If plan gym is known, require match; otherwise allow when membership is active
        return membershipGym.isNullOrBlank() || membershipGym == gymId
    }

    suspend fun canAccessGeneralFeed(): Boolean {
        return fetchGlobalSettings().generalEnabled
    }

    // -------------------------------------------------------------------------
    // Groups
    // -------------------------------------------------------------------------

    suspend fun fetchGroups(scope: String, gymId: String? = null): List<CommunityGroup> {
        return try {
            client.postgrest["community_groups"]
                .select {
                    filter {
                        eq("scope", scope)
                        eq("is_active", true)
                        if (scope == "gym" && !gymId.isNullOrBlank()) {
                            eq("gym_id", gymId)
                        }
                        if (scope == "general") {
                            exact("gym_id", null)
                        }
                    }
                    order("name", Order.ASCENDING)
                }
                .decodeList<CommunityGroup>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun createGroup(
        scope: String,
        name: String,
        description: String?,
        gymId: String?,
        createdBy: String
    ) {
        val insert = CommunityGroupInsert(
            scope = scope,
            gymId = if (scope == "gym") gymId else null,
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            createdBy = createdBy
        )
        client.postgrest["community_groups"].insert(insert)
    }

    suspend fun deactivateGroup(groupId: String) {
        client.postgrest["community_groups"]
            .update(mapOf("is_active" to false)) {
                filter { eq("id", groupId) }
            }
    }

    suspend fun addGroupMember(groupId: String, userId: String, role: String = "member") {
        client.postgrest["community_group_members"].insert(
            CommunityGroupMemberInsert(groupId = groupId, userId = userId, role = role)
        )
    }

    suspend fun removeGroupMember(groupId: String, userId: String) {
        client.postgrest["community_group_members"].delete {
            filter {
                eq("group_id", groupId)
                eq("user_id", userId)
            }
        }
    }

    suspend fun fetchGroupMembers(groupId: String): List<CommunityGroupMember> {
        return try {
            client.postgrest["community_group_members"]
                .select {
                    filter { eq("group_id", groupId) }
                }
                .decodeList()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun isGroupMember(groupId: String, userId: String): Boolean {
        return try {
            client.postgrest["community_group_members"]
                .select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<CommunityGroupMember>()
                .isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    // Posts
    // -------------------------------------------------------------------------

    suspend fun fetchFeed(
        userId: String,
        scope: String,
        gymId: String? = null,
        groupId: String? = null,
        limit: Int = 50
    ): List<CommunityPostUi> {
        val posts = try {
            client.postgrest["community_posts"]
                .select {
                    filter {
                        eq("is_active", true)
                        if (groupId != null) {
                            eq("group_id", groupId)
                            eq("scope", "group")
                        } else {
                            eq("scope", scope)
                            if (scope == "gym" && !gymId.isNullOrBlank()) {
                                eq("gym_id", gymId)
                            }
                            exact("group_id", null)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<CommunityPost>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        if (posts.isEmpty()) return emptyList()

        val authorIds = posts.map { it.authorId }.distinct()
        val authors = fetchAuthorNames(authorIds)
        val postIds = posts.map { it.id }
        val likes = fetchLikesForPosts(postIds)
        val likeCounts = likes.groupingBy { it.postId }.eachCount()
        val myLikes = likes.filter { it.userId == userId }.map { it.postId }.toSet()

        val comments = try {
            client.postgrest["community_comments"]
                .select {
                    filter { isIn("post_id", postIds) }
                }
                .decodeList<CommunityComment>()
        } catch (_: Exception) {
            emptyList<CommunityComment>()
        }
        val commentCounts = comments.groupingBy { it.postId }.eachCount()

        val polls = try {
            client.postgrest["community_polls"]
                .select {
                    filter { isIn("post_id", postIds) }
                }
                .decodeList<CommunityPoll>()
        } catch (_: Exception) {
            emptyList<CommunityPoll>()
        }

        val pollUis = if (polls.isNotEmpty()) {
            val pollIds = polls.map { it.id }
            val options = try {
                client.postgrest["community_poll_options"]
                    .select {
                        filter { isIn("poll_id", pollIds) }
                    }
                    .decodeList<CommunityPollOption>()
            } catch (_: Exception) {
                emptyList<CommunityPollOption>()
            }

            val votes = try {
                client.postgrest["community_poll_votes"]
                    .select {
                        filter { isIn("poll_id", pollIds) }
                    }
                    .decodeList<CommunityPollVote>()
            } catch (_: Exception) {
                emptyList<CommunityPollVote>()
            }

            polls.associate { p ->
                val pOptions = options.filter { it.pollId == p.id }
                val pVotes = votes.filter { it.pollId == p.id }
                val totalVotes = pVotes.size
                val optionVotes = pVotes.groupingBy { it.optionId }.eachCount()
                val myVoteOptionId = pVotes.firstOrNull { it.userId == userId }?.optionId

                val optionsUi = pOptions.map { opt ->
                    val voteCount = optionVotes[opt.id] ?: 0
                    val pct = if (totalVotes == 0) 0 else ((voteCount.toFloat() / totalVotes) * 100).toInt()
                    CommunityPollOptionUi(option = opt, voteCount = voteCount, percentage = pct)
                }

                p.postId to CommunityPollUi(
                    poll = p,
                    options = optionsUi,
                    myVoteOptionId = myVoteOptionId,
                    totalVotes = totalVotes
                )
            }
        } else {
            emptyMap()
        }

        return posts.map { post ->
            CommunityPostUi(
                post = post,
                authorName = authors[post.authorId] ?: "Üye",
                likeCount = likeCounts[post.id] ?: 0,
                likedByMe = post.id in myLikes,
                commentCount = commentCounts[post.id] ?: 0,
                poll = pollUis[post.id]
            )
        }
    }

    private suspend fun fetchAuthorNames(userIds: List<String>): Map<String, String> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            client.postgrest["users"]
                .select {
                    filter { isIn("id", userIds) }
                }
                .decodeList<UserProfile>()
                .associate { user ->
                    val name = listOfNotNull(user.name, user.surname)
                        .joinToString(" ")
                        .ifBlank { user.email ?: "Üye" }
                    user.id to name
                }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun fetchLikesForPosts(postIds: List<String>): List<CommunityLike> {
        if (postIds.isEmpty()) return emptyList()
        return try {
            client.postgrest["community_likes"]
                .select {
                    filter { isIn("post_id", postIds) }
                }
                .decodeList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun createPost(
        authorId: String,
        scope: String,
        content: String?,
        imageUrl: String?,
        gymId: String? = null,
        groupId: String? = null
    ): String {
        val effectiveScope = if (groupId != null) "group" else scope
        val insert = CommunityPostInsert(
            authorId = authorId,
            scope = effectiveScope,
            gymId = if (effectiveScope == "gym" || (effectiveScope == "group" && gymId != null)) gymId else null,
            groupId = groupId,
            content = content?.trim()?.takeIf { it.isNotEmpty() },
            imageUrl = imageUrl?.takeIf { it.isNotBlank() }
        )
        val post = client.postgrest["community_posts"].insert(insert) {
            select()
        }.decodeSingle<CommunityPost>()
        return post.id
    }

    suspend fun deletePost(postId: String, userId: String) {
        client.postgrest["community_posts"]
            .update(mapOf("is_active" to false)) {
                filter {
                    eq("id", postId)
                    eq("author_id", userId)
                }
            }
    }

    suspend fun toggleLike(postId: String, userId: String): Boolean {
        val existing = client.postgrest["community_likes"]
            .select {
                filter {
                    eq("post_id", postId)
                    eq("user_id", userId)
                }
                limit(1)
            }
            .decodeList<CommunityLike>()
        return if (existing.isNotEmpty()) {
            client.postgrest["community_likes"].delete {
                filter {
                    eq("post_id", postId)
                    eq("user_id", userId)
                }
            }
            false
        } else {
            client.postgrest["community_likes"].insert(
                CommunityLike(postId = postId, userId = userId)
            )
            true
        }
    }

    // -------------------------------------------------------------------------
    // Comments
    // -------------------------------------------------------------------------

    suspend fun fetchComments(postId: String): List<CommunityCommentUi> {
        val comments = try {
            client.postgrest["community_comments"]
                .select {
                    filter { eq("post_id", postId) }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<CommunityComment>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

        if (comments.isEmpty()) return emptyList()

        val authorIds = comments.map { it.authorId }.distinct()
        val authors = fetchAuthorNames(authorIds)

        return comments.map { c ->
            CommunityCommentUi(
                comment = c,
                authorName = authors[c.authorId] ?: "Üye"
            )
        }
    }

    suspend fun createComment(postId: String, authorId: String, content: String) {
        val insert = CommunityCommentInsert(
            postId = postId,
            authorId = authorId,
            content = content.trim()
        )
        client.postgrest["community_comments"].insert(insert)
    }

    suspend fun deleteComment(commentId: String, userId: String) {
        client.postgrest["community_comments"].delete {
            filter {
                eq("id", commentId)
                eq("author_id", userId)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Polls
    // -------------------------------------------------------------------------

    suspend fun createPoll(postId: String, question: String, options: List<String>) {
        val poll = client.postgrest["community_polls"]
            .insert(mapOf("post_id" to postId, "question" to question.trim())) {
                select()
            }
            .decodeSingle<CommunityPoll>()

        val optionsInserts = options.filter { it.isNotBlank() }.map { opt ->
            mapOf("poll_id" to poll.id, "option_text" to opt.trim())
        }
        client.postgrest["community_poll_options"].insert(optionsInserts)
    }

    suspend fun votePoll(pollId: String, optionId: String, userId: String) {
        client.postgrest["community_poll_votes"].delete {
            filter {
                eq("poll_id", pollId)
                eq("user_id", userId)
            }
        }
        client.postgrest["community_poll_votes"].insert(
            CommunityPollVoteInsert(pollId = pollId, optionId = optionId, userId = userId)
        )
    }

    // -------------------------------------------------------------------------
    // Image upload
    // -------------------------------------------------------------------------

    suspend fun uploadImageFromUri(context: Context, userId: String, uri: Uri): String {
        val jpegBytes = withContext(Dispatchers.IO) {
            compressUriToJpeg(context, uri)
        }
        val path = "$userId/${UUID.randomUUID()}.jpg"
        try {
            client.storage[STORAGE_BUCKET].upload(path = path, data = jpegBytes) {
                upsert = true
            }
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (message.contains("Bucket not found", ignoreCase = true)) {
                throw IllegalStateException(
                    "community-images deposu bulunamadı. supabase/migrations/20260716000000_community_feed.sql dosyasını çalıştırın."
                )
            }
            throw e
        }
        return client.storage[STORAGE_BUCKET].publicUrl(path)
    }

    private fun compressUriToJpeg(context: Context, uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Görsel okunamadı")
        val bitmap = input.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Görsel çözümlenemedi")
        val maxSide = 1600
        val scale = maxOf(bitmap.width, bitmap.height).toFloat() / maxSide
        val scaled = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width / scale).toInt().coerceAtLeast(1),
                (bitmap.height / scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return out.toByteArray()
    }
}
