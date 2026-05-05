package com.banking.statement.categorization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.banking.statement.AppPreferences
import org.koin.mp.KoinPlatform

@Composable
actual fun rememberRecognitionModePreferenceState(): RecognitionModePreferenceState {
    return remember {
        val preferences = KoinPlatform.getKoin().get<AppPreferences>()
        RecognitionModePreferenceState(
            initialMode = recognitionModeFromName(preferences.getRecognitionModeName()),
            persistMode = { mode -> preferences.setRecognitionModeName(mode.name) }
        )
    }
}
