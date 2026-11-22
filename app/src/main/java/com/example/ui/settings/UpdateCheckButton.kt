package com.example.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.UpdateCheckResult
import com.example.data.UpdateUiState
import com.example.ui.theme.AppThemeTokens

/**
 * Shared tonal-circle Update Check button for the Music and Video tab search rows.
 * - 48dp touch target, 40dp tonal surfaceMuted circle, 20dp stroke icon or 20dp spinner.
 * - Disabled while checking, swapping contentDescription to "Checking for updates".
 * - Shows an 8dp accent dot at top-right when an unskipped update is available.
 */
@Composable
fun UpdateCheckSearchRowButton(
    uiState: UpdateUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "update_check_search_button"
) {
    val colors = AppThemeTokens.colors
    val buttonContentDesc = stringResource(
        if (uiState.isChecking) R.string.update_checking_desc else R.string.update_check_button
    )
    val badgeDesc = stringResource(R.string.update_badge_desc)

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                enabled = !uiState.isChecking,
                role = Role.Button,
                onClickLabel = buttonContentDesc,
                onClick = onClick
            )
            .semantics { contentDescription = buttonContentDesc }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = colors.foregroundAccent
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_update_check),
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (uiState.hasAvailableBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.foregroundAccent)
                    .semantics { contentDescription = badgeDesc }
                    .testTag("${testTag}_badge")
            )
        }
    }
}

fun resolveManualUpdateSnackbarMessage(context: Context, result: UpdateCheckResult): String? {
    return when (result) {
        is UpdateCheckResult.UpdateAvailable -> null
        UpdateCheckResult.UpToDate -> context.getString(R.string.update_status_up_to_date)
        UpdateCheckResult.NoReleases -> context.getString(R.string.update_status_no_releases)
        UpdateCheckResult.RateLimited -> context.getString(R.string.update_status_rate_limited)
        UpdateCheckResult.NetworkError,
        UpdateCheckResult.ParseError -> context.getString(R.string.update_status_error)
    }
}
