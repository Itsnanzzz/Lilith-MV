package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import com.example.BuildConfig
import com.example.LilithApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult

    data class UpdateAvailable(
        val version: String,
        val notes: String,
        val releaseUrl: String,
        val apkUrl: String?,
        val apkSize: Long?,
        val publishedAt: String
    ) : UpdateCheckResult

    data object NoReleases : UpdateCheckResult
    data object RateLimited : UpdateCheckResult
    data object NetworkError : UpdateCheckResult
    data object ParseError : UpdateCheckResult
}

data class UpdateUiState(
    val isChecking: Boolean = false,
    val available: UpdateCheckResult.UpdateAvailable? = null,
    val lastResult: UpdateCheckResult? = null,
    val lastCheckedAt: Long = 0L,
    val skippedVersion: String = "",
    val autoCheckEnabled: Boolean = true
) {
    val isIdle: Boolean get() = !isChecking

    val hasAvailableBadge: Boolean
        get() = !isChecking && available != null &&
            !UpdateVersionComparator.isSameVersion(available.version, skippedVersion)
}

class UpdateRepository(
    context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val appScope: CoroutineScope
) {
    private val appContext = context.applicationContext
    private val launchElapsedMs: Long = SystemClock.elapsedRealtime()

    private val _isChecking = MutableStateFlow(false)
    private val _lastNetworkResult = MutableStateFlow<UpdateCheckResult?>(null)
    private val _activeDialogUpdate = MutableStateFlow<UpdateCheckResult.UpdateAvailable?>(null)
    val activeDialogUpdate: StateFlow<UpdateCheckResult.UpdateAvailable?> =
        _activeDialogUpdate.asStateFlow()

    private val checkLock = Any()
    private var inFlightCheck: Deferred<UpdateCheckResult>? = null

    @Volatile
    private var hasStartedStartupFlow = false

    @Volatile
    private var hasAutoCheckedThisProcess = false

    @Volatile
    private var deferredAutoCheckUntilVideoCloses = false

    @Volatile
    private var lastFailedAutoCheckAtMs = 0L

    @Volatile
    private var lastNetworkAttemptAtMs = 0L

    @Volatile
    private var isAutoPopupSuppressedForSession = false

    private var startupJob: Job? = null
    private var autoPopupDeadlineJob: Job? = null
    private var pendingAutoPopupUpdate: UpdateCheckResult.UpdateAvailable? = null
    private val shownAutoPopupVersionsThisProcess = HashSet<String>()

    private data class PrefSnapshot(
        val autoCheckEnabled: Boolean,
        val lastCheckedAt: Long,
        val skippedVersion: String,
        val cachedRelease: CachedReleaseData?
    )

    private val prefSnapshotFlow = combine(
        settingsDataStore.updateAutoCheckFlow,
        settingsDataStore.updateLastCheckAtFlow,
        settingsDataStore.updateSkippedVersionFlow,
        settingsDataStore.updateCachedReleaseFlow
    ) { autoCheck, lastCheckAt, skippedVersion, cachedRelease ->
        PrefSnapshot(
            autoCheckEnabled = autoCheck,
            lastCheckedAt = lastCheckAt,
            skippedVersion = skippedVersion,
            cachedRelease = cachedRelease
        )
    }

    val uiState: StateFlow<UpdateUiState> = combine(
        _isChecking,
        _lastNetworkResult,
        prefSnapshotFlow
    ) { checking, networkResult, prefs ->
        val installedVersion = getInstalledVersionName()
        val cachedAvailable = prefs.cachedRelease?.toUpdateAvailableIfNewer(installedVersion)
        val networkAvailable = (networkResult as? UpdateCheckResult.UpdateAvailable)
            ?.takeIf { (UpdateVersionComparator.compare(it.version, installedVersion) ?: 0) > 0 }
        val resolvedAvailable = networkAvailable ?: cachedAvailable
        val resolvedLastResult = when {
            networkResult is UpdateCheckResult.UpdateAvailable ->
                networkAvailable ?: UpdateCheckResult.UpToDate
            networkResult != null -> networkResult
            cachedAvailable != null -> cachedAvailable
            else -> null
        }
        UpdateUiState(
            isChecking = checking,
            available = resolvedAvailable,
            lastResult = resolvedLastResult,
            lastCheckedAt = prefs.lastCheckedAt,
            skippedVersion = prefs.skippedVersion,
            autoCheckEnabled = prefs.autoCheckEnabled
        )
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = UpdateUiState()
    )

    fun getInstalledVersionName(): String {
        return try {
            val pm = appContext.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    appContext.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(appContext.packageName, 0)
            }
            packageInfo.versionName?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    fun getInstalledDisplayVersion(): String {
        return UpdateVersionComparator.normalizeDisplayVersion(getInstalledVersionName())
    }

    fun markLaunchedWithViewIntent() {
        isAutoPopupSuppressedForSession = true
        pendingAutoPopupUpdate = null
        autoPopupDeadlineJob?.cancel()
    }

    /**
     * Cold-start entry point called once after the first resumed frame.
     * - If "Check automatically" is off: does nothing.
     * - ~2s after first frame: evaluates stored cached release for cold-start popup
     *   (re-comparing against installed versionName), and runs the 24h network check if due.
     * - Never runs again when returning from background in the same process.
     */
    fun onMainUiFirstResumedFrame(
        isVideoPlayerOpen: () -> Boolean,
        isPopupBlocked: () -> Boolean
    ) {
        if (hasStartedStartupFlow) return
        hasStartedStartupFlow = true

        startupJob = appScope.launch {
            val autoEnabledInitially = settingsDataStore.updateAutoCheckFlow.first()
            if (!autoEnabledInitially) return@launch

            delay(UpdateConstants.AUTO_CHECK_STARTUP_DELAY_MS)

            val autoEnabled = settingsDataStore.updateAutoCheckFlow.first()
            if (!autoEnabled) return@launch

            // 1. Evaluate stored release for cold-start popup (zero network calls)
            if (!isAutoPopupSuppressedForSession) {
                val installedVersion = getInstalledVersionName()
                val cachedRelease = settingsDataStore.updateCachedReleaseFlow.first()
                val cachedUpdate = cachedRelease?.toUpdateAvailableIfNewer(installedVersion)
                val skipped = settingsDataStore.updateSkippedVersionFlow.first()
                if (cachedUpdate != null &&
                    !UpdateVersionComparator.isSameVersion(cachedUpdate.version, skipped) &&
                    cachedUpdate.version !in shownAutoPopupVersionsThisProcess
                ) {
                    pendingAutoPopupUpdate = cachedUpdate
                    tryPresentPendingAutoPopup(isPopupBlocked())
                }
            }

            // 2. Run automatic network check if 24h gate expired & not in video player
            runAutoNetworkCheckIfEligible(
                isVideoPlayerOpen = isVideoPlayerOpen,
                isPopupBlocked = isPopupBlocked
            )
        }
    }

    /**
     * Called when an overlay blocker (dialog, sheet, full-screen video player) closes.
     * Also lets a launch-time ACTION_VIEW video complete its deferred network check once closed.
     */
    fun onAutoPopupBlockerChanged(
        isPopupBlocked: Boolean,
        isVideoPlayerOpen: () -> Boolean
    ) {
        if (!isPopupBlocked && pendingAutoPopupUpdate != null) {
            appScope.launch {
                tryPresentPendingAutoPopup(isPopupBlocked = false)
            }
        }
        if (deferredAutoCheckUntilVideoCloses && !isVideoPlayerOpen()) {
            deferredAutoCheckUntilVideoCloses = false
            appScope.launch {
                runAutoNetworkCheckIfEligible(
                    isVideoPlayerOpen = isVideoPlayerOpen,
                    isPopupBlocked = { isPopupBlocked }
                )
            }
        }
    }

    /**
     * Runs a manual update check.
     * - Only one check runs at a time (if an automatic check is running, awaits its result).
     * - Ignores the 24h gate, but respects the 60s manual cooldown using the stored result.
     * - Always clears skipped version and opens the dialog when an update is available.
     */
    suspend fun checkManually(): UpdateCheckResult {
        val existing = synchronized(checkLock) { inFlightCheck?.takeIf { it.isActive } }
        val result = if (existing != null) {
            existing.await()
        } else {
            val nowMs = System.currentTimeMillis()
            val persistedLastAt = settingsDataStore.updateLastCheckAtFlow.first()
            val effectiveLastNetworkAt = listOf(lastNetworkAttemptAtMs, persistedLastAt)
                .filter { it in 1L..nowMs }
                .maxOrNull() ?: 0L

            if (UpdateCheckGate.isManualCooldownActive(effectiveLastNetworkAt, nowMs)) {
                resolveStoredManualResult()
            } else {
                val installedVersion = getInstalledVersionName()
                getOrStartNetworkCheck(installedVersion).await()
            }
        }

        _lastNetworkResult.value = result
        if (result is UpdateCheckResult.UpdateAvailable) {
            settingsDataStore.setUpdateSkippedVersion("")
            pendingAutoPopupUpdate = null
            autoPopupDeadlineJob?.cancel()
            shownAutoPopupVersionsThisProcess.add(result.version)
            _activeDialogUpdate.value = result
        }
        return result
    }

    fun dismissUpdateDialog() {
        _activeDialogUpdate.value = null
    }

    suspend fun skipVersion(version: String) {
        _activeDialogUpdate.value = null
        pendingAutoPopupUpdate = null
        autoPopupDeadlineJob?.cancel()
        settingsDataStore.setUpdateSkippedVersion(version)
    }

    suspend fun setAutoCheckEnabled(enabled: Boolean) {
        if (!enabled) {
            pendingAutoPopupUpdate = null
            autoPopupDeadlineJob?.cancel()
        }
        settingsDataStore.setUpdateAutoCheck(enabled)
    }

    private suspend fun runAutoNetworkCheckIfEligible(
        isVideoPlayerOpen: () -> Boolean,
        isPopupBlocked: () -> Boolean
    ) {
        if (!LilithApplication.isAppInForegroundFlow.value) return
        if (isVideoPlayerOpen()) {
            if (!hasAutoCheckedThisProcess) {
                deferredAutoCheckUntilVideoCloses = true
            }
            return
        }

        val autoEnabled = settingsDataStore.updateAutoCheckFlow.first()
        val lastCheckAt = settingsDataStore.updateLastCheckAtFlow.first()
        val nowMs = System.currentTimeMillis()
        val eligible = UpdateCheckGate.shouldRunAutoCheck(
            autoCheckEnabled = autoEnabled,
            hasCheckedThisProcess = hasAutoCheckedThisProcess,
            isVideoPlayerOpen = isVideoPlayerOpen(),
            lastCheckAtMs = lastCheckAt,
            lastFailedAttemptAtMs = lastFailedAutoCheckAtMs,
            nowMs = nowMs
        )
        if (!eligible) return

        hasAutoCheckedThisProcess = true
        val installedVersion = getInstalledVersionName()
        val result = getOrStartNetworkCheck(installedVersion).await()
        when (result) {
            is UpdateCheckResult.UpdateAvailable -> {
                _lastNetworkResult.value = result
                val stillAutoEnabled = settingsDataStore.updateAutoCheckFlow.first()
                val skipped = settingsDataStore.updateSkippedVersionFlow.first()
                if (stillAutoEnabled &&
                    !isAutoPopupSuppressedForSession &&
                    !UpdateVersionComparator.isSameVersion(result.version, skipped) &&
                    result.version !in shownAutoPopupVersionsThisProcess
                ) {
                    pendingAutoPopupUpdate = result
                    tryPresentPendingAutoPopup(isPopupBlocked())
                }
            }

            UpdateCheckResult.UpToDate -> {
                _lastNetworkResult.value = result
            }

            UpdateCheckResult.NoReleases,
            UpdateCheckResult.RateLimited,
            UpdateCheckResult.NetworkError,
            UpdateCheckResult.ParseError -> {
                // Silent failure on automatic check; retry backoff after 1 hour.
                lastFailedAutoCheckAtMs = System.currentTimeMillis()
            }
        }
    }

    private suspend fun resolveStoredManualResult(): UpdateCheckResult {
        val installedVersion = getInstalledVersionName()
        val cached = settingsDataStore.updateCachedReleaseFlow.first()
        val cachedAvailable = cached?.toUpdateAvailableIfNewer(installedVersion)
        if (cachedAvailable != null) {
            return cachedAvailable
        }
        val previous = _lastNetworkResult.value
        if (previous != null) {
            if (previous is UpdateCheckResult.UpdateAvailable) {
                return if ((UpdateVersionComparator.compare(previous.version, installedVersion) ?: 0) > 0) {
                    previous
                } else {
                    UpdateCheckResult.UpToDate
                }
            }
            return previous
        }
        return UpdateCheckResult.UpToDate
    }

    private suspend fun tryPresentPendingAutoPopup(isPopupBlocked: Boolean) {
        val candidate = pendingAutoPopupUpdate ?: return
        val elapsedSinceLaunch = SystemClock.elapsedRealtime() - launchElapsedMs
        if (!UpdateCheckGate.isWithinAutoPopupLaunchWindow(elapsedSinceLaunch)) {
            pendingAutoPopupUpdate = null
            autoPopupDeadlineJob?.cancel()
            return
        }
        if (isAutoPopupSuppressedForSession || !LilithApplication.isAppInForegroundFlow.value) {
            return
        }
        val autoEnabled = settingsDataStore.updateAutoCheckFlow.first()
        if (!autoEnabled) {
            pendingAutoPopupUpdate = null
            return
        }
        val installed = getInstalledVersionName()
        val skipped = settingsDataStore.updateSkippedVersionFlow.first()
        if ((UpdateVersionComparator.compare(candidate.version, installed) ?: 0) <= 0 ||
            UpdateVersionComparator.isSameVersion(candidate.version, skipped) ||
            candidate.version in shownAutoPopupVersionsThisProcess
        ) {
            pendingAutoPopupUpdate = null
            return
        }
        if (isPopupBlocked || _activeDialogUpdate.value != null) {
            val remainingMs = UpdateConstants.AUTO_POPUP_MAX_WAIT_AFTER_LAUNCH_MS - elapsedSinceLaunch
            scheduleAutoPopupDeadline(remainingMs)
            return
        }

        pendingAutoPopupUpdate = null
        autoPopupDeadlineJob?.cancel()
        shownAutoPopupVersionsThisProcess.add(candidate.version)
        _activeDialogUpdate.value = candidate
    }

    private fun scheduleAutoPopupDeadline(remainingMs: Long) {
        if (autoPopupDeadlineJob?.isActive == true) return
        autoPopupDeadlineJob = appScope.launch {
            if (remainingMs > 0L) {
                delay(remainingMs + 50L)
            }
            pendingAutoPopupUpdate = null
        }
    }

    private fun getOrStartNetworkCheck(installedVersion: String): Deferred<UpdateCheckResult> {
        synchronized(checkLock) {
            inFlightCheck?.takeIf { it.isActive }?.let { return it }
            _isChecking.value = true
            val deferred = appScope.async {
                try {
                    fetchLatestReleaseWithCache(installedVersion)
                } finally {
                    _isChecking.value = false
                    synchronized(checkLock) {
                        inFlightCheck = null
                    }
                }
            }
            inFlightCheck = deferred
            return deferred
        }
    }

    private suspend fun fetchLatestReleaseWithCache(installedVersion: String): UpdateCheckResult {
        val etag = settingsDataStore.updateETagFlow.first().trim()
        val cached = settingsDataStore.updateCachedReleaseFlow.first()
        return executeHttpGet(
            etagToSend = etag.takeIf { it.isNotEmpty() },
            cached = cached,
            installedVersion = installedVersion,
            allow304RetryWithoutEtag = true
        )
    }

    private suspend fun executeHttpGet(
        etagToSend: String?,
        cached: CachedReleaseData?,
        installedVersion: String,
        allow304RetryWithoutEtag: Boolean
    ): UpdateCheckResult {
        val fetchOutcome = withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            val completionHandle = coroutineContext[Job]?.invokeOnCompletion {
                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
            lastNetworkAttemptAtMs = System.currentTimeMillis()
            try {
                val url = URL(UpdateConstants.RELEASES_API_URL)
                if (!url.protocol.equals("https", ignoreCase = true)) {
                    return@withContext HttpFetchOutcome.Done(UpdateCheckResult.NetworkError)
                }
                val opened = (url.openConnection() as? HttpsURLConnection)
                    ?: return@withContext HttpFetchOutcome.Done(UpdateCheckResult.NetworkError)
                connection = opened
                opened.requestMethod = "GET"
                opened.connectTimeout = CONNECT_TIMEOUT_MS
                opened.readTimeout = READ_TIMEOUT_MS
                opened.useCaches = false
                opened.setRequestProperty("Accept", "application/vnd.github+json")
                opened.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
                opened.setRequestProperty("User-Agent", "LilithMV/$installedVersion")
                if (!etagToSend.isNullOrEmpty()) {
                    opened.setRequestProperty("If-None-Match", etagToSend)
                }

                val statusCode = opened.responseCode
                if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    return@withContext HttpFetchOutcome.NotModified
                }
                if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@withContext HttpFetchOutcome.Done(UpdateCheckResult.NoReleases)
                }
                if (statusCode == HTTP_TOO_MANY_REQUESTS) {
                    return@withContext HttpFetchOutcome.Done(UpdateCheckResult.RateLimited)
                }
                if (statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    val remaining = opened.getHeaderField("x-ratelimit-remaining")?.trim()
                    val res = if (remaining == "0") {
                        UpdateCheckResult.RateLimited
                    } else {
                        UpdateCheckResult.NetworkError
                    }
                    return@withContext HttpFetchOutcome.Done(res)
                }
                if (statusCode !in 200..299) {
                    return@withContext HttpFetchOutcome.Done(UpdateCheckResult.NetworkError)
                }

                val declaredLength = opened.contentLengthLong
                if (declaredLength > MAX_RESPONSE_BYTES) {
                    return@withContext HttpFetchOutcome.Done(UpdateCheckResult.ParseError)
                }

                val bodyString = opened.inputStream.use { stream ->
                    readLimitedUtf8(stream, MAX_RESPONSE_BYTES)
                } ?: return@withContext HttpFetchOutcome.Done(UpdateCheckResult.ParseError)

                val responseETag = opened.getHeaderField("ETag")?.trim().orEmpty()
                val parsed = parseReleaseJson(bodyString, installedVersion)
                HttpFetchOutcome.SuccessBody(parsed, responseETag)
            } catch (e: CancellationException) {
                throw e
            } catch (_: JSONException) {
                HttpFetchOutcome.Done(UpdateCheckResult.ParseError)
            } catch (_: IOException) {
                HttpFetchOutcome.Done(UpdateCheckResult.NetworkError)
            } catch (_: Exception) {
                HttpFetchOutcome.Done(UpdateCheckResult.NetworkError)
            } finally {
                completionHandle?.dispose()
                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
        }

        return when (fetchOutcome) {
            is HttpFetchOutcome.Done -> fetchOutcome.result
            is HttpFetchOutcome.SuccessBody -> {
                when (val parsed = fetchOutcome.parsed) {
                    is ParsedRelease.Valid -> {
                        settingsDataStore.saveUpdateCache(
                            etag = fetchOutcome.etag,
                            release = parsed.releaseData,
                            checkedAtMs = System.currentTimeMillis()
                        )
                        if (parsed.isNewer) {
                            parsed.releaseData.toUpdateAvailable()
                        } else {
                            UpdateCheckResult.UpToDate
                        }
                    }

                    ParsedRelease.Invalid -> UpdateCheckResult.ParseError
                }
            }

            HttpFetchOutcome.NotModified -> {
                if (cached != null) {
                    settingsDataStore.setUpdateLastCheckAt(System.currentTimeMillis())
                    cached.toUpdateAvailableIfNewer(installedVersion) ?: UpdateCheckResult.UpToDate
                } else if (allow304RetryWithoutEtag) {
                    settingsDataStore.clearUpdateETag()
                    executeHttpGet(
                        etagToSend = null,
                        cached = null,
                        installedVersion = installedVersion,
                        allow304RetryWithoutEtag = false
                    )
                } else {
                    UpdateCheckResult.NetworkError
                }
            }
        }
    }

    private fun readLimitedUtf8(stream: InputStream, maxBytes: Int): String? {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var totalBytes = 0
        while (true) {
            val read = stream.read(buffer)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun parseReleaseJson(
        bodyString: String,
        installedVersion: String
    ): ParsedRelease {
        return try {
            val json = JSONObject(bodyString)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) return ParsedRelease.Invalid

            val cmp = UpdateVersionComparator.compare(tagName, installedVersion)
                ?: return ParsedRelease.Invalid

            val rawBody = json.optString("body", "")
            val rawHtmlUrl = json.optString("html_url", "")
            val publishedAt = json.optString("published_at", "").trim()

            var apkUrl: String? = null
            var apkSize: Long? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val assetName = asset.optString("name", "").trim()
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        val rawDownloadUrl = asset.optString("browser_download_url", "").trim()
                        if (rawDownloadUrl.isNotEmpty()) {
                            apkUrl = UpdateUrlValidator.validateAssetDownloadUrl(rawDownloadUrl)
                        }
                        val size = asset.optLong("size", -1L)
                        if (size > 0L) {
                            apkSize = size
                        }
                        break
                    }
                }
            }

            val releaseData = CachedReleaseData(
                version = UpdateVersionComparator.normalizeDisplayVersion(tagName),
                notes = UpdateNotesFormatter.formatReleaseNotes(rawBody)
                    .take(UpdateConstants.MAX_NOTES_LENGTH),
                releaseUrl = UpdateUrlValidator.validateReleasePageUrl(rawHtmlUrl),
                apkUrl = apkUrl,
                apkSize = apkSize,
                publishedAt = publishedAt
            )
            ParsedRelease.Valid(releaseData = releaseData, isNewer = cmp > 0)
        } catch (_: JSONException) {
            ParsedRelease.Invalid
        } catch (_: Exception) {
            ParsedRelease.Invalid
        }
    }

    private fun CachedReleaseData.toUpdateAvailable(): UpdateCheckResult.UpdateAvailable {
        return UpdateCheckResult.UpdateAvailable(
            version = UpdateVersionComparator.normalizeDisplayVersion(version),
            notes = notes.take(UpdateConstants.MAX_NOTES_LENGTH),
            releaseUrl = UpdateUrlValidator.validateReleasePageUrl(releaseUrl),
            apkUrl = apkUrl?.let { UpdateUrlValidator.validateAssetDownloadUrl(it) },
            apkSize = apkSize,
            publishedAt = publishedAt
        )
    }

    private fun CachedReleaseData.toUpdateAvailableIfNewer(
        installedVersion: String
    ): UpdateCheckResult.UpdateAvailable? {
        val cmp = UpdateVersionComparator.compare(version, installedVersion) ?: return null
        return if (cmp > 0) toUpdateAvailable() else null
    }

    private sealed interface HttpFetchOutcome {
        data class Done(val result: UpdateCheckResult) : HttpFetchOutcome
        data class SuccessBody(val parsed: ParsedRelease, val etag: String) : HttpFetchOutcome
        data object NotModified : HttpFetchOutcome
    }

    private sealed interface ParsedRelease {
        data class Valid(val releaseData: CachedReleaseData, val isNewer: Boolean) : ParsedRelease
        data object Invalid : ParsedRelease
    }

    companion object {
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
