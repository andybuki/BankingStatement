package com.banking.statement.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import bankingstatement.composeapp.generated.resources.share
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import org.jetbrains.compose.resources.painterResource

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
    val detailText: String? = null,
    val accountId: Long = 0,
    val accountName: String = ""
) {
    companion object {
        /**
         * Extracts a clean display name from the counterparty and description.
         * Special handling for PayPal and other payment processors.
         */
        fun extractDisplayName(counterparty: String?, description: String): String {
            val counterpartyLower = counterparty?.lowercase() ?: ""
            val descriptionLower = description.lowercase()

            // PayPal special handling - extract merchant name
            if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
                val merchantName = extractPayPalMerchant(description)
                if (merchantName != null) {
                    return "PayPal · $merchantName"
                }
                return "PayPal"
            }

            // Use counterparty if available and meaningful
            if (!counterparty.isNullOrBlank() && counterparty.length > 2) {
                return counterparty.take(50)
            }

            // Clean up description for display
            return cleanDescriptionForDisplay(description)
        }

        /**
         * Extracts merchant name from PayPal transaction descriptions.
         * Example: "PayPal Europe S.a.r.l. ... Preply, Inc., Ihr Einkauf bei Preply, Inc."
         * Returns: "Preply, Inc."
         */
        private fun extractPayPalMerchant(description: String): String? {
            // Common patterns for merchant names in PayPal transactions
            val patterns = listOf(
                // German: "Ihr Einkauf bei [Merchant]"
                Regex("""[Ii]hr [Ee]inkauf bei\s+(.+?)(?:\s*,\s*Ihr|\s*$)"""),
                // "bei [Merchant]" pattern
                Regex("""bei\s+([A-Z][^,]{2,30})"""),
                // Look for company patterns after PP reference numbers
                Regex("""/PP\.[^/]+/\.?\s*([A-Z][^,]{2,40})"""),
                // Look for merchant after long reference codes
                Regex("""\d{10,}/[^,]+,\s*([A-Z][^,]{2,40})""")
            )

            for (pattern in patterns) {
                val match = pattern.find(description)
                if (match != null) {
                    val merchant = match.groupValues[1].trim()
                        .replace(Regex("""\s+"""), " ")
                        .take(35)
                    if (merchant.length >= 3 && !merchant.all { it.isDigit() || it == '.' }) {
                        return merchant
                    }
                }
            }

            return null
        }

        /**
         * Cleans up a description for display, prioritizing readable text over numbers.
         */
        private fun cleanDescriptionForDisplay(description: String): String {
            // Remove common verbose prefixes
            val cleaned = description
                .replace(Regex("""^(SEPA-?|ÜBERWEISUNG|LASTSCHRIFT|KARTENZAHLUNG)\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^(Card payment|Transfer|Direct debit)\s*""", RegexOption.IGNORE_CASE), "")
                .trim()

            // If mostly numbers/codes, keep it shorter; otherwise allow longer text
            val textRatio = cleaned.count { it.isLetter() }.toFloat() / cleaned.length.coerceAtLeast(1)
            val maxLength = if (textRatio > 0.5) 70 else 50

            return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength) + "…"
        }

        /**
         * Extracts additional detail text from remittance info or description.
         * This is shown as a secondary line in the UI.
         */
        fun extractDetailText(description: String, remittanceInfo: String?, counterparty: String?): String? {
            val counterpartyLower = counterparty?.lowercase() ?: ""
            val descriptionLower = description.lowercase()

            // For PayPal, try to extract the purchase description
            if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
                val purchaseDetail = extractPayPalPurchaseDetail(description)
                if (purchaseDetail != null) {
                    return purchaseDetail
                }
            }

            // Use remittance info if available and meaningful
            if (!remittanceInfo.isNullOrBlank() && remittanceInfo.length > 5) {
                val cleaned = cleanDetailText(remittanceInfo)
                if (cleaned != null) {
                    return cleaned
                }
            }

            // Extract second line info from description if present
            val lines = description.split(Regex("""[\n\r]+"""))
            if (lines.size > 1) {
                val secondLine = lines[1].trim()
                if (secondLine.length > 5) {
                    val cleaned = cleanDetailText(secondLine)
                    if (cleaned != null) {
                        return cleaned
                    }
                }
            }

            return null
        }

        /**
         * Cleans detail text by removing reference codes and validating content.
         * Returns null if the text is not meaningful for display.
         */
        private fun cleanDetailText(text: String): String? {
            // Remove Mandat:, Referenz:, and similar reference patterns
            val cleaned = text
                .replace(Regex("""Mandat:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Referenz:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Mandatsref\.?:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Gläubiger-?ID:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Creditor-?ID:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""End-to-End-?Ref\.?:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""EREF:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""MREF:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""CRED:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            // Check if remaining text is meaningful (>30% letters, at least 5 chars)
            if (cleaned.length < 5) return null
            val textRatio = cleaned.count { it.isLetter() }.toFloat() / cleaned.length
            if (textRatio < 0.3) return null

            return cleaned.take(60).let {
                if (cleaned.length > 60) "$it…" else it
            }
        }

        private fun extractPayPalPurchaseDetail(description: String): String? {
            // Look for "Ihr Einkauf bei" pattern and extract the full context
            val match = Regex("""[Ii]hr [Ee]inkauf bei\s+(.+)""").find(description)
            if (match != null) {
                return match.groupValues[1].take(50).let {
                    if (match.groupValues[1].length > 50) "$it…" else it
                }
            }
            return null
        }
    }
}

/**
 * Filter option for account dropdown
 */
data class AccountFilterOption(
    val id: Long?,  // null means "All Accounts"
    val name: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<TransactionDisplay>,
    accounts: List<AccountFilterOption> = emptyList(),
    onBackClick: () -> Unit,
    onShare: ((ExportFormat, List<TransactionDisplay>, String?) -> Unit)? = null,
    onCategoryChange: ((TransactionDisplay, TransactionCategory) -> Unit)? = null
) {
    val strings = LocalStrings.current
    var selectedAccountId by remember { mutableStateOf<Long?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf<TransactionDisplay?>(null) }

    // Filter transactions based on selected account and search query
    val filteredTransactions = remember(transactions, selectedAccountId, searchQuery) {
        var result = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }

        // Apply search filter
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase().trim()
            result = result.filter { tx ->
                tx.description.lowercase().contains(query) ||
                tx.counterparty?.lowercase()?.contains(query) == true ||
                tx.category.displayName.lowercase().contains(query) ||
                tx.category.displayNameDe.lowercase().contains(query) ||
                tx.date.contains(query) ||
                formatAmount(tx.amount, tx.currency).contains(query)
            }
        }

        result
    }

    // Get selected account name for display
    val selectedAccountName = remember(selectedAccountId, accounts) {
        if (selectedAccountId == null) {
            strings.allAccounts
        } else {
            accounts.find { it.id == selectedAccountId }?.name ?: strings.allAccounts
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.transactionListTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Image(
                            painter = painterResource(Res.drawable.back),
                            contentDescription = strings.back,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                        )
                    }
                },
                actions = {
                    // Share button with dropdown
                    if (onShare != null && filteredTransactions.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { shareMenuExpanded = true }) {
                                Image(
                                    painter = painterResource(Res.drawable.share),
                                    contentDescription = strings.share,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                            DropdownMenu(
                                expanded = shareMenuExpanded,
                                onDismissRequest = { shareMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.exportCsv) },
                                    onClick = {
                                        shareMenuExpanded = false
                                        onShare(ExportFormat.CSV, filteredTransactions, selectedAccountName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.exportPdf) },
                                    onClick = {
                                        shareMenuExpanded = false
                                        onShare(ExportFormat.PDF, filteredTransactions, selectedAccountName)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
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

        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(strings.searchTransactions) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Text("✕", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${filteredTransactions.size} ${strings.transactions.lowercase()}" +
                if (searchQuery.isNotBlank()) " (${strings.filtered})" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

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
                    TransactionItem(
                        transaction = transaction,
                        onClick = if (onCategoryChange != null) {
                            { showCategoryPicker = transaction }
                        } else null
                    )
                }
            }
        }
        }
    }

    // Category picker dialog
    showCategoryPicker?.let { transaction ->
        CategoryPickerDialog(
            currentCategory = transaction.category,
            onCategorySelected = { newCategory ->
                onCategoryChange?.invoke(transaction, newCategory)
                showCategoryPicker = null
            },
            onDismiss = { showCategoryPicker = null }
        )
    }
}

@Composable
fun TransactionItem(
    transaction: TransactionDisplay,
    onClick: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable { onClick() }
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                // Primary line: smart display name (PayPal handling, longer text for readable content)
                Text(
                    text = TransactionDisplay.extractDisplayName(transaction.counterparty, transaction.description),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Secondary line: detail text if available
                transaction.detailText?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Category and date
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = transaction.category.getLocalizedName(strings),
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
        TransactionCategory.TRANSPORT -> "🚌"
        TransactionCategory.SUPERMARKET -> "🛒"
        TransactionCategory.RESTAURANT -> "🍽️"
        TransactionCategory.SHOPPING -> "🛍️"
        TransactionCategory.HEALTH -> "💊"
        TransactionCategory.INSURANCE -> "🛡️"
        TransactionCategory.ENTERTAINMENT -> "🎬"
        TransactionCategory.SUBSCRIPTIONS -> "📱"
        TransactionCategory.INVESTMENT -> "📈"
        TransactionCategory.TRAVEL -> "✈️"
        TransactionCategory.SALARY -> "💰"
        TransactionCategory.REFUND -> "↩️"
        TransactionCategory.TRANSFER -> "↔️"
        TransactionCategory.EDUCATION -> "🎓"
        TransactionCategory.TAXES -> "📋"
        TransactionCategory.OTHER -> "❓"
    }
}

@Composable
fun CategoryPickerDialog(
    currentCategory: TransactionCategory,
    onCategorySelected: (TransactionCategory) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.changeCategory,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(TransactionCategory.entries.filter { it != TransactionCategory.OTHER }) { category ->
                    val isSelected = category == currentCategory
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCategorySelected(category) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                parseColor(category.color).copy(alpha = 0.3f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(parseColor(category.color).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = getCategoryEmoji(category),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = category.getLocalizedName(strings),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) parseColor(category.color) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
