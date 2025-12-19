package com.banking.statement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.banking.statement.ui.theme.ThemeMode
import com.banking.statement.ui.theme.ThemePreferences

fun MainViewController() = ComposeUIViewController {
    val themePreferences = remember { ThemePreferences() }
    var currentThemeMode by remember { mutableStateOf(themePreferences.getThemeMode()) }

    App(
        themeMode = currentThemeMode,
        onThemeModeChange = { mode ->
            currentThemeMode = mode
            themePreferences.setThemeMode(mode)
        }
    )
}
