package com.banking.statement.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

/**
 * Theme mode options
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

// Light theme colors
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF545F71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF111C2B),
    tertiary = Color(0xFF6D5E7A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6E1FF),
    onTertiaryContainer = Color(0xFF271B33),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onError = Color.White,
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFBFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    inverseOnSurface = Color(0xFFF1F0F4),
    inverseSurface = Color(0xFF2F3033),
    inversePrimary = Color(0xFFA0CAFD)
)

// Dark theme colors - using black/raven color scheme
// Footer/Header: Pure black (0xFF000000)
// Main content area: Jet black/Raven (0xFF1A1A1A / 0xFF212121)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA0CAFD),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBCC7DC),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFDAC5E8),
    onTertiary = Color(0xFF3D2F4A),
    tertiaryContainer = Color(0xFF554662),
    onTertiaryContainer = Color(0xFFF6E1FF),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF212121),  // Raven/jet black for main content
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF000000),     // Pure black for header/footer
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2D2D2D),  // Slightly lighter for cards
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8D9199),
    inverseOnSurface = Color(0xFF1A1C1E),
    inverseSurface = Color(0xFFE3E2E6),
    inversePrimary = Color(0xFF1565C0)
)

// Custom colors for header/footer bar (same as navigation surface)
val NavBarColor = Color(0xFF000000)  // Pure black for navigation bar

/**
 * Theme preference storage - platform-specific implementation
 */
expect class ThemePreferences {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
}

/**
 * Composition local for theme mode
 */
val LocalThemeMode = compositionLocalOf { ThemeMode.SYSTEM }

/**
 * Get system dark theme status - platform-specific
 */
@Composable
expect fun isSystemInDarkThemeCompat(): Boolean

/**
 * Banking Statement theme wrapper
 */
@Composable
fun BankingStatementTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkThemeCompat()

    val isDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemIsDark
    }

    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
