package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.banking.statement.LocalStrings
import com.banking.statement.ui.theme.AppColors

/**
 * Represents an existing account for selection
 */
data class AccountOption(
    val id: Long,
    val name: String,
    val bankName: String,
    val iban: String?,
    val color: String?,
    val transactionCount: Long,
    val balance: Double?
)

/**
 * User's choice when importing a statement
 */
sealed class ImportChoice {
    data class AddToExisting(val accountId: Long) : ImportChoice()
    data class CreateNew(val accountName: String) : ImportChoice()
    data object Cancel : ImportChoice()
}

/**
 * Dialog shown when a new bank statement is detected
 */
@Composable
fun ImportAccountDialog(
    bankName: String,
    iban: String?,
    statementPeriod: String?,
    transactionCount: Int,
    existingAccounts: List<AccountOption>,
    suggestedAccountName: String,
    onChoice: (ImportChoice) -> Unit
) {
    val strings = LocalStrings.current
    var selectedOption by remember { mutableStateOf<String>("new") }
    var selectedAccountId by remember { mutableStateOf<Long?>(existingAccounts.firstOrNull()?.id) }
    var newAccountName by remember { mutableStateOf(suggestedAccountName) }

    Dialog(onDismissRequest = { onChoice(ImportChoice.Cancel) }) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)  // Use more screen width
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = strings.newBankStatement,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                // Statement info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.bankLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = bankName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (statementPeriod != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = strings.periodLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = statementPeriod,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = strings.transactions,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$transactionCount",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (iban != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = strings.ibanLabel,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatIban(iban),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Divider()

                // Options
                Text(
                    text = strings.whereToGo,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )

                // Option 1: Create new account
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedOption = "new" }
                        .background(
                            if (selectedOption == "new")
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else
                                Color.Transparent
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOption == "new",
                        onClick = { selectedOption = "new" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.createNewAccount,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (selectedOption == "new") {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = newAccountName,
                                onValueChange = { newAccountName = it },
                                label = { Text(strings.accountName) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Option 2: Add to existing account (if accounts exist)
                if (existingAccounts.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selectedOption == "existing")
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    Color.Transparent
                            )
                            .padding(12.dp)
                    ) {
                        // Radio button row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOption = "existing" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOption == "existing",
                                onClick = { selectedOption = "existing" }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.addToExisting,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Account list - full width, outside of row
                        if (selectedOption == "existing") {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(existingAccounts) { account ->
                                    AccountSelectionItem(
                                        account = account,
                                        isSelected = selectedAccountId == account.id,
                                        onClick = { selectedAccountId = account.id }
                                    )
                                }
                            }
                        }
                    }
                }

                Divider()

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onChoice(ImportChoice.Cancel) }) {
                        Text(strings.cancel)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when (selectedOption) {
                                "new" -> onChoice(ImportChoice.CreateNew(newAccountName))
                                "existing" -> selectedAccountId?.let {
                                    onChoice(ImportChoice.AddToExisting(it))
                                }
                            }
                        },
                        enabled = when (selectedOption) {
                            "new" -> newAccountName.isNotBlank()
                            "existing" -> selectedAccountId != null
                            else -> false
                        }
                    ) {
                        Text(strings.importAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountSelectionItem(
    account: AccountOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(parseAccountColor(account.color))
                ) {
                    Text(
                        text = account.name.take(2).uppercase(),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Account name - takes remaining space
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Balance on the right
                if (account.balance != null) {
                    Text(
                        text = formatAmount(account.balance, "EUR"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (account.balance >= 0) AppColors.Income else AppColors.Expenses
                    )
                }
            }

            // Bank info on second line - full width
            Text(
                text = "${account.bankName} • ${account.transactionCount} transactions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Dialog shown when adding a statement to an account that already has the same IBAN
 * This is an informational dialog since it will auto-add
 */
@Composable
fun ImportConfirmationDialog(
    bankName: String,
    accountName: String,
    transactionCount: Int,
    duplicateCount: Int,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings.addToAccount.replace("%s", accountName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This statement from $bankName will be added to your existing account.")
                Text("• $transactionCount ${strings.transactions.lowercase()}")
                if (duplicateCount > 0) {
                    Text(
                        "• $duplicateCount duplicates will be skipped",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(strings.add)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(strings.cancel)
            }
        }
    )
}

/**
 * Dialog shown after successful import
 */
@Composable
fun ImportSuccessDialog(
    accountName: String,
    transactionsImported: Int,
    duplicatesSkipped: Int,
    isNewAccount: Boolean,
    isDuplicateStatement: Boolean = false,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    isDuplicateStatement -> strings.duplicateStatement
                    isNewAccount -> strings.accountCreated
                    else -> strings.importComplete
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDuplicateStatement) {
                    Text(strings.duplicateStatementMessage)
                } else {
                    if (isNewAccount) {
                        Text("New account \"$accountName\" has been created.")
                    }
                    Text("✓ $transactionsImported ${strings.transactions.lowercase()}")
                    if (duplicatesSkipped > 0) {
                        Text(
                            "○ $duplicatesSkipped duplicates skipped",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(strings.done)
            }
        }
    )
}

/**
 * Represents the details of an import error for display in the error dialog
 */
data class ImportErrorDetails(
    val errorMessage: String,
    val fileName: String? = null,
    val fileFormat: String? = null,
    val technicalDetails: String? = null
)

/**
 * Dialog shown when an import fails, providing detailed error information,
 * actionable suggestions, and a retry option.
 */
@Composable
fun ImportErrorDialog(
    errorDetails: ImportErrorDetails,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with error icon and title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = strings.importErrorTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider()

                // "What happened" section
                Text(
                    text = strings.importErrorWhatHappened,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )

                Text(
                    text = errorDetails.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // File info card (if available)
                if (errorDetails.fileName != null || errorDetails.fileFormat != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            errorDetails.fileName?.let { name ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.importErrorFileLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                            errorDetails.fileFormat?.let { format ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.importErrorFormatLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = format.uppercase(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            errorDetails.technicalDetails?.let { details ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.importErrorDetailsLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // "What you can do" section
                Text(
                    text = strings.importErrorWhatYouCanDo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuggestionRow(strings.importErrorTryDifferentFormat)
                    SuggestionRow(strings.importErrorCheckFileValid)
                    SuggestionRow(strings.importErrorContactSupport)
                }

                HorizontalDivider()

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.dismiss)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onRetry) {
                        Text(strings.retryImport)
                    }
                }
            }
        }
    }
}

/**
 * Dialog shown after the 5th successful import to prompt the user to rate the app
 */
@Composable
fun RatingPromptDialog(
    onRate: () -> Unit,
    onLater: () -> Unit,
    onNever: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(
                text = strings.ratingTitle,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(strings.ratingMessage)
        },
        confirmButton = {
            Button(onClick = onRate) {
                Text(strings.ratingRate)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNever) {
                    Text(strings.ratingNever)
                }
                TextButton(onClick = onLater) {
                    Text(strings.ratingLater)
                }
            }
        }
    )
}

@Composable
private fun SuggestionRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

// Helper functions
private fun formatIban(iban: String): String {
    return iban.chunked(4).joinToString(" ")
}

/*private fun formatAmount(amount: Double, currency: String): String {
    val formatted = "%.2f".format(kotlin.math.abs(amount))
    val sign = if (amount < 0) "-" else "+"
    return "$sign$formatted $currency"
}*/

private fun formatAmount(amount: Double, currency: String): String {
    val absAmount = kotlin.math.abs(amount)
    val rounded = kotlin.math.round(absAmount * 100) / 100
    val formatted = rounded.toString().replace(".", ",")
    val sign = if (amount < 0) "-" else "+"
    return "$sign$formatted $currency"
}

private fun parseAccountColor(hexColor: String?): Color {
    if (hexColor == null) return Color(0xFF5C6BC0) // Default indigo

    return try {
        val hex = hexColor.removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) {
        Color(0xFF5C6BC0)
    }
}
