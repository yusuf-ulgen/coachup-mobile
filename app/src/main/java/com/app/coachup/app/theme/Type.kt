package com.app.coachup.app.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.app.coachup.app.R

/**
 * Inter font family – smooth, modern typeface similar to iOS San Francisco.
 */
val InterFontFamily = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * App typography scale – mirrors iOS AppTypography exactly.
 *
 * iOS → Android mapping:
 *   title      25 Medium  → titleLarge   25sp  W500
 *   subtitle   20 Medium  → titleMedium  20sp  W500
 *   statusBar  17 SemiBold→ titleSmall   17sp  W600
 *   normal     16 Regular → bodyLarge    16sp  W400
 *   normalMedium 16 Medium→ bodyMedium   16sp  W500
 *   small      14 Regular → bodySmall    14sp  W400
 *   timestamp  11 Regular → labelSmall   11sp  W400
 */

// ---------------------------------------------------------------------------
// Named text-style constants  (for direct use in composables)
// ---------------------------------------------------------------------------

/** iOS: AppTypography.title  – 25sp Medium */
val AppTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 25.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 32.sp,
    letterSpacing = 0.sp
)

/** iOS: AppTypography.subtitle  – 20sp Medium */
val AppSubtitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 20.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)

/** iOS: AppTypography.statusBar  – 17sp SemiBold */
val AppStatusBarStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 17.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)

/** iOS: AppTypography.normal  – 16sp Regular */
val AppNormalStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 16.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp
)

/** iOS: AppTypography.normalMedium  – 16sp Medium */
val AppNormalMediumStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 16.sp,
    fontWeight = FontWeight.Medium,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp
)

/** iOS: AppTypography.small  – 14sp Regular */
val AppSmallStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 14.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp
)

/** Timestamp label  – 11sp Regular  (used in MessageBubble) */
val AppTimestampStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 11.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 16.sp,
    letterSpacing = 0.sp
)

/** Camera permission description – 15sp Regular */
val AppDescriptionStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 15.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 22.sp,
    letterSpacing = 0.15.sp
)

/** Camera permission title – 22sp SemiBold */
val AppPermissionTitleStyle = TextStyle(
    fontFamily = InterFontFamily,
    fontSize = 22.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)

// ---------------------------------------------------------------------------
// Material 3 Typography  (wires named styles into the M3 slot system)
// ---------------------------------------------------------------------------

val AppTypography = Typography(
    // Display / Headline – not directly used in iOS design but populated for
    // any standard M3 consumer (e.g. TopAppBar).
    displayLarge = AppTitleStyle.copy(fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = AppTitleStyle.copy(fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = AppTitleStyle.copy(fontSize = 36.sp, lineHeight = 44.sp),

    headlineLarge = AppTitleStyle.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = AppTitleStyle.copy(fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = AppSubtitleStyle.copy(fontSize = 24.sp, lineHeight = 32.sp),

    // Title slots
    titleLarge = AppTitleStyle,        // 25sp  Medium  – iOS title
    titleMedium = AppSubtitleStyle,    // 20sp  Medium  – iOS subtitle
    titleSmall = AppStatusBarStyle,    // 17sp  SemiBold – iOS statusBar

    // Body slots
    bodyLarge = AppNormalStyle,        // 16sp  Regular – iOS normal
    bodyMedium = AppNormalMediumStyle, // 16sp  Medium  – iOS normalMedium
    bodySmall = AppSmallStyle,         // 14sp  Regular – iOS small

    // Label slots
    labelLarge = AppSmallStyle.copy(fontWeight = FontWeight.Medium),
    labelMedium = AppTimestampStyle.copy(fontSize = 12.sp),
    labelSmall = AppTimestampStyle,    // 11sp  Regular – timestamp
)
