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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.ThemeMode
import com.example.ui.theme.LocalNeumorphicColors
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicCard
import com.example.ui.theme.NeumorphicChip
import com.example.ui.theme.NeumorphicSwitch
import com.example.ui.theme.neumorphic
import com.example.ui.video.SubtitleSettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val nc = LocalNeumorphicColors.current
    val themeMode by viewModel.themeMode.collectAsState()
    val gapless by viewModel.gaplessPlayback.collectAsState()
    val pauseOnHeadset by viewModel.pauseOnHeadsetUnplug.collectAsState()
    val subtitleLang by viewModel.preferredSubtitleLanguage.collectAsState()
    val subtitleSettings by viewModel.subtitleSettings.collectAsState()

    var showSubtitleStyleDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(nc.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = nc.textPrimary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 1. Theme Mode Card
        NeumorphicCard(
            shape = RoundedCornerShape(18.dp),
            level = NeumorphLevel.SMALL,
            style = NeumorphStyle.RAISED,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .neumorphic(
                                shape = CircleShape,
                                level = NeumorphLevel.SMALL,
                                style = NeumorphStyle.RAISED
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = nc.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = nc.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeumorphicChip(
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        label = { Text(stringResource(R.string.settings_theme_system), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicChip(
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        label = { Text(stringResource(R.string.settings_theme_dark), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicChip(
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        label = { Text(stringResource(R.string.settings_theme_light), fontSize = 12.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Playback Behavior (Gapless & Pause on Headset Unplug)
        NeumorphicCard(
            shape = RoundedCornerShape(18.dp),
            level = NeumorphLevel.SMALL,
            style = NeumorphStyle.RAISED,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Gapless Playback
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .neumorphic(
                                    shape = CircleShape,
                                    level = NeumorphLevel.SMALL,
                                    style = NeumorphStyle.RAISED
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = nc.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_gapless),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = nc.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Transisi audio tanpa henti",
                                style = MaterialTheme.typography.bodySmall,
                                color = nc.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    NeumorphicSwitch(
                        checked = gapless,
                        onCheckedChange = { viewModel.setGaplessPlayback(it) },
                        modifier = Modifier.testTag("gapless_playback_switch")
                    )
                }

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
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .neumorphic(
                                    shape = CircleShape,
                                    level = NeumorphLevel.SMALL,
                                    style = NeumorphStyle.RAISED
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = nc.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.settings_pause_on_headset),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = nc.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Jeda saat earphone terputus",
                                style = MaterialTheme.typography.bodySmall,
                                color = nc.textSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    NeumorphicSwitch(
                        checked = pauseOnHeadset,
                        onCheckedChange = { viewModel.setPauseOnHeadsetUnplug(it) },
                        modifier = Modifier.testTag("pause_headset_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Subtitle Preferences
        NeumorphicCard(
            shape = RoundedCornerShape(18.dp),
            level = NeumorphLevel.SMALL,
            style = NeumorphStyle.RAISED,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .neumorphic(
                                shape = CircleShape,
                                level = NeumorphLevel.SMALL,
                                style = NeumorphStyle.RAISED
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = nc.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.subtitle_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = nc.textPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.settings_preferred_subtitle_lang),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = nc.textSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeumorphicChip(
                        selected = subtitleLang == "auto",
                        onClick = { viewModel.setPreferredSubtitleLanguage("auto") },
                        label = { Text("Otomatis", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicChip(
                        selected = subtitleLang == "id",
                        onClick = { viewModel.setPreferredSubtitleLanguage("id") },
                        label = { Text("Indonesia", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeumorphicChip(
                        selected = subtitleLang == "en",
                        onClick = { viewModel.setPreferredSubtitleLanguage("en") },
                        label = { Text("English", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                    NeumorphicChip(
                        selected = subtitleLang == "ja",
                        onClick = { viewModel.setPreferredSubtitleLanguage("ja") },
                        label = { Text("日本語", fontSize = 11.5.sp) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                NeumorphicCard(
                    shape = RoundedCornerShape(12.dp),
                    level = NeumorphLevel.SMALL,
                    style = NeumorphStyle.RAISED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSubtitleStyleDialog = true }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Kustomisasi Gaya Subtitle",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = nc.accent
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. About App Card
        NeumorphicCard(
            shape = RoundedCornerShape(18.dp),
            level = NeumorphLevel.SMALL,
            style = NeumorphStyle.RAISED,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .neumorphic(
                                shape = CircleShape,
                                level = NeumorphLevel.SMALL,
                                style = NeumorphStyle.RAISED
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = nc.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_about),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = nc.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_version),
                            style = MaterialTheme.typography.bodySmall,
                            color = nc.textSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.settings_app_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = nc.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showSubtitleStyleDialog) {
        SubtitleSettingsDialog(
            settings = subtitleSettings,
            onSave = { viewModel.updateSubtitleSettings(it) },
            onDismiss = { showSubtitleStyleDialog = false }
        )
    }
}
