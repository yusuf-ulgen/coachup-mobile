package com.app.coachup.app.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand / Primary  (invariant across light & dark)
// ---------------------------------------------------------------------------

/** Primary brand color – #FF6047 */
val Primary = Color(0xFFFF6047)

/** 10 % tinted background for primary-accented surfaces */
val PrimaryLight = Color(0x1AFF6047) // FF6047 @ 10 % alpha

// ---------------------------------------------------------------------------
// Neutral palette  (raw hex values; used to build semantic tokens below)
// ---------------------------------------------------------------------------

/** Near-black used as the default text / foreground in light mode */
val Black100 = Color(0xFF191717)

/** 50 % opacity variant of Black100 – used for secondary text in light mode */
val Black50 = Color(0x80191717)

/** 5 % opacity variant of Black100 – used for very subtle backgrounds */
val Black5 = Color(0x0D191717)

/** Warm off-white used as the default text / foreground in dark mode */
val White100 = Color(0xFFFAF9F8)

/** 50 % opacity variant of White100 */
val White50 = Color(0x80FAF9F8)

/** Pure white */
val AllWhite = Color(0xFFFFFFFF)

// ---------------------------------------------------------------------------
// Background / Surface tokens
// ---------------------------------------------------------------------------

/** App background – light mode */
val BackgroundLight = Color(0xFFFAF9F8)

/** App background – dark mode */
val BackgroundDark = Color(0xFF191717)

/** Card / elevated surface – light mode */
val CardBackgroundLight = Color(0xFFFFFFFF)

/** Card / elevated surface – dark mode */
val CardBackgroundDark = Color(0xFF2C2C2C)

// ---------------------------------------------------------------------------
// Text tokens
// ---------------------------------------------------------------------------

/** Primary text – light mode */
val TextLight = Color(0xFF191717)

/** Primary text – dark mode */
val TextDark = Color(0xFFFAF9F8)

/** Secondary text – light mode  (50 % Black100) */
val TextSecondaryLight = Color(0x80191717)

/** Secondary text – dark mode  (60 % White100) */
val TextSecondaryDark = Color(0x99FAF9F8)

// ---------------------------------------------------------------------------
// Border / Stroke tokens
// ---------------------------------------------------------------------------

/** Border / divider – light mode */
val BorderLight = Color(0xFFBFAFA4)

/** Border / divider – dark mode */
val BorderDark = Color(0xFF444444)

/** Passive (inactive) stroke – invariant */
val StrokePassive = Color(0xFFBFAFA4)

// ---------------------------------------------------------------------------
// Purple accent palette
// ---------------------------------------------------------------------------

val Purple100 = Color(0xFF231C33)
val PurpleSecondary = Color(0xFF352C4C)

// ---------------------------------------------------------------------------
// Status / utility  (standard Material colors kept as named constants)
// ---------------------------------------------------------------------------

val ErrorRed = Color(0xFFB00020)
val WarningOrange = Color(0xFFFF9800)
val SuccessGreen = Color(0xFF4CAF50)
val InfoBlue = Color(0xFF2196F3)
