package com.bankwise.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIScreen
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle

/**
 * iOS implementation of theme preferences using NSUserDefaults
 */
actual class ThemePreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getThemeMode(): ThemeMode {
        val value = defaults.stringForKey(KEY_THEME_MODE)
        return if (value != null) {
            try {
                ThemeMode.valueOf(value)
            } catch (e: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        } else {
            ThemeMode.SYSTEM
        }
    }

    actual fun setThemeMode(mode: ThemeMode) {
        defaults.setObject(mode.name, KEY_THEME_MODE)
    }

    companion object {
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

/**
 * iOS implementation of system dark theme check
 */
@Composable
actual fun isSystemInDarkThemeCompat(): Boolean {
    return remember {
        val traitCollection = UIScreen.mainScreen.traitCollection
        traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
    }
}
