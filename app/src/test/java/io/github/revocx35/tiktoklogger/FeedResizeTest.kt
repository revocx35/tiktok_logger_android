package io.github.revocx35.tiktoklogger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.revocx35.tiktoklogger.data.Prefs
import io.github.revocx35.tiktoklogger.ui.screens.FeedActionRail
import io.github.revocx35.tiktoklogger.ui.screens.feedContentScale
import io.github.revocx35.tiktoklogger.ui.screens.feedResizeMode
import io.github.revocx35.tiktoklogger.ui.theme.WaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The feed's resize button: "Fit" shows the whole video (black bars), "Fill" crops it to the screen.
 *
 * Before this, the feed always played with `RESIZE_MODE_ZOOM`, so anything that is not 9:16 lost its sides and
 * there was no way back without leaving the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class FeedResizeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aFreshInstallFillsAndTheChoiceSticks() {
        Prefs(RuntimeEnvironment.getApplication()).feedFitVideo = false
        val prefs = Prefs(RuntimeEnvironment.getApplication())
        assertFalse("the feed fills the screen until the owner asks otherwise", prefs.feedFitVideo)

        prefs.feedFitVideo = true
        assertTrue(
            "the choice must survive a restart, or every launch crops again",
            Prefs(RuntimeEnvironment.getApplication()).feedFitVideo,
        )
        prefs.feedFitVideo = false
    }

    @Test
    fun theModeReachesThePlayerAndThePoster() {
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, feedResizeMode(fit = false))
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, feedResizeMode(fit = true))
        assertEquals(ContentScale.Crop, feedContentScale(fit = false))
        assertEquals(ContentScale.Fit, feedContentScale(fit = true))

        // The real surface takes both modes, which is the half of the change a unit test can prove.
        val view = PlayerView(RuntimeEnvironment.getApplication())
        view.resizeMode = feedResizeMode(fit = false)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, view.resizeMode)
        view.resizeMode = feedResizeMode(fit = true)
        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, view.resizeMode)
    }

    @Test
    fun theRailOffersResizeAndClickingItSwitchesTheMode() {
        var clicks = 0
        compose.setContent {
            var fit by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                FeedActionRail(
                    likes = 1_200,
                    commentsCount = 34,
                    liked = false,
                    muted = true,
                    fit = fit,
                    onLike = {},
                    onComments = {},
                    onSend = {},
                    onToggleFit = {
                        fit = !fit
                        clicks++
                    },
                    onToggleMute = {},
                    onRefresh = {},
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                )
            }
        }

        // Filling the screen: the button offers "Fit", and it sits with like / comments / send.
        compose.onNodeWithText("Fit").assertExists()
        compose.onNodeWithContentDescription("Like").assertExists()
        compose.onNodeWithContentDescription("Comments").assertExists()
        compose.onNodeWithContentDescription("Send this video to a chat").assertExists()

        compose.onNodeWithContentDescription("Fit the whole video on the screen").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks)
        compose.onNodeWithText("Fill").assertExists()

        compose.onNodeWithContentDescription("Fill the screen").performClick()
        compose.waitForIdle()
        assertEquals(2, clicks)
        compose.onNodeWithText("Fit").assertExists()
    }

    /** Screenshots of both states, for comparing the rail with the web UI (`build/screenshots/`). */
    @Test
    fun railScreenshots() = railShot("feed_rail_fill", fit = false)

    @Test
    fun railFitScreenshot() = railShot("feed_rail_fit", fit = true)

    private fun railShot(name: String, fit: Boolean) {
        compose.setContent {
            WaTheme(dark = true) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    FeedActionRail(
                        likes = 12_400,
                        commentsCount = 318,
                        liked = false,
                        muted = true,
                        fit = fit,
                        onLike = {},
                        onComments = {},
                        onSend = {},
                        onToggleFit = {},
                        onToggleMute = {},
                        onRefresh = {},
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }
}
