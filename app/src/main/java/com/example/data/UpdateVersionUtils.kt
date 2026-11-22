package com.example.data

import java.net.URI

object UpdateConstants {
    const val GITHUB_OWNER = "Itsnanzzz"
    const val GITHUB_REPO = "Lilith-MV"
    const val RELEASES_FALLBACK_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
    const val RELEASES_API_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val AUTO_CHECK_RETRY_DELAY_MS = 60L * 60L * 1000L
    const val MANUAL_COOLDOWN_MS = 60_000L
    const val AUTO_CHECK_STARTUP_DELAY_MS = 2_000L
    const val AUTO_POPUP_MAX_WAIT_AFTER_LAUNCH_MS = 10_000L
    const val MAX_NOTES_LENGTH = 4000
}

/**
 * Pure Kotlin semantic-ish version parser and comparator (no Android framework dependencies).
 */
object UpdateVersionComparator {

    fun parseSegments(raw: String): List<Long>? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) {
            val afterV = trimmed.substring(1)
            if (afterV.startsWith('.')) afterV.substring(1) else afterV
        } else {
            trimmed
        }
        val dashIdx = withoutPrefix.indexOf('-')
        val plusIdx = withoutPrefix.indexOf('+')
        val cutIdx = listOf(dashIdx, plusIdx).filter { it >= 0 }.minOrNull() ?: withoutPrefix.length
        val core = withoutPrefix.substring(0, cutIdx).trim()
        if (core.isEmpty()) return null

        val parts = core.split('.')
        val numbers = ArrayList<Long>(parts.size)
        for (part in parts) {
            if (part.isEmpty() || !part.all { it.isDigit() }) return null
            val num = part.toLongOrNull() ?: return null
            numbers.add(num)
        }
        return numbers
    }

    /**
     * Compares [candidate] against [current].
     * Returns > 0 if [candidate] is newer, 0 if equal, < 0 if older, or null if either is unparsable.
     */
    fun compare(candidate: String, current: String): Int? {
        val a = parseSegments(candidate) ?: return null
        val b = parseSegments(current) ?: return null
        val maxLen = maxOf(a.size, b.size)
        for (i in 0 until maxLen) {
            val segA = a.getOrElse(i) { 0L }
            val segB = b.getOrElse(i) { 0L }
            val cmp = segA.compareTo(segB)
            if (cmp != 0) return cmp
        }
        return 0
    }

    fun isSameVersion(a: String, b: String): Boolean {
        val first = a.trim()
        val second = b.trim()
        if (first.isEmpty() || second.isEmpty()) return false
        if (first == second) return true
        return compare(first, second) == 0
    }

    fun normalizeDisplayVersion(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("v", ignoreCase = true)) {
            val afterV = trimmed.substring(1)
            val rest = if (afterV.startsWith('.')) afterV.substring(1) else afterV
            if (rest.isEmpty()) trimmed else "v$rest"
        } else {
            "v$trimmed"
        }
    }
}

/**
 * Pure Kotlin URL validator for GitHub release and asset links.
 */
object UpdateUrlValidator {

    private const val REQUIRED_REPO_PATH_PREFIX =
        "/${UpdateConstants.GITHUB_OWNER}/${UpdateConstants.GITHUB_REPO}/"

    private val ASSET_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    fun validateReleasePageUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return UpdateConstants.RELEASES_FALLBACK_URL
        return try {
            val uri = URI(trimmed).normalize()
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val port = uri.port
            val path = uri.path.orEmpty()
            if (scheme == "https" &&
                host == "github.com" &&
                (port == -1 || port == 443) &&
                path.startsWith(REQUIRED_REPO_PATH_PREFIX)
            ) {
                trimmed
            } else {
                UpdateConstants.RELEASES_FALLBACK_URL
            }
        } catch (_: Exception) {
            UpdateConstants.RELEASES_FALLBACK_URL
        }
    }

    fun validateAssetDownloadUrl(rawUrl: String?): String {
        val trimmed = rawUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return UpdateConstants.RELEASES_FALLBACK_URL
        return try {
            val uri = URI(trimmed).normalize()
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            val port = uri.port
            val path = uri.path.orEmpty()
            val validHost = host != null && host in ASSET_HOSTS
            val validGithubPath = host != "github.com" || path.startsWith(REQUIRED_REPO_PATH_PREFIX)
            if (scheme == "https" &&
                validHost &&
                validGithubPath &&
                (port == -1 || port == 443)
            ) {
                trimmed
            } else {
                UpdateConstants.RELEASES_FALLBACK_URL
            }
        } catch (_: Exception) {
            UpdateConstants.RELEASES_FALLBACK_URL
        }
    }
}

/**
 * Pure Kotlin markdown-to-plain-text formatter for GitHub release notes.
 */
object UpdateNotesFormatter {
    private val HEADING_PREFIX_REGEX = Regex("""^\s*#+\s*""")
    private val BULLET_PREFIX_REGEX = Regex("""^(\s*)-\s+""")
    private val MULTI_BLANK_LINES_REGEX = Regex("""\n{3,}""")

    fun formatReleaseNotes(rawBody: String?): String {
        if (rawBody.isNullOrBlank()) return ""
        val normalized = rawBody
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val formattedLines = normalized.split('\n').map { line ->
            var cleaned = HEADING_PREFIX_REGEX.replace(line, "")
            cleaned = BULLET_PREFIX_REGEX.replace(cleaned, "$1• ")
            cleaned.replace("**", "").replace("`", "")
        }
        val joined = MULTI_BLANK_LINES_REGEX
            .replace(formattedLines.joinToString("\n"), "\n\n")
            .trim()
        return if (joined.length > UpdateConstants.MAX_NOTES_LENGTH) {
            joined.take(UpdateConstants.MAX_NOTES_LENGTH - 1).trimEnd() + "…"
        } else {
            joined
        }
    }
}

/**
 * Single source of truth for the 24h automatic check gate, 1h retry backoff after failure,
 * 60s manual check cooldown, and 10s launch window for blocked auto-popups.
 */
object UpdateCheckGate {

    fun isIntervalExpired(
        lastCheckAtMs: Long,
        nowMs: Long,
        intervalMs: Long = UpdateConstants.AUTO_CHECK_INTERVAL_MS
    ): Boolean {
        if (lastCheckAtMs <= 0L) return true
        // Monotonic fallback if device clock moved backwards or lastCheckAt is in the future
        if (lastCheckAtMs > nowMs) return true
        return (nowMs - lastCheckAtMs) >= intervalMs
    }

    fun shouldRunAutoCheck(
        autoCheckEnabled: Boolean,
        hasCheckedThisProcess: Boolean,
        isVideoPlayerOpen: Boolean,
        lastCheckAtMs: Long,
        lastFailedAttemptAtMs: Long,
        nowMs: Long
    ): Boolean {
        if (!autoCheckEnabled || hasCheckedThisProcess || isVideoPlayerOpen) return false
        if (lastFailedAttemptAtMs > 0L && nowMs >= lastFailedAttemptAtMs) {
            if ((nowMs - lastFailedAttemptAtMs) < UpdateConstants.AUTO_CHECK_RETRY_DELAY_MS) {
                return false
            }
        }
        return isIntervalExpired(lastCheckAtMs = lastCheckAtMs, nowMs = nowMs)
    }

    fun isManualCooldownActive(
        lastNetworkCheckAtMs: Long,
        nowMs: Long,
        cooldownMs: Long = UpdateConstants.MANUAL_COOLDOWN_MS
    ): Boolean {
        if (lastNetworkCheckAtMs <= 0L) return false
        if (lastNetworkCheckAtMs > nowMs) return false
        return (nowMs - lastNetworkCheckAtMs) < cooldownMs
    }

    fun isWithinAutoPopupLaunchWindow(
        elapsedSinceLaunchMs: Long,
        maxWaitMs: Long = UpdateConstants.AUTO_POPUP_MAX_WAIT_AFTER_LAUNCH_MS
    ): Boolean {
        return elapsedSinceLaunchMs in 0L..maxWaitMs
    }
}
