package com.app.coachup.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Topluluk model defaults / post body constraints (client-side checks).
 */
class CommunityModelsTest {

    @Test
    fun communitySettings_defaultsEnabled() {
        val s = CommunitySettings(scope = "global")
        assertTrue(s.generalEnabled)
        assertTrue(s.gymFeedEnabled)
        assertNull(s.gymId)
    }

    @Test
    fun communityPost_clientValidationRequiresTextOrImage() {
        fun isValidPost(content: String?, imageUrl: String?): Boolean =
            !content.isNullOrBlank() || !imageUrl.isNullOrBlank()

        assertTrue(isValidPost("Merhaba", null))
        assertTrue(isValidPost(null, "https://example.com/a.jpg"))
        assertTrue(isValidPost("x", "https://example.com/a.jpg"))
        assertFalse(isValidPost(null, null))
        assertFalse(isValidPost("   ", null))
        assertFalse(isValidPost("", ""))
    }

    @Test
    fun communityPostUi_likeState() {
        val post = CommunityPost(
            id = "p1",
            authorId = "u1",
            scope = "general",
            content = "Hi"
        )
        val ui = CommunityPostUi(
            post = post,
            authorName = "Ali Veli",
            likeCount = 3,
            likedByMe = true
        )
        assertEquals("Ali Veli", ui.authorName)
        assertEquals(3, ui.likeCount)
        assertTrue(ui.likedByMe)
    }

    @Test
    fun communityGroup_scopes() {
        val gymGroup = CommunityGroup(
            id = "1",
            scope = "gym",
            gymId = "gym-x",
            name = "VIP"
        )
        val generalGroup = CommunityGroup(
            id = "2",
            scope = "general",
            name = "Yarış"
        )
        assertEquals("gym", gymGroup.scope)
        assertEquals("gym-x", gymGroup.gymId)
        assertNull(generalGroup.gymId)
    }
}
