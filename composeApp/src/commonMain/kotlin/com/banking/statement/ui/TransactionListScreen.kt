package com.banking.statement.ui

import androidx.compose.foundation.background
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
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.TransactionCategory

/**
 * Display model for a transaction in the list
 */
data class TransactionDisplay(
    val id: Long,
    val date: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val category: TransactionCategory,
    val counterparty: String?,
    val accountId: Long = 0,
    val accountName: String = ""
)

/**
 * Filter option for account dropdown
 */
data class AccountFilterOption(
    val id: Long?,  // null means "All Accounts"
    val name: String
)

@Composable
fun TransactionListScreen(
    transactions: List<TransactionDisplay>,
    accounts: List<AccountFilterOption> = emptyList(),
    onBackClick: () -> Unit
) {
    val strings = LocalStrings.current
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Filter transactions based on selected account
    val filteredTransactions = remember(transactions, selectedAccountId) {
        if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }
    }

    // Get selected account name for display
    val selectedAccountName = remember(selectedAccountId, accounts) {
        if (selectedAccountId == null) {
            strings.allAccounts
        } else {
            accounts.find { it.id == selectedAccountId }?.name ?: strings.allAccounts
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.transactionListTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onBackClick) {
                Text(strings.back)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Account filter dropdown (only show if multiple accounts)
        if (accounts.size > 1) {
            Box {
                OutlinedButton(
                    onClick = { dropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = selectedAccountName,
                        modifier = Modifier.weight(1f)
                    )
                    Text(" ▼")
                }

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    // All Accounts option
                    DropdownMenuItem(
                        text = { Text(strings.allAccounts) },
                        onClick = {
                            selectedAccountId = null
                            dropdownExpanded = false
                        }
                    )
                    HorizontalDivider()
                    // Individual accounts
                    accounts.forEach { account ->
                        if (account.id != null) {
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccountId = account.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = "${filteredTransactions.size} ${strings.transactions.lowercase()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${strings.noTransactions}.\n${strings.importFirst}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransactions) { transaction ->
                    TransactionItem(transaction)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(transaction: TransactionDisplay) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parseColor(transaction.category.color).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getCategoryEmoji(transaction.category),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Description and category
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.counterparty ?: transaction.description.take(30),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = parseColor(transaction.category.color)
                    )
                    Text(
                        text = " · ${transaction.date}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Amount
            Text(
                text = formatAmount(transaction.amount, transaction.currency),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (transaction.amount >= 0) {
                    Color(0xFF4CAF50) // Green for positive
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

private fun formatAmount(amount: Double, currency: String): String {
    val symbol = when (currency) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        else -> currency
    }
    val formatted = "%.2f".format(kotlin.math.abs(amount))
        .replace(".", ",") // German format
    return if (amount >= 0) "+$formatted $symbol" else "-$formatted $symbol"
}

private fun parseColor(hexColor: String): Color {
    return try {
        val hex = hexColor.removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) {
        Color.Gray
    }
}

private fun getCategoryEmoji(category: TransactionCategory): String {
    return when (category) {
        TransactionCategory.RENT -> "🏠"
        TransactionCategory.UTILITIES -> "⚡"
        TransactionCategory.PUBLIC_TRANSPORT -> "🚇"
        TransactionCategory.CAR -> "🚗"
        TransactionCategory.SUPERMARKET -> "🛒"
        TransactionCategory.RESTAURANT -> "🍽️"
        TransactionCategory.SHOPPING -> "🛍️"
        TransactionCategory.HEALTH -> "💊"
        TransactionCategory.INSURANCE -> "🛡️"
        TransactionCategory.ENTERTAINMENT -> "🎬"
        TransactionCategory.SUBSCRIPTIONS -> "📱"
        TransactionCategory.PHONE_INTERNET -> "📞"
        TransactionCategory.BANK_FEES -> "🏦"
        TransactionCategory.INVESTMENT -> "📈"
        TransactionCategory.FITNESS -> "💪"
        TransactionCategory.SALARY -> "💰"
        TransactionCategory.REFUND -> "↩️"
        TransactionCategory.TRANSFER -> "↔️"
        TransactionCategory.CASH -> "💵"
        TransactionCategory.PAYMENT_SERVICE -> "💳"
        TransactionCategory.OTHER -> "❓"
    }
}
