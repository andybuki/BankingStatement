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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import kotlinx.coroutines.flow.distinctUntilChanged
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import bankingstatement.composeapp.generated.resources.share
import bankingstatement.composeapp.generated.resources.ic_sort_desc
import bankingstatement.composeapp.generated.resources.ic_calendar
import com.banking.statement.LocalStrings
import com.banking.statement.ui.components.CategoryAvatar
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.components.MoneyAmount
import com.banking.statement.ui.components.PeriodPills
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.export.ExportFormat
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.todayIn
import kotlin.math.roundToInt

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
    hasMoreTransactions: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf<TransactionDisplay?>(null) }

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
            .background(AppColors.SurfaceTint)
    ) {
        // Search + sort + filter chrome
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s2 + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.s2)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = strings.searchTransactions,
                        color = AppColors.TextTertiary,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(AppRadii.md),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = AppColors.TextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.CardBackground,
                    unfocusedContainerColor = AppColors.CardBackground,
                    focusedBorderColor = AppColors.Primary,
                    unfocusedBorderColor = AppColors.Divider
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Text("✕", color = AppColors.TextTertiary, fontSize = 14.sp)
                        }
                    }
                }
            )
            ChromeIconButton(
                active = sortActive,
                onClick = { showSortDialog = true },
                iconRes = Res.drawable.ic_sort_desc,
                contentDescription = "Sort"
            )
            ChromeIconButton(
                active = timeFilter != TransactionTimeFilter.ALL,
                onClick = { showFilterDialog = true },
                iconRes = Res.drawable.ic_calendar,
                contentDescription = "Filter"
            )
        }

        // Period pills + item count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.s4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val pillOptions = listOf(
                strings.periodWeek,
                strings.periodMonth,
                strings.periodYear,
                strings.periodAll
            )
            val pillSelected = when (timeFilter) {
                TransactionTimeFilter.WEEK -> strings.periodWeek
                TransactionTimeFilter.MONTH -> strings.periodMonth
                TransactionTimeFilter.YEAR -> strings.periodYear
                else -> strings.periodAll
            }
            val tz = TimeZone.currentSystemDefault()
            PeriodPills(
                selected = pillSelected,
                options = pillOptions,
                onSelect = { opt ->
                    val today = Clock.System.todayIn(tz)
                    when (opt) {
                        strings.periodWeek -> {
                            timeFilter = TransactionTimeFilter.WEEK
                            val s = LocalDate.fromEpochDays(today.toEpochDays() - 6).atStartOfDayIn(tz).epochSeconds
                            val e = today.atStartOfDayIn(tz).epochSeconds + 86399L
                            onDateRangeChange?.invoke(s, e)
                        }
                        strings.periodMonth -> {
                            timeFilter = TransactionTimeFilter.MONTH
                            val s = LocalDate(today.year, today.monthNumber, 1).atStartOfDayIn(tz).epochSeconds
                            val nextMonth = if (today.monthNumber == 12) LocalDate(today.year + 1, 1, 1)
                            else LocalDate(today.year, today.monthNumber + 1, 1)
                            val e = nextMonth.atStartOfDayIn(tz).epochSeconds - 1L
                            onDateRangeChange?.invoke(s, e)
                        }
                        strings.periodYear -> {
                            timeFilter = TransactionTimeFilter.YEAR
                            val s = LocalDate(today.year, 1, 1).atStartOfDayIn(tz).epochSeconds
                            val e = LocalDate(today.year + 1, 1, 1).atStartOfDayIn(tz).epochSeconds - 1L
                            onDateRangeChange?.invoke(s, e)
                        }
                        else -> {
                            timeFilter = TransactionTimeFilter.ALL
                            onDateRangeChange?.invoke(null, null)
                        }
                    }
                }
            )
            Text(
                text = transactionCountText,
                fontSize = 11.sp,
                color = AppColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.s3))

        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(sortOrder) {
            listState.animateScrollToItem(0)
        }

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = strings.noTransactions,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(AppSpacing.s1))
                    Text(
                        text = strings.importFirst,
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }
        } else {
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

            val dayGroups = remember(filteredTransactions) {
                filteredTransactions
                    .groupBy { it.date }
                    .map { (date, txs) -> date to txs }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = AppSpacing.s4,
                    end = AppSpacing.s4,
                    top = 0.dp,
                    bottom = AppSpacing.s6
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.s3 + 2.dp)
            ) {
                items(
                    items = dayGroups,
                    key = { it.first }
                ) { (date, txs) ->
                    DayGroupCard(
                        date = date,
                        txs = txs,
                        onClickTx = if (onCategoryChange != null) {
                            { tx -> showCategoryPicker = tx }
                        } else null
                    )
                }

                if (isLoadingMore) {
                    item(key = "loading_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppSpacing.s4),
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
private fun ChromeIconButton(
    active: Boolean,
    onClick: () -> Unit,
    iconRes: org.jetbrains.compose.resources.DrawableResource,
    contentDescription: String
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(AppRadii.md))
            .background(
                if (active) AppColors.Primary.copy(alpha = 0.12f)
                else AppColors.CardBackground
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(
                if (active) AppColors.Primary else AppColors.TextSecondary
            )
        )
    }
}

@Composable
private fun DayGroupCard(
    date: String,
    txs: List<TransactionDisplay>,
    onClickTx: ((TransactionDisplay) -> Unit)?
) {
    val total = txs.sumOf { it.amount }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EyebrowLabel(text = date)
            val sign = if (total >= 0) "+" else "−"
            val color = if (total >= 0) AppColors.Income else AppColors.Expenses
            Text(
                text = "$sign€${formatMoneyAbs2dp(total)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = AppElevations.xs,
                    shape = RoundedCornerShape(AppRadii.lg),
                    clip = false
                )
                .clip(RoundedCornerShape(AppRadii.lg))
                .background(AppColors.CardBackground)
        ) {
            txs.forEachIndexed { index, tx ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp)
                            .height(1.dp)
                            .background(AppColors.SurfaceTint)
                    )
                }
                TxRow(
                    transaction = tx,
                    onClick = if (onClickTx != null) {
                        { onClickTx(tx) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun TxRow(
    transaction: TransactionDisplay,
    onClick: (() -> Unit)? = null
) {
    val strings = LocalStrings.current
    val categoryName = if (transaction.hasCustomCategory) transaction.effectiveCategoryName
    else transaction.category.getLocalizedName(strings)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppSpacing.s4, vertical = AppSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
    ) {
        if (transaction.hasCustomCategory) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(parseColor(transaction.effectiveCategoryColor).copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = transaction.effectiveCategoryIcon,
                    fontSize = 18.sp
                )
            }
        } else {
            CategoryAvatar(category = transaction.category, size = 36.dp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = TransactionDisplay.extractDisplayName(transaction.counterparty, transaction.description),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            transaction.detailText?.let { detail ->
                Spacer(Modifier.height(1.dp))
                Text(
                    text = detail,
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = "$categoryName · ${transaction.date}",
                fontSize = 12.sp,
                color = AppColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        MoneyAmount(
            value = transaction.amount,
            size = 14,
            weight = FontWeight.SemiBold,
            currencySymbol = currencySymbol(transaction.currency)
        )
    }
}

private fun currencySymbol(currency: String): String = when (currency) {
    "EUR" -> "€"
    "USD" -> "$"
    "GBP" -> "£"
    else -> currency
}

private fun formatMoneyAbs2dp(value: Double): String {
    val cents = (kotlin.math.abs(value) * 100.0).roundToInt()
    val whole = cents / 100
    val frac = cents % 100
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    return "$wholeStr.${frac.toString().padStart(2, '0')}"
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

