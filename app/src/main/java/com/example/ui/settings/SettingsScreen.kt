package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ThemeMode
import com.example.ui.theme.AppChip
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppSecondaryButton
import com.example.ui.theme.AppSegmentedControl
import com.example.ui.theme.AppSwitch
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import com.example.ui.theme.LilithTheme
import com.example.ui.video.SubtitleSettingsDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.example.data.AppAccent
import com.example.ui.navigation.LocalBottomContentPadding
import androidx.compose.ui.unit.Dp

/**
 * Clean, flat Settings Screen matching reference design:
 * - Grouped sections inside surfaceMuted cards (16dp radius)
 * - Modern AppSegmentedControl for Theme mode
 * - Accent Color card with 7 circular swatches
 * - Flat AppSwitch components for playback behavior toggles
 * - AppChip row for subtitle language preferences
 * - Clean AppSecondaryButton for subtitle customization dialog
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = LocalBottomContentPadding.current
) {
    val colors = AppThemeTokens.colors
    val themeMode by viewModel.themeMode.collectAsState()
    val appAccent by viewModel.appAccent.collectAsState()
    val gapless by viewModel.gaplessPlayback.collectAsState()
    val pauseOnHeadset by viewModel.pauseOnHeadsetUnplug.collectAsState()
    val subtitleLang by viewModel.preferredSubtitleLanguage.collectAsState()
    val subtitleSettings by viewModel.subtitleSettings.collectAsState()

    var showSubtitleStyleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppTokens.ScreenHorizontalPadding, vertical = 16.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = AppTypography.screenTitle,
            color = colors.textPrimary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Theme Mode Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTokens.Radius16))
                .background(colors.surfaceMuted)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = AppTypography.listTitle,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val themeOptions = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)

                val systemLabel = stringResource(R.string.settings_theme_system)
                val darkLabel = stringResource(R.string.settings_theme_dark)
                val lightLabel = stringResource(R.string.settings_theme_light)

                AppSegmentedControl(
                    items = themeOptions,
                    selectedItem = themeMode,
                    onItemSelected = { viewModel.setThemeMode(it) },
                    labelProvider = { mode ->
                        when (mode) {
                            ThemeMode.SYSTEM -> systemLabel
                            ThemeMode.DARK -> darkLabel
                            ThemeMode.LIGHT -> lightLabel
                        }
                    },
                    itemTestTagProvider = { "theme_option_${it.name.lowercase()}" },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Accent Color Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTokens.Radius16))
                .background(colors.surfaceMuted)
                .padding(16.dp)
                .testTag("accent_color_card")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_accent_color),
                        style = AppTypography.listTitle,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val accentRows = AppAccent.entries.chunked(4)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    accentRows.forEach { rowAccents ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowAccents.forEach { accent ->
                                val isSelected = appAccent == accent
                                val accentColors = AppTokens.resolveAccentColors(accent, colors.isDark)
                                val swatchColor = accentColors.accent
                                val onAccentColor = accentColors.onAccent
                                val isWhiteSwatch = accent == AppAccent.WHITE
                                val accentLabel = when (accent) {
                                    AppAccent.WHITE -> stringResource(R.string.accent_white)
                                    AppAccent.BLUE -> stringResource(R.string.accent_blue)
                                    AppAccent.GREEN -> stringResource(R.string.accent_green)
                                    AppAccent.YELLOW -> stringResource(R.string.accent_yellow)
                                    AppAccent.RED -> stringResource(R.string.accent_red)
                                    AppAccent.ORANGE -> stringResource(R.string.accent_orange)
                                    AppAccent.PURPLE -> stringResource(R.string.accent_purple)
                                    AppAccent.CYAN -> stringResource(R.string.accent_cyan)
                                }
                                val swatchContentDesc = if (isSelected) {
                                    stringResource(R.string.accent_swatch_selected, accentLabel)
                                } else {
                                    accentLabel
                                }

                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            role = Role.RadioButton,
                                            onClick = { viewModel.setAccentColor(accent) }
                                        )
                                        .semantics {
                                            this.selected = isSelected
                                            this.contentDescription = swatchContentDesc
                                        }
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 2.5.dp,
                                                    color = swatchColor,
                                                    shape = CircleShape
                                                )
                                            } else Modifier
                                        )
                                        .testTag("accent_swatch_${accent.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(swatchColor)
                                            .then(
                                                if (isWhiteSwatch) {
                                                    Modifier.border(
                                                        width = 1.dp,
                                                        color = if (!colors.isDark) colors.disabled else colors.hairline,
                                                        shape = CircleShape
                                                    )
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = onAccentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val selectedAccentName = when (appAccent) {
                    AppAccent.WHITE -> stringResource(R.string.accent_white)
                    AppAccent.BLUE -> stringResource(R.string.accent_blue)
                    AppAccent.GREEN -> stringResource(R.string.accent_green)
                    AppAccent.YELLOW -> stringResource(R.string.accent_yellow)
                    AppAccent.RED -> stringResource(R.string.accent_red)
                    AppAccent.ORANGE -> stringResource(R.string.accent_orange)
                    AppAccent.PURPLE -> stringResource(R.string.accent_purple)
                    AppAccent.CYAN -> stringResource(R.string.accent_cyan)
                }

                Text(
                    text = selectedAccentName,
                    style = AppTypography.caption.copy(fontSize = 13.sp),
                    color = colors.textSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Playback Behavior (Gapless & Pause on Headset Unplug)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTokens.Radius16))
                .background(colors.surfaceMuted)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Gapless Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_gapless),
                                style = AppTypography.listTitle.copy(fontSize = 14.sp),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_gapless_desc),
                                style = AppTypography.listSubtitle,
                                color = colors.textSecondary
                            )
                        }
                    }
                    AppSwitch(
                        checked = gapless,
                        onCheckedChange = { viewModel.setGaplessPlayback(it) },
                        modifier = Modifier.testTag("gapless_playback_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.hairline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Pause on Headset Unplug
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_pause_on_headset),
                                style = AppTypography.listTitle.copy(fontSize = 14.sp),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_pause_on_headset_desc),
                                style = AppTypography.listSubtitle,
                                color = colors.textSecondary
                            )
                        }
                    }
                    AppSwitch(
                        checked = pauseOnHeadset,
                        onCheckedChange = { viewModel.setPauseOnHeadsetUnplug(it) },
                        modifier = Modifier.testTag("pause_headset_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Subtitle Preferences Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTokens.Radius16))
                .background(colors.surfaceMuted)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.subtitle_settings),
                        style = AppTypography.listTitle,
                        color = colors.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.settings_preferred_subtitle_lang),
                    style = AppTypography.label,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppChip(
                        selected = subtitleLang == "auto",
                        onClick = { viewModel.setPreferredSubtitleLanguage("auto") },
                        text = stringResource(R.string.subtitle_lang_auto),
                        modifier = Modifier.weight(1f)
                    )
                    AppChip(
                        selected = subtitleLang == "id",
                        onClick = { viewModel.setPreferredSubtitleLanguage("id") },
                        text = stringResource(R.string.lang_indonesian),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppChip(
                        selected = subtitleLang == "en",
                        onClick = { viewModel.setPreferredSubtitleLanguage("en") },
                        text = stringResource(R.string.lang_english),
                        modifier = Modifier.weight(1f)
                    )
                    AppChip(
                        selected = subtitleLang == "ja",
                        onClick = { viewModel.setPreferredSubtitleLanguage("ja") },
                        text = stringResource(R.string.subtitle_lang_japanese),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                AppSecondaryButton(
                    text = stringResource(R.string.subtitle_customize_style),
                    onClick = { showSubtitleStyleDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. About App Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTokens.Radius16))
                .background(colors.surfaceMuted)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_about),
                            style = AppTypography.listTitle,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = AppTypography.caption,
                            color = colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.settings_app_desc),
                    style = AppTypography.listSubtitle,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp + bottomPadding))
    }

    if (showSubtitleStyleDialog) {
        SubtitleSettingsDialog(
            settings = subtitleSettings,
            onSave = { viewModel.updateSubtitleSettings(it) },
            onDismiss = { showSubtitleStyleDialog = false }
        )
    }
}

// =========================================================================
// PREVIEWS FOR ACCENT COLORS
// =========================================================================

@Preview(name = "All Accents Sample - Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewAllAccentsLight() {
    LilithTheme(darkTheme = false, appAccent = AppAccent.BLUE) {
        AllAccentsSampleDemo(darkTheme = false)
    }
}

@Preview(name = "All Accents Sample - Dark", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun PreviewAllAccentsDark() {
    LilithTheme(darkTheme = true, appAccent = AppAccent.BLUE) {
        AllAccentsSampleDemo(darkTheme = true)
    }
}

@Composable
private fun AllAccentsSampleDemo(darkTheme: Boolean) {
    val colors = AppThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppAccent.entries.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { accent ->
                    LilithTheme(darkTheme = darkTheme, appAccent = accent) {
                        val themed = AppThemeTokens.colors
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(themed.accent)
                                .then(
                                    if (accent == AppAccent.WHITE) {
                                        Modifier.border(1.dp, themed.hairline, CircleShape)
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = themed.onAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(text = "Selected", selected = true, onClick = {})
            AppChip(text = "Normal", selected = false, onClick = {})
        }
        AppPrimaryButton(text = "Play Music", onClick = {})
        AppSwitch(checked = true, onCheckedChange = {})
    }
}
