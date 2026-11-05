package com.example.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

// Define ONE font family object using BUNDLED font files in res/font
val AppFontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

object AppTypography {
    val screenTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    )
    val nowPlayingTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    )
    val collectionHeaderTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    )
    val listTitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )
    val listSubtitle = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
    val body = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val label = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    val badge = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
    val navLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
    val navLabelSelected = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
    val timeLabel = TextStyle(
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = "tnum"
    )
    val caption = timeLabel
}

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = AppTypography.collectionHeaderTitle,
    headlineSmall = AppTypography.nowPlayingTitle,
    titleLarge = AppTypography.screenTitle,
    titleMedium = AppTypography.listTitle,
    titleSmall = AppTypography.label,
    bodyLarge = AppTypography.body,
    bodyMedium = AppTypography.listSubtitle,
    bodySmall = AppTypography.caption,
    labelLarge = AppTypography.body.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = AppTypography.label,
    labelSmall = AppTypography.badge
)

// TEMPORARY TYPOGRAPHY VERIFICATION PREVIEW (Can be deleted later)
@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun TypographyVerificationPreview() {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val sampleText = "The quick brown fox jumps over the lazy dog 12345"
        val weights = listOf(
            FontWeight.Normal to "400 Regular",
            FontWeight.Medium to "500 Medium",
            FontWeight.SemiBold to "600 SemiBold",
            FontWeight.Bold to "700 Bold"
        )
        weights.forEach { (weight, labelText) ->
            Text(
                text = "Manrope ($labelText): $sampleText",
                fontFamily = AppFontFamily,
                fontWeight = weight,
                fontSize = 13.sp
            )
            Text(
                text = "System ($labelText): $sampleText",
                fontFamily = FontFamily.Default,
                fontWeight = weight,
                fontSize = 13.sp
            )
            Text(
                text = "Serif ($labelText): $sampleText",
                fontFamily = FontFamily.Serif,
                fontWeight = weight,
                fontSize = 13.sp
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}


