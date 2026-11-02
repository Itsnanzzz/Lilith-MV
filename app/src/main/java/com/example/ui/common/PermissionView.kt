package com.example.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicTheme
import com.example.ui.theme.NeumorphicTokens
import com.example.ui.theme.neumorphic

@Composable
fun PermissionView(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphicTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Raised circular emblem with soft depth
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .neumorphic(
                        shape = CircleShape,
                        level = NeumorphLevel.LARGE,
                        style = NeumorphStyle.RAISED,
                        lightShadowColorOverride = colors.lightShadow,
                        darkShadowColorOverride = colors.darkShadow,
                        backgroundColorOverride = colors.surface
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.permission_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.permission_description),
                fontSize = 14.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Raised Neumorphic Action Button
            val buttonStyle = if (isPressed) NeumorphStyle.PRESSED else NeumorphStyle.RAISED
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .neumorphic(
                        shape = RoundedCornerShape(NeumorphicTokens.RadiusLarge),
                        level = NeumorphLevel.MEDIUM,
                        style = buttonStyle,
                        lightShadowColorOverride = colors.lightShadow,
                        darkShadowColorOverride = colors.darkShadow,
                        backgroundColorOverride = colors.surface
                    )
                    .clip(RoundedCornerShape(NeumorphicTokens.RadiusLarge))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onRequestPermission
                    )
                    .testTag("grant_permission_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = stringResource(R.string.permission_grant),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.accent
                    )
                }
            }
        }
    }
}
