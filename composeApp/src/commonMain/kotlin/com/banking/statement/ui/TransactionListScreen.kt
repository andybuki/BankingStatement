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
    selectedSortOrder: TransactionSortOrder = TransactionSortOrder.DATE_DESC,
    onSortOrderChange: ((TransactionSortOrder) -> Unit)? = null,
    customCategories: List<com.banking.statement.categorization.CustomCategory> = emptyList(),
    onBackClick: (() -> Unit)? = null,
    onShare: ((ExportFormat, List<TransactionDisplay>, String?) -> Unit)? = null,
    onCategoryChange: ((TransactionDisplay, TransactionCategory) -> Unit)? = null,
    onCustomCategoryChange: ((TransactionDisplay, Long) -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null,
    onViewSourcePdf: ((TransactionDisplay) -> Unit)? = null,
    hasMoreTransactions: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf<TransactionDisplay?>(null) }
    var showActionsFor by remember { mutableStateOf<TransactionDisplay?>(null) }

    // Sort state is hoisted (DB-level sorting). timeFilter stays local for UI.
    val sortOrder = selectedSortOrder
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

    // Transactions are DB-filtered AND DB-sorted; only apply search locally
    val filteredTransactions by remember(transactions, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                transactions
            } else {
                val query = searchQuery.lowercase().trim()
                transactions.filter { tx ->
                    tx.description.lowercase().contains(query) ||
                    tx.counterparty?.lowercase()?.contains(query) == true ||
                    tx.category.displayName.lowercase().contains(query) ||
                    tx.category.displayNameDe.lowercase().contains(query) ||
                    tx.customCategoryName?.lowercase()?.contains(query) == true ||
                    tx.date.contains(query) ||
                    formatAmount(tx.amount, tx.currency).contains(query)
                }
            }
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
                            {
                                // Single tap opens the actions sheet with
                                // "Change category" + "View source PDF" (if
                                // available). The user taps the row once and
                                // picks their action instead of jumping
                                // straight into the category picker.
                                showActionsFor = transaction
                            }
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

    // Tap-a-row actions sheet: Change category / View source PDF.
    showActionsFor?.let { transaction ->
        TransactionActionsDialog(
            transaction = transaction,
            canViewSourcePdf = onViewSourcePdf != null && transaction.hasSourcePdf,
            onChangeCategory = {
                showActionsFor = null
                showCategoryPicker = transaction
            },
            onViewSourcePdf = {
                showActionsFor = null
                onViewSourcePdf?.invoke(transaction)
            },
            onDismiss = { showActionsFor = null }
        )
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
                                    onSortOrderChange?.invoke(order)
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
                                onClick = {
                                    onSortOrderChange?.invoke(order)
                                    showSortDialog = false
                                }
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

internal fun parseColor(hexColor: String): Color {
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

