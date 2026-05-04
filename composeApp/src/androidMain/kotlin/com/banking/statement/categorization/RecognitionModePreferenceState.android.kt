package com.banking.statement.categorization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.banking.statement.AppPreferences

@Composable
actual fun rememberRecognitionModePreferenceState(): RecognitionModePreferenceState {
    val context = LocalContext.current
    return remember(context) {
        val preferences = AppPreferences(context)
        RecognitionModePreferenceState(
            initialMode = recognitionModeFromName(preferences.getRecognitionModeName()),
            persistMode = { mode -> preferences.setRecognitionModeName(mode.name) }
        )
    }
}
