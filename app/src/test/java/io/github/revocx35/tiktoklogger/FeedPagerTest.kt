package io.github.revocx35.tiktoklogger

import io.github.revocx35.tiktoklogger.core.FeedItem
import io.github.revocx35.tiktoklogger.ui.screens.PLAYER_POOL
import io.github.revocx35.tiktoklogger.ui.screens.mergeFeedWindow
import io.github.revocx35.tiktoklogger.ui.screens.poolSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The swiping feed (0.7.0): how a fresh server window joins the pager's list, and which player a page uses. */
class FeedPagerTest {
    private fun item(id: String, liked: Boolean = false) = FeedItem(id = id, liked = liked)

    @Test
    fun newVideosAreAppendedAndNothingAboveMoves() {
        val old = listOf(item("1"), item("2"), item("3"))
        val merged = mergeFeedWindow(old, listOf(item("2"), item("3"), item("4"), item("5")), keep = "2")
        assertEquals(listOf("1", "2", "3", "4", "5"), merged.map { it.id })
    }

    @Test
    fun knownVideosAreRefreshedExceptTheOneOnScreen() {
        val old = listOf(item("1"), item("2", liked = true), item("3"))
        val merged = mergeFeedWindow(old, listOf(item("2", liked = false), item("3", liked = true)), keep = "2")
        assertTrue("the video on screen keeps its local (optimistic) state", merged[1].liked)
        assertTrue("a video below takes the server's state", merged[2].liked)
    }

    @Test
    fun anEmptyWindowChangesNothing() {
        val old = listOf(item("1"), item("2"))
        assertEquals(old, mergeFeedWindow(old, emptyList(), keep = "1"))
    }

    @Test
    fun neighbouringPagesNeverShareAPlayer() {
        for (p in 0..50) {
            val slots = (p - 1..p + 1).filter { it >= 0 }.map(::poolSlot)
            assertEquals("pages ${p - 1}..${p + 1}", slots.size, slots.toSet().size)
            assertTrue(poolSlot(p) in 0 until PLAYER_POOL)
        }
        assertNotEquals(poolSlot(0), poolSlot(1))
    }
}
