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

/**
 * App color palette - centralized color definitions
 * Based on modern banking app design (similar to Revolut, N26)
 */
object AppColors {
    // Main background
    val MainBackground = Color(0xFF0F2A44)  // Dark navy blue

    // Header colors
    val HeaderBackground = Color(0xFF0F2A44)  // Dark navy blue
    val HeaderText = Color(0xFFFFFFFF)  // White
    val HeaderIcons = Color(0xFFE5E7EB)  // Light gray
    val HeaderSeparator = Color(0xFF1E3A5F)  // Slightly lighter navy
    val HeaderSecondaryText = Color(0xFF94A3B8)  // Gray for labels like "Income", "Expenses"

    // Footer (Bottom Navigation) colors
    val FooterBackground = Color(0xFFFFFFFF)  // White
    val FooterTopLine = Color(0xFFE5E7EB)  // Light gray border
    val FooterActiveIcon = Color(0xFF2563EB)  // Blue
    val FooterActiveText = Color(0xFF2563EB)  // Blue
    val FooterInactiveIcon = Color(0xFF94A3B8)  // Gray
    val FooterInactiveText = Color(0xFF94A3B8)  // Gray

    // Data colors
    val Income = Color(0xFF16A34A)  // Green
    val Expenses = Color(0xFFDC2626)  // Red

    // Button colors
    val ButtonPrimary = Color(0xFF2563EB)  // Blue
    val ButtonPrimaryText = Color(0xFFFFFFFF)  // White
    val ButtonDisabled = Color(0xFFCBD5E1)  // Light gray
    val ButtonDisabledText = Color(0xFF94A3B8)  // Gray

    // Card/Block colors
    val CardBackground = Color(0xFFFFFFFF)  // White
    val CardBorder = Color(0xFFE5E7EB)  // Light gray
    val CardPrimaryText = Color(0xFF0F172A)  // Very dark blue/black
    val CardSecondaryText = Color(0xFF475569)  // Dark gray

    // Accent/Primary color
    val Primary = Color(0xFF2563EB)  // Blue
    val PrimaryLight = Color(0xFF3B82F6)  // Lighter blue
    val PrimaryDark = Color(0xFF1D4ED8)  // Darker blue
}

// Light theme colors - with new color scheme
private val LightColorScheme = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),  // Light blue container
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E293B),
    tertiary = Color(0xFF7C3AED),  // Purple for accents
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    error = AppColors.Expenses,
    errorContainer = Color(0xFFFEE2E2),
    onError = Color.White,
    onErrorContainer = Color(0xFF7F1D1D),
    background = AppColors.MainBackground,
    onBackground = Color.White,
    surface = AppColors.CardBackground,
    onSurface = AppColors.CardPrimaryText,
    surfaceVariant = Color(0xFFF1F5F9),  // Light gray for card variants
    onSurfaceVariant = AppColors.CardSecondaryText,
    outline = AppColors.CardBorder,
    inverseOnSurface = Color(0xFF1E293B),
    inverseSurface = Color(0xFFF1F5F9),
    inversePrimary = Color(0xFF93C5FD)
)

// Dark theme colors - keeping dark mode option available
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),  // Light blue for dark mode
    onPrimary = Color(0xFF1E3A8A),
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF1E293B),
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF4C1D95),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFEDE9FE),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF7F1D1D),
    onError = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF0F172A),  // Dark blue background
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),  // Dark surface
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    inverseOnSurface = Color(0xFF0F172A),
    inverseSurface = Color(0xFFF1F5F9),
    inversePrimary = Color(0xFF2563EB)
)

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
