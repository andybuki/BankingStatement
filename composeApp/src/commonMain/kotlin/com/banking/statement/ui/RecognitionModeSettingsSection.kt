package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.categorization.RecognitionMode
import com.banking.statement.categorization.rememberRecognitionModePreferenceState
import com.banking.statement.ui.components.MLSetGroup
import com.banking.statement.ui.components.MLSetRow
import com.banking.statement.ui.components.MoneyLupeChoiceDialog
import com.banking.statement.ui.theme.AppColors

@Composable
fun RecognitionModeSettingsSection(
    currentRecognitionMode: RecognitionMode = RecognitionMode.SAFE,
    onRecognitionModeChange: (RecognitionMode) -> Unit = {}
) {
    val preferenceState = rememberRecognitionModePreferenceState()
    val selectedMode = preferenceState.mode
    var showRecognitionModePicker by remember { mutableStateOf(false) }

    MLSetGroup(eyebrow = "Automation") {
        MLSetRow(
            icon = Icons.Filled.AutoAwesome,
            iconTint = Color(0xFF8B5CF6),
            title = "Category recognition",
            subtitle = recognitionModeDescription(selectedMode),
            onClick = { showRecognitionModePicker = true },
            showChevron = true,
            isFirst = true,
            trailing = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppColors.SurfaceTint)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = recognitionModeLabel(selectedMode),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextSecondary
                    )
                }
            }
        )
    }

    if (showRecognitionModePicker) {
        MoneyLupeChoiceDialog(
            eyebrow = "Automation",
            title = "Category recognition",
            options = listOf(
                RecognitionMode.SAFE to "Safe — rules only",
                RecognitionMode.BALANCED to "Balanced — rules + ML fallback",
                RecognitionMode.EXPERIMENTAL to "Experimental — more ML suggestions"
            ),
            selected = selectedMode,
            onSelect = { mode ->
                preferenceState.updateMode(mode)
                onRecognitionModeChange(mode)
                showRecognitionModePicker = false
            },
            onDismiss = { showRecognitionModePicker = false }
        )
    }
}

private fun recognitionModeLabel(mode: RecognitionMode): String {
    return when (mode) {
        RecognitionMode.SAFE -> "Safe"
        RecognitionMode.BALANCED -> "Balanced"
        RecognitionMode.EXPERIMENTAL -> "Experimental"
    }
}

private fun recognitionModeDescription(mode: RecognitionMode): String {
    return when (mode) {
        RecognitionMode.SAFE -> "Rules and manual corrections only. Unknown stays Other."
        RecognitionMode.BALANCED -> "Rules first, then ML fallback when confidence is at least 0.30."
        RecognitionMode.EXPERIMENTAL -> "Rules first, then lower-threshold ML fallback at 0.20."
    }
}
