package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.playback.AudioFormatDetector
import com.example.playback.MusicPlaybackService
import com.example.ui.common.TimeUtils
import com.example.ui.common.getAppLocales
import com.example.ui.common.getAppString
import com.example.ui.common.setAppLocales
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var app: LilithApplication
    private lateinit var serviceController: ServiceController<MusicPlaybackService>

    @Before
    fun setUp() {
        MainActivity.clearLastSyncedLocaleTag()
        app = ApplicationProvider.getApplicationContext()
        serviceController = Robolectric.buildService(MusicPlaybackService::class.java).create()
        val binder = serviceController.get().onBind(Intent("androidx.media3.session.MediaSessionService"))
        Shadows.shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, MusicPlaybackService::class.java),
            binder
        )
        // Simulate fresh launch where default locale is initialized to Indonesian ("id")
        setAppLocales(app, "id")
    }

    @After
    fun tearDown() {
        MainActivity.clearLastSyncedLocaleTag()
        setAppLocales(app, "id")
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
    fun `cold start defaults to indonesian even when device locale is english`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
            assertEquals("id", getAppLocales(app).get(0)?.language)
        }
    }

    @Test
    fun `switching language in settings keeps UI strings and selected pill consistent`() {
        grantMediaPermissions()
        RuntimeEnvironment.setQualifiers("+id-rID")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()

            // Verify bottom bar initially renders Indonesian
            composeTestRule.onNodeWithText("Musik").assertIsDisplayed()
            composeTestRule.onNodeWithText("Video").assertIsDisplayed()
            composeTestRule.onNodeWithText("Pengaturan").assertIsDisplayed()

            // Open Settings tab and verify Indonesia pill is selected with Indonesian labels
            composeTestRule.onNodeWithTag("nav_settings_item").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Bahasa Aplikasi").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("lang_option_id").performScrollTo().assertIsDisplayed().assertIsSelected()
            composeTestRule.onNodeWithTag("lang_option_en").performScrollTo().assertIsDisplayed().assertIsNotSelected()

            // Switch to English
            composeTestRule.onNodeWithTag("lang_option_en").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals("en", getAppLocales(app).get(0)?.language)

            // Simulate Android 13+ LocaleManagerService configuration dispatch & Activity recreation
            RuntimeEnvironment.setQualifiers("+en-rUS")
            scenario.recreate()
            composeTestRule.waitForIdle()

            // Verify active Settings tab is preserved, UI renders in English, and English pill is selected
            composeTestRule.onAllNodesWithText("Settings").onFirst().performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Theme").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Accent Color").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("App Language").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Gapless Playback").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("lang_option_en").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("lang_option_id").performScrollTo().assertIsNotSelected()
            composeTestRule.onNodeWithText("Music").assertIsDisplayed()
            composeTestRule.onNodeWithText("Video").assertIsDisplayed()

            // Switch back to Indonesian
            composeTestRule.onNodeWithTag("lang_option_id").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals("id", getAppLocales(app).get(0)?.language)

            RuntimeEnvironment.setQualifiers("+id-rID")
            scenario.recreate()
            composeTestRule.waitForIdle()

            composeTestRule.onAllNodesWithText("Pengaturan").onFirst().performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Tema").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Warna Aksen").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("Bahasa Aplikasi").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("lang_option_id").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("lang_option_en").performScrollTo().assertIsNotSelected()
            composeTestRule.onNodeWithText("Musik").assertIsDisplayed()
        }
    }

    @Test
    fun `plurals and getAppString resolve accurately for indonesian and english`() {
        setAppLocales(app, "id")
        assertEquals("Tidak ada media ditemukan", app.getAppString(R.string.no_media_found))
        val idConfig = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("id"))
        }
        val idRes = app.createConfigurationContext(idConfig).resources
        assertEquals("1 Lagu", idRes.getQuantityString(R.plurals.songs_count, 1, 1))
        assertEquals("5 Lagu", idRes.getQuantityString(R.plurals.songs_count, 5, 5))

        setAppLocales(app, "en")
        assertEquals("No media found", app.getAppString(R.string.no_media_found))
        val enConfig = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("en"))
        }
        val enRes = app.createConfigurationContext(enConfig).resources
        assertEquals("1 Song", enRes.getQuantityString(R.plurals.songs_count, 1, 1))
        assertEquals("5 Songs", enRes.getQuantityString(R.plurals.songs_count, 5, 5))
    }

    @Test
    fun `desynced configuration recovers instead of ignoring language selection`() {
        grantMediaPermissions()
        // Activity starts with en-rUS configuration while app locale is "id"
        RuntimeEnvironment.setQualifiers("+en-rUS")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("nav_settings_item").performClick()
            composeTestRule.waitForIdle()

            // Even if system configuration lagged in en-rUS, tapping "id" must not no-op
            composeTestRule.onNodeWithTag("lang_option_id").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals("id", getAppLocales(app).get(0)?.language)

            // Switching to "en" updates stored locale and keeps selection synced
            composeTestRule.onNodeWithTag("lang_option_en").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            assertEquals("en", getAppLocales(app).get(0)?.language)
            composeTestRule.onNodeWithTag("lang_option_en").performScrollTo().assertIsSelected()
            composeTestRule.onNodeWithTag("lang_option_id").performScrollTo().assertIsNotSelected()
        }
    }

    @Test
    fun `diagnostic lang debug with multi locale id and en`() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        RuntimeEnvironment.setQualifiers("+in-rID,+en-rUS")
        com.example.ui.common.logLangDebug(app, "Diagnostic.multiLocale(in-rID,en-rUS)")
        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitForIdle()
        }
    }
}
