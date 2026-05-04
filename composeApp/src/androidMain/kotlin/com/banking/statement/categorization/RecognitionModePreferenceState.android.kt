package com.banking.statement.categorization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.banking.statement.AppPreferences
import org.koin.compose.koinInject

@Composable
actual fun rememberRecognitionModePreferenceState(): RecognitionModePreferenceState {
    val preferences: AppPreferences = koinInject()
    return remember(preferences) {
        RecognitionModePreferenceState(
            initialMode = recognitionModeFromName(preferences.getRecognitionModeName()),
            persistMode = { mode -> preferences.setRecognitionModeName(mode.name) }
        )
    }
}
