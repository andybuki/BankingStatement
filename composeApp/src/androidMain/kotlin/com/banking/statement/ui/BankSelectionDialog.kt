package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.banking.statement.DetectedBankOption
import com.banking.statement.LocalStrings

/**
 * Dialog for selecting a bank when automatic detection is uncertain
 */
@Composable
fun BankSelectionDialog(
    detectedBanks: List<DetectedBankOption>,
    onBankSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedBank by remember { mutableStateOf<String?>(null) }
    val strings = LocalStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Title
                Text(
                    text = strings.selectYourBank,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle
                Text(
                    text = strings.multipleBanksDetected,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bank list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(detectedBanks) { bank ->
                        BankOptionItem(
                            bank = bank,
                            isSelected = selectedBank == bank.bankName,
                            onClick = { selectedBank = bank.bankName },
                            strings = strings
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { selectedBank?.let { onBankSelected(it) } },
                        enabled = selectedBank != null
                    ) {
                        Text(strings.select)
                    }
                }
            }
        }
    }
}

@Composable
private fun BankOptionItem(
    bank: DetectedBankOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    strings: com.banking.statement.AppStrings
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bank.bankName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                if (bank.matchedIdentifiers.isNotEmpty()) {
                    Text(
                        text = "${strings.matched} ${bank.matchedIdentifiers.take(3).joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Confidence badge
            ConfidenceBadge(confidence = bank.confidence, strings = strings)
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: String, strings: com.banking.statement.AppStrings) {
    val (backgroundColor, textColor, localizedText) = when (confidence) {
        "Certain" -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, strings.confidenceCertain)
        "High" -> Triple(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary, strings.confidenceHigh)
        "Medium" -> Triple(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary, strings.confidenceMedium)
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, confidence)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = localizedText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}
