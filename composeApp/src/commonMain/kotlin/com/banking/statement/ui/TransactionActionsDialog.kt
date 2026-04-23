package com.banking.statement.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banking.statement.LocalStrings

/**
 * Action sheet shown when the user taps a transaction row.
 *
 * Single-tap on a row opens this dialog instead of jumping straight to the
 * category picker, so the same tap gesture can also be used to open the
 * source PDF. The category picker is still reachable from here in one extra
 * tap, matching the "tap twice to change category" UX the feature request
 * described.
 */
@Composable
fun TransactionActionsDialog(
    transaction: TransactionDisplay,
    canViewSourcePdf: Boolean,
    onChangeCategory: () -> Unit,
    onViewSourcePdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = TransactionDisplay.extractDisplayName(
                        transaction.counterparty,
                        transaction.description
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${transaction.date}  ·  ${transaction.effectiveCategoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionRow(
                    icon = "🏷️",
                    label = strings.changeCategory,
                    subtitle = "Assign a different predefined or custom category",
                    onClick = onChangeCategory
                )
                if (canViewSourcePdf) {
                    ActionRow(
                        icon = "📄",
                        label = "View source PDF",
                        subtitle = if (transaction.sourcePage != null)
                            "Open the original statement at page ${transaction.sourcePage + 1}"
                        else
                            "Open the original statement PDF",
                        onClick = onViewSourcePdf
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.cancel) }
        }
    )
}

@Composable
private fun ActionRow(
    icon: String,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
