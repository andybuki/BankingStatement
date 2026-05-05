package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.banking.statement.AppStrings
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.RecognitionMode
import com.banking.statement.categorization.TransactionMlClassifier
import com.banking.statement.categorization.rememberRecognitionModePreferenceState
import com.banking.statement.ui.components.MLSetGroup
import com.banking.statement.ui.components.MLSetRow
import com.banking.statement.ui.components.MoneyLupeChoiceDialog
import com.banking.statement.ui.theme.AppColors
import org.koin.mp.KoinPlatform

@Composable
fun RecognitionModeSettingsSection() {
    val strings = LocalStrings.current
    val preferenceState = rememberRecognitionModePreferenceState()
    val selectedMode = preferenceState.mode
    var showRecognitionModePicker by remember { mutableStateOf(false) }
    val modelVersion = remember {
        KoinPlatform.getKoin().get<TransactionMlClassifier>().modelVersion
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MLSetGroup(eyebrow = strings.recognitionEyebrow) {
            MLSetRow(
                icon = Icons.Filled.AutoAwesome,
                iconTint = Color(0xFF8B5CF6),
                title = strings.recognitionTitle,
                subtitle = recognitionModeDescription(strings, selectedMode),
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
                            text = recognitionModeLabel(strings, selectedMode),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            )
        }

        Text(
            text = modelStatusText(strings, modelVersion),
            fontSize = 11.sp,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }

    if (showRecognitionModePicker) {
        MoneyLupeChoiceDialog(
            eyebrow = strings.recognitionEyebrow,
            title = strings.recognitionTitle,
            options = RecognitionMode.entries.map { mode ->
                mode to recognitionModeOptionLabel(strings, mode)
            },
            selected = selectedMode,
            onSelect = { mode ->
                preferenceState.update(mode)
                showRecognitionModePicker = false
            },
            onDismiss = { showRecognitionModePicker = false }
        )
    }
}

private fun recognitionModeLabel(strings: AppStrings, mode: RecognitionMode): String {
    return when (mode) {
        RecognitionMode.SAFE -> strings.recognitionLabelSafe
        RecognitionMode.BALANCED -> strings.recognitionLabelBalanced
        RecognitionMode.EXPERIMENTAL -> strings.recognitionLabelExperimental
    }
}

private fun recognitionModeOptionLabel(strings: AppStrings, mode: RecognitionMode): String {
    return when (mode) {
        RecognitionMode.SAFE -> strings.recognitionOptionSafe
        RecognitionMode.BALANCED -> strings.recognitionOptionBalanced
        RecognitionMode.EXPERIMENTAL -> strings.recognitionOptionExperimental
    }
}

private fun recognitionModeDescription(strings: AppStrings, mode: RecognitionMode): String {
    val threshold = mode.mlConfidenceThreshold
    return when (mode) {
        RecognitionMode.SAFE -> strings.recognitionDescriptionSafe
        RecognitionMode.BALANCED ->
            strings.recognitionDescriptionBalanced.replace(
                THRESHOLD_PLACEHOLDER,
                formatThreshold(threshold ?: 0.0)
            )
        RecognitionMode.EXPERIMENTAL ->
            strings.recognitionDescriptionExperimental.replace(
                THRESHOLD_PLACEHOLDER,
                formatThreshold(threshold ?: 0.0)
            )
    }
}

private fun formatThreshold(value: Double): String {
    val scaled = (value * 100).toInt()
    val whole = scaled / 100
    val frac = scaled % 100
    return whole.toString() + "." + (if (frac < 10) "0$frac" else frac.toString())
}

private fun modelStatusText(strings: AppStrings, version: String?): String {
    return if (version.isNullOrBlank()) {
        strings.recognitionModelMissing
    } else {
        strings.recognitionModelLoaded.replace(VERSION_PLACEHOLDER, version)
    }
}

private const val THRESHOLD_PLACEHOLDER = "{threshold}"
private const val VERSION_PLACEHOLDER = "{version}"
