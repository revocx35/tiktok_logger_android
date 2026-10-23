package io.github.revocx35.tiktoklogger

import io.github.revocx35.tiktoklogger.core.StoryItem
import io.github.revocx35.tiktoklogger.media.MediaOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Saved story files: a story has no name of its own, so the app names it like the server does for a browser download —
 * `story_@user_2026-10-23_05-37.jpg` — instead of the `wa_photo-<ts>` the chat media uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryFileNameTest {
    private fun story(
        type: String = "video",
        mime: String? = "video/mp4",
        at: Long = 1792686726000,
        username: String? = "ege",
        userId: String = "7613506544320103454",
    ) = StoryItem(id = "7699530832234433799", userId = userId, username = username, type = type, mime = mime, takenAt = at)

    /** The name is built in the phone's own time zone, like the server's is in the container's (TZ=Europe/Istanbul). */
    private fun stamp(at: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))

    @Test
    fun namesAVideoStoryAfterItsAuthorAndTime() {
        val at = 1792686726000
        assertEquals("story_@ege_${stamp(at)}.mp4", MediaOps.storyFileName(story(at = at), null))
    }

    @Test
    fun usesTheStoryAuthorWhenTheChipIsNotChosenAndTheMimeForPhotos() {
        val at = 1792686726000
        assertEquals("story_@farkeder_${stamp(at)}.jpg", MediaOps.storyFileName(story(type = "image", mime = "image/jpeg", username = "farkeder"), null))
        // The viewer passes the name of the account being shown, which wins over the story's own field.
        assertEquals("story_@yoruk_${stamp(at)}.png", MediaOps.storyFileName(story(type = "image", mime = "image/png"), "yoruk"))
    }

    @Test
    fun fallsBackToTheIdAndCannotEscapeTheAlbum() {
        val noName = MediaOps.storyFileName(story(username = null), null)
        assertTrue(noName, noName.startsWith("story_@7613506544320103454_"))
        val hostile = MediaOps.storyFileName(story(username = "../../etc/pa\"ss\r\nword"), null)
        assertTrue(hostile, hostile.startsWith("story_@....etcpassword_"))
        assertEquals(hostile, hostile.replace(Regex("[\\\\/:*?\"<>|\r\n]"), ""))
    }

    @Test
    fun anUnknownMimeStillGetsAnExtension() {
        val name = MediaOps.storyFileName(story(mime = null), null)
        assertTrue(name, name.endsWith(".mp4"))
    }
}
