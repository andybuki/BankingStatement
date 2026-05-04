package com.banking.statement.categorization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.banking.statement.AppPreferences

@Composable
actual fun rememberRecognitionModePreferenceState(): RecognitionModePreferenceState {
    return remember {
        val preferences = AppPreferences()
        RecognitionModePreferenceState(
            initialMode = recognitionModeFromName(preferences.getRecognitionModeName()),
            persistMode = { mode -> preferences.setRecognitionModeName(mode.name) }
        )
    }
}
