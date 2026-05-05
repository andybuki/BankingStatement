package com.banking.statement.categorization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class RecognitionModePreferenceState(
    initialMode: RecognitionMode,
    private val persistMode: (RecognitionMode) -> Unit
) {
    var mode: RecognitionMode by mutableStateOf(initialMode)
        private set

    fun update(newMode: RecognitionMode) {
        mode = newMode
        persistMode(newMode)
    }
}

@Composable
expect fun rememberRecognitionModePreferenceState(): RecognitionModePreferenceState

fun recognitionModeFromName(modeName: String?): RecognitionMode {
    return RecognitionMode.entries.find { it.name == modeName } ?: RecognitionMode.SAFE
}
