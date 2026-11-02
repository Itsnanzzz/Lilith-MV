package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.playback.AudioFormatDetector
import com.example.ui.common.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

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
    fun `main activity creates successfully`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        val activity = controller.get()
        assertNotNull(activity)
    }
}
