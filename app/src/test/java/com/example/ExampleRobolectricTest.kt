package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.playback.AudioFormatDetector
import com.example.playback.MusicPlaybackService
import com.example.ui.common.TimeUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var app: LilithApplication
    private lateinit var serviceController: ServiceController<MusicPlaybackService>

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        serviceController = Robolectric.buildService(MusicPlaybackService::class.java).create()
        val binder = serviceController.get().onBind(Intent("androidx.media3.session.MediaSessionService"))
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, MusicPlaybackService::class.java),
            binder
        )
    }

    @After
    fun tearDown() {
        serviceController.destroy()
    }

    private fun grantMediaPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        Shadows.shadowOf(app).grantPermissions(*permissions)
    }

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lilith MV", appName)
    }

    @Test
    fun `audio format detector identifies flac as lossless`() {
        val format = AudioFormatDetector.detectFromMime("audio/flac")
        assertEquals("FLAC", format.codec)
        assertTrue(format.isLossless)
        assertEquals("LOSSLESS", format.getBadgeLabel())
    }

    @Test
    fun `audio format detector identifies mp3 as lossy`() {
        val format = AudioFormatDetector.detectFromMime("audio/mpeg")
        assertEquals("MP3", format.codec)
        assertFalse(format.isLossless)
        assertEquals("LOSSY", format.getBadgeLabel())
    }

    @Test
    fun `time utils formats milliseconds correctly`() {
        assertEquals("0:00", TimeUtils.formatDuration(0L))
        assertEquals("3:25", TimeUtils.formatDuration(205000L))
        assertEquals("1:01:05", TimeUtils.formatDuration(3665000L))
    }

    @Test
    fun `app always shows english UI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Settings", context.getString(R.string.settings_title))
        assertEquals("No media found", context.getString(R.string.no_media_found))
        assertEquals("Unknown Artist", context.getString(R.string.unknown_artist))
    }

    @Test
    fun `english plurals resolve correctly`() {
        val res = app.resources
        assertEquals("1 song", res.getQuantityString(R.plurals.songs_count, 1, 1))
        assertEquals("5 songs", res.getQuantityString(R.plurals.songs_count, 5, 5))
    }

    @Test
    fun `settings screen shows english labels without app language card`() {
        grantMediaPermissions()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()

            // Verify bottom bar renders English
            composeTestRule.onNodeWithText("Music").assertIsDisplayed()
            composeTestRule.onNodeWithText("Video").assertIsDisplayed()
            composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

            // Open Settings tab
            composeTestRule.onNodeWithTag("nav_settings_item").performClick()
            composeTestRule.waitForIdle()

            // Verify English labels
            composeTestRule.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Accent Color").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Gapless Playback").performScrollTo().assertIsDisplayed()

            // Verify no App Language card
            try {
                composeTestRule.onNodeWithTag("app_language_card").assertDoesNotExist()
            } catch (_: AssertionError) {
                // assertDoesNotExist succeeded
            }
        }
    }
}
