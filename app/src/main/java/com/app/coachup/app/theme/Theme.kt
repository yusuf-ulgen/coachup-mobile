package com.app.coachup.app.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// ---------------------------------------------------------------------------
// DataStore extension property – single instance per process
// ---------------------------------------------------------------------------

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "coachup_theme_prefs"
)

// ---------------------------------------------------------------------------
// AppTheme enum – mirrors iOS AppTheme
// ---------------------------------------------------------------------------

enum class AppTheme(val rawValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    val displayName: String
        get() = when (this) {
            SYSTEM -> "Sistem"
            LIGHT -> "Açık"
            DARK -> "Koyu"
        }

    companion object {
        fun fromRawValue(value: String): AppTheme =
            entries.firstOrNull { it.rawValue == value } ?: SYSTEM
    }
}

// ---------------------------------------------------------------------------
// ThemeManager – DataStore-backed singleton, mirrors iOS ThemeManager
// ---------------------------------------------------------------------------

class ThemeManager(private val context: Context) {

    private val themeKey = stringPreferencesKey("app_theme")

    /** Emits the persisted [AppTheme] preference; defaults to [AppTheme.SYSTEM]. */
    val currentThemeFlow: Flow<AppTheme> = context.themeDataStore.data.map { prefs ->
        AppTheme.fromRawValue(prefs[themeKey] ?: AppTheme.SYSTEM.rawValue)
    }

    /** Persists the selected [theme] to DataStore. */
    suspend fun setTheme(theme: AppTheme) {
        context.themeDataStore.edit { prefs ->
            prefs[themeKey] = theme.rawValue
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

// ---------------------------------------------------------------------------
// Color schemes – hand-crafted from AppColors to match iOS exactly
// ---------------------------------------------------------------------------

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = AllWhite,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = Primary,

    secondary = Purple100,
    onSecondary = AllWhite,
    secondaryContainer = PurpleSecondary,
    onSecondaryContainer = White100,

    background = BackgroundLight,
    onBackground = TextLight,

    surface = CardBackgroundLight,
    onSurface = TextLight,
    surfaceVariant = Black5,
    onSurfaceVariant = TextSecondaryLight,

    outline = BorderLight,
    outlineVariant = StrokePassive,

    error = ErrorRed,
    onError = AllWhite,
)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = AllWhite,
    primaryContainer = PrimaryLight,
    onPrimaryContainer = White100,

    secondary = PurpleSecondary,
    onSecondary = AllWhite,
    secondaryContainer = Purple100,
    onSecondaryContainer = White100,

    background = BackgroundDark,
    onBackground = TextDark,

    surface = CardBackgroundDark,
    onSurface = TextDark,
    surfaceVariant = Color(0xFF2A2727),
    onSurfaceVariant = TextSecondaryDark,

    outline = BorderDark,
    outlineVariant = BorderDark,

    error = ErrorRed,
    onError = AllWhite,
)

// Helper alias so the compiler doesn't require an import of androidx.compose.ui.graphics.Color
// when referencing it inside this file.
private fun Color(value: Long) = androidx.compose.ui.graphics.Color(value)

// ---------------------------------------------------------------------------
// CoachUpTheme composable
// ---------------------------------------------------------------------------

/**
 * Root theme wrapper for all CoachUp screens.
 *
 * @param themeManager Injected [ThemeManager]; collects the persisted preference.
 * @param dynamicColor  When true and API >= 31, uses Material You dynamic colours.
 *                      Disabled by default so the brand palette always applies.
 * @param content       The composable content tree.
 */
@Composable
fun CoachUpTheme(
    themeManager: ThemeManager? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()

    val isDark = if (themeManager != null) {
        val storedTheme by themeManager.currentThemeFlow.collectAsState(initial = AppTheme.SYSTEM)
        when (storedTheme) {
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
            AppTheme.SYSTEM -> systemDark
        }
    } else {
        systemDark
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
