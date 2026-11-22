package com.example

import com.example.data.UpdateCheckGate
import com.example.data.UpdateConstants
import com.example.data.UpdateNotesFormatter
import com.example.data.UpdateUrlValidator
import com.example.data.UpdateVersionComparator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun compare_handlesVPrefixesAndMultiDigitSegments() {
        assertTrue(UpdateVersionComparator.compare("v1.10.0", "1.9.9")!! > 0)
        assertTrue(UpdateVersionComparator.compare("V2.0", "v1.99.99")!! > 0)
        assertEquals(0, UpdateVersionComparator.compare("v1.2", "1.2.0"))
        assertEquals(0, UpdateVersionComparator.compare("v.1.1.0", "1.1.0"))
        assertTrue(UpdateVersionComparator.compare("V.1.2.1", "1.2")!! > 0)
        assertTrue(UpdateVersionComparator.compare("1.2.1", "v1.2")!! > 0)
        assertTrue(UpdateVersionComparator.compare("v1.1.9", "1.2")!! < 0)
        assertTrue(UpdateVersionComparator.isSameVersion("v.1.2.0", "1.2"))
        assertFalse(UpdateVersionComparator.isSameVersion("", "1.2"))
    }

    @Test
    fun compare_stripsPreReleaseAndBuildMetadata() {
        assertEquals(0, UpdateVersionComparator.compare("v1.3.0-beta.1", "1.3.0"))
        assertEquals(0, UpdateVersionComparator.compare("1.3.0+build.42", "v1.3"))
        assertTrue(UpdateVersionComparator.compare("v1.4.0-rc.1+2026", "1.2")!! > 0)
    }

    @Test
    fun compare_returnsNullForUnparsableTags() {
        val invalidTags = listOf(
            "", "   ", "v", "V", "v.", "v..1.0", ".1.0", "release-final", "1..2", "1.2a", "-beta"
        )
        for (tag in invalidTags) {
            assertNull("Expected null parse for '$tag'", UpdateVersionComparator.parseSegments(tag))
            assertNull("Expected null compare for '$tag'", UpdateVersionComparator.compare(tag, "1.2"))
            assertNull("Expected null compare as current for '$tag'", UpdateVersionComparator.compare("1.3", tag))
        }
    }

    @Test
    fun normalizeDisplayVersion_standardizesLeadingV() {
        assertEquals("v1.2", UpdateVersionComparator.normalizeDisplayVersion("1.2"))
        assertEquals("v1.3.0", UpdateVersionComparator.normalizeDisplayVersion("V1.3.0"))
        assertEquals("v1.1.0", UpdateVersionComparator.normalizeDisplayVersion("v.1.1.0"))
        assertEquals("", UpdateVersionComparator.normalizeDisplayVersion("  "))
    }

    @Test
    fun validateReleasePageUrl_allowsOnlyRepoHttpsAndFallsBackOtherwise() {
        val validUrl = "https://github.com/Itsnanzzz/Lilith-MV/releases/tag/v1.3.0"
        assertEquals(validUrl, UpdateUrlValidator.validateReleasePageUrl(validUrl))

        val invalidUrls = listOf(
            "",
            "http://github.com/Itsnanzzz/Lilith-MV/releases/tag/v1.3.0",
            "https://github.com/OtherUser/Lilith-MV/releases/tag/v1.3.0",
            "https://github.com/Itsnanzzz/OtherRepo/releases/tag/v1.3.0",
            "https://github.com/Itsnanzzz/Lilith-MV/../OtherRepo",
            "https://evil.example.com/Lilith-MV.apk",
            "javascript:alert(1)"
        )
        for (bad in invalidUrls) {
            assertEquals(
                "Expected fallback for '$bad'",
                UpdateConstants.RELEASES_FALLBACK_URL,
                UpdateUrlValidator.validateReleasePageUrl(bad)
            )
        }
    }

    @Test
    fun validateAssetDownloadUrl_allowsTrustedGitHubHostsOnly() {
        val validGitHubAsset =
            "https://github.com/Itsnanzzz/Lilith-MV/releases/download/v1.3.0/app-release.apk"
        val validObjectsHost =
            "https://objects.githubusercontent.com/github-production-release-asset-123/app.apk"
        val validReleasesHost =
            "https://github-releases.githubusercontent.com/12345/app.apk"

        assertEquals(validGitHubAsset, UpdateUrlValidator.validateAssetDownloadUrl(validGitHubAsset))
        assertEquals(validObjectsHost, UpdateUrlValidator.validateAssetDownloadUrl(validObjectsHost))
        assertEquals(validReleasesHost, UpdateUrlValidator.validateAssetDownloadUrl(validReleasesHost))

        val invalidAssetUrls = listOf(
            "http://objects.githubusercontent.com/123/app.apk",
            "https://github.com/SomeoneElse/Lilith-MV/releases/download/v1.3.0/app.apk",
            "https://cdn.example.com/app.apk",
            "https://github.com:8443/Itsnanzzz/Lilith-MV/releases/download/v1.3.0/app.apk"
        )
        for (bad in invalidAssetUrls) {
            assertEquals(
                "Expected fallback for '$bad'",
                UpdateConstants.RELEASES_FALLBACK_URL,
                UpdateUrlValidator.validateAssetDownloadUrl(bad)
            )
        }
    }

    @Test
    fun formatReleaseNotes_stripsBasicMarkdownAndTruncates() {
        val raw = """
            ## What's New
            
            - Added **update** check
            - Fixed `crash` on startup
            
            
            
            ### Notes
            Plain line
        """.trimIndent()

        val expected = """
            What's New

            • Added update check
            • Fixed crash on startup

            Notes
            Plain line
        """.trimIndent()

        assertEquals(expected, UpdateNotesFormatter.formatReleaseNotes(raw))

        val huge = "a".repeat(4500)
        val formattedHuge = UpdateNotesFormatter.formatReleaseNotes(huge)
        assertEquals(UpdateConstants.MAX_NOTES_LENGTH, formattedHuge.length)
        assertTrue(formattedHuge.endsWith("…"))
    }

    @Test
    fun updateCheckGate_enforcesIntervalFutureClockAndRetryBackoff() {
        val now = 1_700_000_000_000L
        val oneHour = UpdateConstants.AUTO_CHECK_RETRY_DELAY_MS
        val oneDay = UpdateConstants.AUTO_CHECK_INTERVAL_MS

        assertTrue(UpdateCheckGate.isIntervalExpired(0L, now))
        assertTrue(UpdateCheckGate.isIntervalExpired(now + 60_000L, now))
        assertFalse(UpdateCheckGate.isIntervalExpired(now - oneDay + 1_000L, now))
        assertTrue(UpdateCheckGate.isIntervalExpired(now - oneDay, now))

        // Disabled / already checked / video open prevent auto-check
        assertFalse(
            UpdateCheckGate.shouldRunAutoCheck(
                autoCheckEnabled = false,
                hasCheckedThisProcess = false,
                isVideoPlayerOpen = false,
                lastCheckAtMs = 0L,
                lastFailedAttemptAtMs = 0L,
                nowMs = now
            )
        )
        assertFalse(
            UpdateCheckGate.shouldRunAutoCheck(
                autoCheckEnabled = true,
                hasCheckedThisProcess = true,
                isVideoPlayerOpen = false,
                lastCheckAtMs = 0L,
                lastFailedAttemptAtMs = 0L,
                nowMs = now
            )
        )
        assertFalse(
            UpdateCheckGate.shouldRunAutoCheck(
                autoCheckEnabled = true,
                hasCheckedThisProcess = false,
                isVideoPlayerOpen = true,
                lastCheckAtMs = 0L,
                lastFailedAttemptAtMs = 0L,
                nowMs = now
            )
        )

        // Failed attempt blocks for 1h, then allows retry when interval has expired
        assertFalse(
            UpdateCheckGate.shouldRunAutoCheck(
                autoCheckEnabled = true,
                hasCheckedThisProcess = false,
                isVideoPlayerOpen = false,
                lastCheckAtMs = 0L,
                lastFailedAttemptAtMs = now - oneHour + 5_000L,
                nowMs = now
            )
        )
        assertTrue(
            UpdateCheckGate.shouldRunAutoCheck(
                autoCheckEnabled = true,
                hasCheckedThisProcess = false,
                isVideoPlayerOpen = false,
                lastCheckAtMs = 0L,
                lastFailedAttemptAtMs = now - oneHour,
                nowMs = now
            )
        )
    }

    @Test
    fun updateCheckGate_enforcesManualCooldownAndAutoPopupWindow() {
        val now = 1_700_000_000_000L
        assertFalse(UpdateCheckGate.isManualCooldownActive(0L, now))
        assertFalse(UpdateCheckGate.isManualCooldownActive(now + 10_000L, now))
        assertTrue(UpdateCheckGate.isManualCooldownActive(now - 59_999L, now))
        assertFalse(UpdateCheckGate.isManualCooldownActive(now - 60_000L, now))

        assertTrue(UpdateCheckGate.isWithinAutoPopupLaunchWindow(0L))
        assertTrue(UpdateCheckGate.isWithinAutoPopupLaunchWindow(2_000L))
        assertTrue(UpdateCheckGate.isWithinAutoPopupLaunchWindow(10_000L))
        assertFalse(UpdateCheckGate.isWithinAutoPopupLaunchWindow(10_001L))
        assertFalse(UpdateCheckGate.isWithinAutoPopupLaunchWindow(-1L))
    }
}
