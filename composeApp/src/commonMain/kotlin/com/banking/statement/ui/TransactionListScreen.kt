package com.banking.statement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.flow.distinctUntilChanged
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import bankingstatement.composeapp.generated.resources.share
import bankingstatement.composeapp.generated.resources.ic_sort_desc
import bankingstatement.composeapp.generated.resources.ic_calendar
import com.banking.statement.LocalStrings
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn

enum class TransactionSortOrder {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC, NAME_ASC
}

enum class TransactionTimeFilter {
    ALL, WEEK, MONTH, YEAR, CUSTOM
}

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
    val accountName: String = "",
    // Custom category support
    val customCategoryId: Long? = null,
    val customCategoryName: String? = null,
    val customCategoryIcon: String? = null,
    val customCategoryColor: String? = null
) {
    /**
     * Returns true if this transaction has a custom category assigned
     */
    val hasCustomCategory: Boolean get() = customCategoryId != null

    /**
     * Returns the effective display name for the category
     */
    val effectiveCategoryName: String get() = customCategoryName ?: category.displayName

    /**
     * Returns the effective icon for the category
     */
    val effectiveCategoryIcon: String get() = customCategoryIcon ?: category.icon

    /**
     * Returns the effective color for the category
     */
    val effectiveCategoryColor: String get() = customCategoryColor ?: category.color

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
    selectedAccountId: Long? = null,  // Controlled from outside (App level) — already DB-filtered
    onAccountSelected: ((Long?) -> Unit)? = null,
    totalTransactionCount: Long = 0L,  // Accurate total from DB (filtered)
    selectedDateRange: Pair<Long, Long>? = null,
    onDateRangeChange: ((Long?, Long?) -> Unit)? = null,
    customCategories: List<com.banking.statement.categorization.CustomCategory> = emptyList(),
    onBackClick: (() -> Unit)? = null,
    onShare: ((ExportFormat, List<TransactionDisplay>, String?) -> Unit)? = null,
    onCategoryChange: ((TransactionDisplay, TransactionCategory) -> Unit)? = null,
    onCustomCategoryChange: ((TransactionDisplay, Long) -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null,
    hasMoreTransactions: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf<TransactionDisplay?>(null) }

    // Sort & filter state
    var sortOrder by remember { mutableStateOf(TransactionSortOrder.DATE_DESC) }
    var timeFilter by remember { mutableStateOf(TransactionTimeFilter.ALL) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    // DatePicker state for custom range
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var pendingStartMillis by remember { mutableStateOf<Long?>(null) }
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()

    // Sync timeFilter with external selectedDateRange so Clear works
    LaunchedEffect(selectedDateRange) {
        if (selectedDateRange == null && timeFilter == TransactionTimeFilter.CUSTOM) {
            timeFilter = TransactionTimeFilter.ALL
        }
    }

    // Transactions are DB-filtered; apply search + sort locally only
    val filteredTransactions by remember(transactions, searchQuery, sortOrder) {
        derivedStateOf {
            var result = transactions

            // Search filter
            if (searchQuery.isNotBlank()) {
                val query = searchQuery.lowercase().trim()
                result = result.filter { tx ->
                    tx.description.lowercase().contains(query) ||
                    tx.counterparty?.lowercase()?.contains(query) == true ||
                    tx.category.displayName.lowercase().contains(query) ||
                    tx.category.displayNameDe.lowercase().contains(query) ||
                    tx.customCategoryName?.lowercase()?.contains(query) == true ||
                    tx.date.contains(query) ||
                    formatAmount(tx.amount, tx.currency).contains(query)
                }
            }

            // Sort
            result = when (sortOrder) {
                TransactionSortOrder.DATE_DESC -> result.sortedByDescending { parseTxDate(it.date)?.toEpochDays() }
                TransactionSortOrder.DATE_ASC -> result.sortedBy { parseTxDate(it.date)?.toEpochDays() }
                TransactionSortOrder.AMOUNT_DESC -> result.sortedByDescending { it.amount }
                TransactionSortOrder.AMOUNT_ASC -> result.sortedBy { it.amount }
                TransactionSortOrder.NAME_ASC -> result.sortedBy {
                    (it.counterparty ?: it.description).lowercase()
                }
            }

            result
        }
    }

    val isSearchActive = searchQuery.isNotBlank()
    val isFiltered = isSearchActive || timeFilter != TransactionTimeFilter.ALL
    val sortActive = sortOrder != TransactionSortOrder.DATE_DESC

    // Count text: always show DB total (accurate) unless search active
    val transactionCountText by remember(totalTransactionCount, filteredTransactions.size, isSearchActive) {
        derivedStateOf {
            if (!isSearchActive) {
                "$totalTransactionCount ${strings.transactions.lowercase()}"
            } else {
                "${filteredTransactions.size} ${strings.transactions.lowercase()} (${strings.filtered})"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Search field + Sort + Filter buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
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
            // Sort button
            IconButton(
                onClick = { showSortDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (sortActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_sort_desc),
                    contentDescription = "Sort",
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(
                        if (sortActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            // Filter button
            IconButton(
                onClick = { showFilterDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (timeFilter != TransactionTimeFilter.ALL) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_calendar),
                    contentDescription = "Filter",
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(
                        if (timeFilter != TransactionTimeFilter.ALL) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = transactionCountText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        // Scroll to top when sort order changes
        LaunchedEffect(sortOrder) {
            listState.animateScrollToItem(0)
        }

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
            // Detect when user scrolls near the bottom to trigger loading more
            if (hasMoreTransactions && onLoadMore != null) {
                LaunchedEffect(listState) {
                    snapshotFlow {
                        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val totalItems = listState.layoutInfo.totalItemsCount
                        lastVisibleIndex to totalItems
                    }
                    .distinctUntilChanged()
                    .collect { (lastVisible, total) ->
                        if (total > 0 && lastVisible >= total - 10) {
                            onLoadMore()
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredTransactions,
                    key = { it.id }
                ) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = if (onCategoryChange != null) {
                            { showCategoryPicker = transaction }
                        } else null
                    )
                }

                // Loading indicator at the bottom when loading more
                if (isLoadingMore) {
                    item(key = "loading_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }

    // Category picker dialog
    showCategoryPicker?.let { transaction ->
        CategoryPickerDialog(
            currentCategory = transaction.category,
            customCategories = customCategories,
            currentCustomCategoryId = null,
            onCategorySelected = { newCategory ->
                onCategoryChange?.invoke(transaction, newCategory)
                showCategoryPicker = null
            },
            onCustomCategorySelected = if (onCustomCategoryChange != null) { customCategoryId ->
                onCustomCategoryChange.invoke(transaction, customCategoryId)
                showCategoryPicker = null
            } else null,
            onManageCategories = if (onManageCategories != null) {
                {
                    showCategoryPicker = null
                    onManageCategories.invoke()
                }
            } else null,
            onDismiss = { showCategoryPicker = null }
        )
    }

    // Sort dialog
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort transactions", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        TransactionSortOrder.DATE_DESC to "Date: Newest first",
                        TransactionSortOrder.DATE_ASC to "Date: Oldest first",
                        TransactionSortOrder.AMOUNT_DESC to "Amount: Highest first",
                        TransactionSortOrder.AMOUNT_ASC to "Amount: Lowest first",
                        TransactionSortOrder.NAME_ASC to "Name: A → Z"
                    ).forEach { (order, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    sortOrder = order
                                    showSortDialog = false
                                }
                                .background(
                                    if (sortOrder == order) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sortOrder == order,
                                onClick = { sortOrder = order; showSortDialog = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) { Text("Close") }
            }
        )
    }

    // Filter dialog
    if (showFilterDialog) {
        val tz = TimeZone.currentSystemDefault()
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Filter by time", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        TransactionTimeFilter.ALL to "All time",
                        TransactionTimeFilter.WEEK to "This week",
                        TransactionTimeFilter.MONTH to "This month",
                        TransactionTimeFilter.YEAR to "This year",
                        TransactionTimeFilter.CUSTOM to "Custom range"
                    ).forEach { (filter, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (filter != TransactionTimeFilter.CUSTOM) {
                                        timeFilter = filter
                                        val today = Clock.System.todayIn(tz)
                                        val (start, end) = when (filter) {
                                            TransactionTimeFilter.ALL -> null to null
                                            TransactionTimeFilter.WEEK -> {
                                                val s = LocalDate.fromEpochDays(today.toEpochDays() - 6).atStartOfDayIn(tz).epochSeconds
                                                val e = today.atStartOfDayIn(tz).epochSeconds + 86399L
                                                s to e
                                            }
                                            TransactionTimeFilter.MONTH -> {
                                                val s = LocalDate(today.year, today.monthNumber, 1).atStartOfDayIn(tz).epochSeconds
                                                val nextMonth = if (today.monthNumber == 12) LocalDate(today.year + 1, 1, 1) else LocalDate(today.year, today.monthNumber + 1, 1)
                                                val e = nextMonth.atStartOfDayIn(tz).epochSeconds - 1L
                                                s to e
                                            }
                                            TransactionTimeFilter.YEAR -> {
                                                val s = LocalDate(today.year, 1, 1).atStartOfDayIn(tz).epochSeconds
                                                val e = LocalDate(today.year + 1, 1, 1).atStartOfDayIn(tz).epochSeconds - 1L
                                                s to e
                                            }
                                            TransactionTimeFilter.CUSTOM -> null to null
                                        }
                                        onDateRangeChange?.invoke(start, end)
                                        showFilterDialog = false
                                    } else {
                                        timeFilter = filter
                                        showFilterDialog = false
                                        showStartDatePicker = true
                                    }
                                }
                                .background(
                                    if (timeFilter == filter) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = timeFilter == filter,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("Close") }
            },
            dismissButton = {
                if (timeFilter != TransactionTimeFilter.ALL) {
                    TextButton(onClick = {
                        timeFilter = TransactionTimeFilter.ALL
                        onDateRangeChange?.invoke(null, null)
                        showFilterDialog = false
                    }) { Text("Clear") }
                }
            }
        )
    }

    // Start date picker
    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = startDatePickerState.selectedDateMillis
                    if (millis != null) {
                        pendingStartMillis = millis
                        showStartDatePicker = false
                        showEndDatePicker = true
                    }
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = startDatePickerState, title = { Text("Select start date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }

    // End date picker
    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = pendingStartMillis
                    val endMillis = endDatePickerState.selectedDateMillis
                    if (startMillis != null && endMillis != null) {
                        val startEpoch = startMillis / 1000L
                        val endEpoch = endMillis / 1000L + 86399L
                        onDateRangeChange?.invoke(startEpoch, endEpoch)
                    }
                    showEndDatePicker = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = endDatePickerState, title = { Text("Select end date", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
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
            // Category indicator - supports custom categories
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parseColor(transaction.effectiveCategoryColor).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (transaction.hasCustomCategory) transaction.effectiveCategoryIcon else getCategoryEmoji(transaction.category),
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
                // Category and date - supports custom categories
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (transaction.hasCustomCategory) transaction.effectiveCategoryName else transaction.category.getLocalizedName(strings),
                        style = MaterialTheme.typography.bodySmall,
                        color = parseColor(transaction.effectiveCategoryColor)
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
                    AppColors.Income // Green for positive
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
    /*val formatted = "%.2f".format(kotlin.math.abs(amount))
        .replace(".", ",") // German format*/

    val absAmount = kotlin.math.abs(amount)
    val rounded = kotlin.math.round(absAmount * 100) / 100
    val formatted = rounded.toString().replace(".", ",") // German format

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

private fun parseTxDate(dateStr: String): kotlinx.datetime.LocalDate? {
    return try {
        val parts = dateStr.split(".")
        if (parts.size == 3) {
            kotlinx.datetime.LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        } else null
    } catch (_: Exception) { null }
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
    customCategories: List<com.banking.statement.categorization.CustomCategory> = emptyList(),
    currentCustomCategoryId: Long? = null,
    onCategorySelected: (TransactionCategory) -> Unit,
    onCustomCategorySelected: ((Long) -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null,
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
                // Custom Categories Section (if any)
                if (customCategories.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.customCategories,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(customCategories) { customCategory ->
                        val isSelected = currentCustomCategoryId == customCategory.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCustomCategorySelected?.invoke(customCategory.id)
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    parseColor(customCategory.color).copy(alpha = 0.3f)
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
                                        .background(parseColor(customCategory.color).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = customCategory.icon,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = customCategory.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) parseColor(customCategory.color) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                // Predefined Categories Section
                item {
                    Text(
                        text = if (customCategories.isNotEmpty()) strings.predefinedCategories else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = if (customCategories.isNotEmpty()) 8.dp else 0.dp)
                    )
                }

                items(TransactionCategory.entries.filter { it != TransactionCategory.OTHER }) { category ->
                    val isSelected = category == currentCategory && currentCustomCategoryId == null
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
        dismissButton = {
            if (onManageCategories != null) {
                TextButton(onClick = onManageCategories) {
                    Text(strings.newCategory)
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
