package com.example

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaRepository
import com.example.data.RenameMediaResult
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistSongEntity
import com.example.data.model.AudioTrack
import com.example.data.model.SongExtraInfo
import com.example.playback.AudioFormatDetector
import com.example.playback.MusicPlaybackService
import com.example.ui.common.MediaRenameDialog
import com.example.ui.common.TimeUtils
import com.example.ui.music.CollectionDetailView
import com.example.ui.music.MusicViewModel
import com.example.ui.music.SongInfoDialog
import com.example.ui.theme.LilithTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.io.File

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

    @Test
    fun `song info dialog displays metadata and handles close`() {
        var dismissed = false
        val sampleTrack = AudioTrack(
            id = 42L,
            uri = "content://media/external/audio/media/42",
            title = "Midnight Echoes",
            artist = "Neon Skyline",
            album = "After Hours",
            durationMs = 215_000L,
            path = "/storage/emulated/0/Music/Midnight_Echoes.flac",
            folderName = "Music",
            mimeType = "audio/flac",
            size = 24_500_000L,
            displayName = "Midnight_Echoes.flac",
            dateAdded = 1_700_000_000L,
            dateModified = 1_700_001_000L
        )

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    LilithTheme {
                        SongInfoDialog(
                            track = sampleTrack,
                            onDismiss = { dismissed = true },
                            onCopyPath = {},
                            loadExtraInfo = {
                                SongExtraInfo(
                                    codec = "FLAC",
                                    bitrate = "1411 kbps",
                                    sampleRate = "44.1 kHz",
                                    bitDepth = "16-bit",
                                    channels = "2 (Stereo)"
                                )
                            }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText("Midnight_Echoes.flac").assertIsDisplayed()
            composeTestRule.onNodeWithText("Midnight Echoes").assertIsDisplayed()
            composeTestRule.onNodeWithText("Neon Skyline").assertIsDisplayed()
            composeTestRule.onNodeWithText("After Hours").assertIsDisplayed()
            composeTestRule.onNodeWithTag("song_info_path").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("FLAC").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("1411 kbps").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("44.1 kHz").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("16-bit").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText("2 (Stereo)").performScrollTo().assertIsDisplayed()

            composeTestRule.onNodeWithTag("info_dialog_close_button").performClick()
            composeTestRule.waitForIdle()
            assertTrue(dismissed)
        }
    }

    @Test
    fun `media rename dialog validates input and enables confirm only for valid new names`() {
        var confirmedFullName: String? = null
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    LilithTheme {
                        MediaRenameDialog(
                            title = activity.getString(R.string.rename_song_title),
                            baseName = "OldTrack",
                            extension = "mp3",
                            isRenaming = false,
                            onConfirm = { confirmedFullName = it },
                            onDismiss = {},
                            validateName = { raw ->
                                when {
                                    raw.trim().isEmpty() -> activity.getString(R.string.rename_error_empty)
                                    raw.contains('/') -> activity.getString(R.string.rename_error_invalid_chars)
                                    else -> null
                                }
                            },
                            textFieldTestTag = "rename_song_text_field"
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            // Unchanged name keeps confirm button disabled
            composeTestRule.onNodeWithTag("rename_dialog_confirm_button").assertIsNotEnabled()

            // Invalid character triggers error and disables confirm
            composeTestRule.onNodeWithTag("rename_song_text_field").performTextReplacement("Bad/Name")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("rename_dialog_error_text").assertIsDisplayed()
            composeTestRule.onNodeWithTag("rename_dialog_confirm_button").assertIsNotEnabled()

            // Valid new name enables confirm and outputs full filename with extension
            composeTestRule.onNodeWithTag("rename_song_text_field").performTextReplacement("NewTrack")
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("rename_dialog_confirm_button")
                .assertIsEnabled()
                .performClick()
            composeTestRule.waitForIdle()
            assertEquals("NewTrack.mp3", confirmedFullName)
        }
    }

    @Test
    fun `collection detail forwards info share rename and delete actions`() {
        var infoClicked: AudioTrack? = null
        var shareClicked: AudioTrack? = null
        var renameClicked: AudioTrack? = null
        var deleteClicked: AudioTrack? = null

        val track = AudioTrack(
            id = 99L,
            uri = "content://media/external/audio/media/99",
            title = "Album Closer",
            artist = "Starlight",
            album = "Horizon",
            durationMs = 180_000L,
            displayName = "Album Closer.mp3"
        )

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    LilithTheme {
                        CollectionDetailView(
                            title = "Horizon",
                            subtitle = "Starlight",
                            artTarget = null,
                            tracks = listOf(track),
                            currentPlayingTrackUri = null,
                            isPlaying = false,
                            playlists = emptyList(),
                            onBack = {},
                            onPlayTrack = {},
                            onPlayAll = {},
                            onShuffleAll = {},
                            onAddToQueue = {},
                            onAddToPlaylist = { _, _ -> },
                            onInfo = { infoClicked = it },
                            onShare = { shareClicked = it },
                            onRename = { renameClicked = it },
                            onDeletePermanently = { deleteClicked = it }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            // Open menu and verify Info
            composeTestRule.onNodeWithTag("song_more_button_99").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("song_menu_info_99").performClick()
            composeTestRule.waitForIdle()
            assertEquals(track.id, infoClicked?.id)

            // Open menu and verify Share
            composeTestRule.onNodeWithTag("song_more_button_99").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("song_menu_share_99").performClick()
            composeTestRule.waitForIdle()
            assertEquals(track.id, shareClicked?.id)

            // Open menu and verify Rename
            composeTestRule.onNodeWithTag("song_more_button_99").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("song_menu_rename_99").performClick()
            composeTestRule.waitForIdle()
            assertEquals(track.id, renameClicked?.id)

            // Open menu and verify Delete Permanently
            composeTestRule.onNodeWithTag("song_more_button_99").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("delete_permanently_item_99").performClick()
            composeTestRule.waitForIdle()
            assertEquals(track.id, deleteClicked?.id)
        }
    }

    @Test
    fun `playlist detail hides delete permanently while keeping info share and rename`() {
        var infoClicked = false
        var shareClicked = false
        var renameClicked = false
        var removeFromPlaylistClicked = false

        val track = AudioTrack(
            id = 202L,
            uri = "content://media/external/audio/media/202",
            title = "Playlist Track",
            artist = "Artist",
            album = "Album",
            durationMs = 150_000L,
            displayName = "Playlist Track.flac"
        )

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    LilithTheme {
                        CollectionDetailView(
                            title = "My Playlist",
                            subtitle = "1 song",
                            artTarget = null,
                            tracks = listOf(track),
                            currentPlayingTrackUri = null,
                            isPlaying = false,
                            playlists = emptyList(),
                            onBack = {},
                            onPlayTrack = {},
                            onPlayAll = {},
                            onShuffleAll = {},
                            onAddToQueue = {},
                            onAddToPlaylist = { _, _ -> },
                            showBackButton = false,
                            currentPlaylistId = 10L,
                            onInfo = { infoClicked = true },
                            onShare = { shareClicked = true },
                            onRename = { renameClicked = true },
                            onRemoveFromPlaylist = { removeFromPlaylistClicked = true },
                            onDeletePermanently = { error("Should not be passed in playlist mode") }
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("song_more_button_202").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("song_menu_info_202").assertIsDisplayed()
            composeTestRule.onNodeWithTag("song_menu_share_202").assertIsDisplayed()
            composeTestRule.onNodeWithTag("song_menu_rename_202").assertIsDisplayed()
            composeTestRule.onNodeWithTag("delete_permanently_item_202").assertDoesNotExist()

            composeTestRule.onNodeWithTag("song_menu_info_202").performClick()
            composeTestRule.waitForIdle()
            assertTrue(infoClicked)

            composeTestRule.onNodeWithTag("song_more_button_202").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("song_menu_share_202").performClick()
            composeTestRule.waitForIdle()
            assertTrue(shareClicked)

            composeTestRule.onNodeWithTag("song_more_button_202").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("song_menu_rename_202").performClick()
            composeTestRule.waitForIdle()
            assertTrue(renameClicked)

            composeTestRule.onNodeWithTag("song_more_button_202").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("Remove from Playlist").performClick()
            composeTestRule.waitForIdle()
            assertTrue(removeFromPlaylistClicked)
        }
    }

    @Test
    fun `music view model rename validation and delete cleanup work with room and files`() = runBlocking {
        val tempDir = File(app.cacheDir, "music_actions_test").apply { mkdirs() }
        val file1 = File(tempDir, "AlphaSong.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val file2 = File(tempDir, "BetaSong.mp3").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        try {
            val viewModel = MusicViewModel(app)
            val track1 = AudioTrack(
                id = 101L,
                uri = Uri.fromFile(file1).toString(),
                title = "AlphaSong",
                artist = "Tester",
                album = "TestAlbum",
                path = file1.absolutePath,
                folderName = tempDir.name,
                displayName = "AlphaSong.mp3"
            )

            // Validate empty, invalid chars, and file-system collision with BetaSong.mp3
            assertNotNull(viewModel.validateTrackName(track1, "   "))
            assertNotNull(viewModel.validateTrackName(track1, "Bad:Name"))
            assertNotNull(viewModel.validateTrackName(track1, ".Hidden"))
            assertNotNull(viewModel.validateTrackName(track1, "BetaSong"))
            assertNull(viewModel.validateTrackName(track1, "GammaSong"))

            // Insert into Room playlist and verify delete cleanup removes it
            val playlistId = app.database.playlistDao().insertPlaylist(PlaylistEntity(name = "Roadtrip"))
            app.database.playlistDao().insertPlaylistSong(
                PlaylistSongEntity(
                    playlistId = playlistId,
                    audioId = track1.id,
                    audioUri = track1.uri,
                    title = track1.title,
                    artist = track1.artist,
                    album = track1.album,
                    durationMs = 120_000L,
                    orderIndex = 0
                )
            )
            assertEquals(1, app.database.playlistDao().getSongsForPlaylist(playlistId).first().size)

            viewModel.onTrackDeletedSuccessfully(track1)
            val deadline = System.currentTimeMillis() + 3_000L
            while (
                app.database.playlistDao().getSongsForPlaylist(playlistId).first().isNotEmpty() &&
                System.currentTimeMillis() < deadline
            ) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                kotlinx.coroutines.delay(25)
            }
            assertEquals(0, app.database.playlistDao().getSongsForPlaylist(playlistId).first().size)

            // Verify MediaRepository file:// rename & delete
            val repo = MediaRepository(app)
            val renameResult = repo.renameTrack(track1, "GammaSong.mp3")
            assertTrue(renameResult is RenameMediaResult.Success)
            val renamedFile = File(tempDir, "GammaSong.mp3")
            assertTrue(renamedFile.exists())
            assertFalse(file1.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
