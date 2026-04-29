package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.banking.statement.LocalStrings
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing

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
 * Dialog shown when a new bank statement is detected. Brand-styled to match
 * MoneyLupeChoiceDialog (eyebrow, rounded card, brand colors) instead of the
 * stock Material Card.
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(AppElevations.md, RoundedCornerShape(AppRadii.xl), clip = false)
                .clip(RoundedCornerShape(AppRadii.xl))
                .background(AppColors.CardBackground)
                .padding(AppSpacing.s5)
        ) {
            EyebrowLabel(text = strings.transactions)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.newBankStatement,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(AppSpacing.s4))

            // Statement summary card — flat tinted block, no nested Material Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppRadii.md))
                    .background(AppColors.SurfaceTint)
                    .padding(AppSpacing.s3),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ImportInfoRow(label = strings.bankLabel, value = bankName)
                if (statementPeriod != null) {
                    ImportInfoRow(label = strings.periodLabel, value = statementPeriod)
                }
                ImportInfoRow(label = strings.transactions, value = transactionCount.toString(), mono = true)
                if (iban != null) {
                    ImportInfoRow(label = strings.ibanLabel, value = formatIban(iban), mono = true)
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.s4))

            EyebrowLabel(text = strings.whereToGo)
            Spacer(modifier = Modifier.height(AppSpacing.s2))

            // Option 1: Create new account
            ImportOptionCard(
                selected = selectedOption == "new",
                onClick = { selectedOption = "new" },
                icon = Icons.Filled.AddCircle,
                title = strings.createNewAccount,
                content = {
                    if (selectedOption == "new") {
                        Spacer(modifier = Modifier.height(AppSpacing.s2))
                        OutlinedTextField(
                            value = newAccountName,
                            onValueChange = { newAccountName = it },
                            label = {
                                Text(
                                    text = strings.accountName,
                                    fontSize = 12.sp,
                                    color = AppColors.TextSecondary
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(AppRadii.md),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Primary,
                                unfocusedBorderColor = AppColors.Divider,
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary,
                                cursorColor = AppColors.Primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )

            // Option 2: Add to existing account
            if (existingAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppSpacing.s2))
                ImportOptionCard(
                    selected = selectedOption == "existing",
                    onClick = { selectedOption = "existing" },
                    icon = Icons.Filled.AccountBalanceWallet,
                    title = strings.addToExisting,
                    content = {
                        if (selectedOption == "existing") {
                            Spacer(modifier = Modifier.height(AppSpacing.s2))
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.s4))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onChoice(ImportChoice.Cancel) },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextSecondary)
                ) {
                    Text(
                        text = strings.cancel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(AppSpacing.s1))
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
                    },
                    shape = RoundedCornerShape(AppRadii.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.HeaderText,
                        disabledContainerColor = AppColors.Disabled,
                        disabledContentColor = AppColors.TextTertiary
                    )
                ) {
                    Text(
                        text = strings.importAction,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportInfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppColors.TextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun ImportOptionCard(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.md))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AppColors.Primary else AppColors.Divider,
                shape = RoundedCornerShape(AppRadii.md)
            )
            .background(
                if (selected) AppColors.PrimaryContainer.copy(alpha = 0.4f)
                else AppColors.CardBackground
            )
            .clickable(onClick = onClick)
            .padding(AppSpacing.s3)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Primary.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.s2 + 2.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        content()
    }
}

@Composable
private fun AccountSelectionItem(
    account: AccountOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.md))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) AppColors.Primary else AppColors.Divider,
                shape = RoundedCornerShape(AppRadii.md)
            )
            .background(
                if (isSelected) AppColors.PrimaryContainer.copy(alpha = 0.4f)
                else AppColors.CardBackground
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.s3, vertical = AppSpacing.s2 + 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Squircle color avatar with initials, mirroring the manage-accounts list
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(parseAccountColor(account.color)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = account.name.take(2).uppercase(),
                fontSize = 11.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(AppSpacing.s2 + 2.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${account.bankName} · ${account.transactionCount} tx",
                fontSize = 11.sp,
                color = AppColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (account.balance != null) {
            Text(
                text = formatAmount(account.balance, "EUR"),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (account.balance >= 0) AppColors.Income else AppColors.Expenses
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
 * Brand-styled success dialog shown after a successful import.
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
    val title = when {
        isDuplicateStatement -> strings.duplicateStatement
        isNewAccount -> strings.accountCreated
        else -> strings.importComplete
    }
    val accentColor = if (isDuplicateStatement) AppColors.TextTertiary else AppColors.Income

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(AppElevations.md, RoundedCornerShape(AppRadii.xl), clip = false)
                .clip(RoundedCornerShape(AppRadii.xl))
                .background(AppColors.CardBackground)
                .padding(AppSpacing.s5),
            horizontalAlignment = Alignment.Start
        ) {
            // Big circular check / accent
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.s3))

            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(AppSpacing.s2))

            if (isDuplicateStatement) {
                Text(
                    text = strings.duplicateStatementMessage,
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
            } else {
                if (isNewAccount) {
                    Text(
                        text = "${strings.accountCreated}: \"$accountName\"",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.s3))
                }

                // Stats card — flat tinted block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppRadii.md))
                        .background(AppColors.SurfaceTint)
                        .padding(AppSpacing.s3),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SuccessStatRow(
                        label = strings.transactions,
                        value = transactionsImported.toString(),
                        valueColor = AppColors.Income
                    )
                    if (duplicatesSkipped > 0) {
                        SuccessStatRow(
                            label = "Duplicates skipped",
                            value = duplicatesSkipped.toString(),
                            valueColor = AppColors.TextTertiary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.s4))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(AppRadii.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.HeaderText
                    )
                ) {
                    Text(
                        text = strings.done,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessStatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = AppColors.TextSecondary
        )
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = valueColor
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
