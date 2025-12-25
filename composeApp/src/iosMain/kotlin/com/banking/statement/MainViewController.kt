@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.banking.statement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.ComposeUIViewController
import com.banking.statement.categorization.CategoryOverrideManager
import com.banking.statement.categorization.KeywordDatabase
import com.banking.statement.categorization.MerchantDatabase
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.db.DatabaseDriverFactory
import com.banking.statement.db.TransactionRepository
import com.banking.statement.ui.AccountManagementItem
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.ImportChoice
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import com.banking.statement.ui.theme.ThemeMode
import com.banking.statement.ui.theme.ThemePreferences
import com.banking.statement.export.ExportFormat
import com.banking.statement.export.SpendingExportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun MainViewController() = ComposeUIViewController {
    // Initialize state
    var importState by remember { mutableStateOf(ImportState()) }
    var stats by remember { mutableStateOf(DatabaseStats()) }
    var transactions by remember { mutableStateOf<List<TransactionDisplay>>(emptyList()) }
    var categorySpending by remember { mutableStateOf<List<CategorySpending>>(emptyList()) }
    var monthlySummary by remember { mutableStateOf<List<MonthlySummary>>(emptyList()) }
    var totalIncome by remember { mutableStateOf(0.0) }
    var totalExpenses by remember { mutableStateOf(0.0) }
    var accountsForManagement by remember { mutableStateOf<List<AccountManagementItem>>(emptyList()) }

    // Theme management
    val themePreferences = remember { ThemePreferences() }
    var currentThemeMode by remember { mutableStateOf(themePreferences.getThemeMode()) }

    // Initialize database driver
    val driverFactory = remember { DatabaseDriverFactory() }

    // Initialize keyword database for category matching
    val keywordDatabase = remember { KeywordDatabase() }

    // Initialize temporary repository to access database
    val tempRepository = remember { TransactionRepository(driverFactory) }

    // Initialize merchant database for improved categorization
    val merchantDatabase = remember { MerchantDatabase(tempRepository.database) }

    // Initialize category override manager for user corrections
    val categoryOverrideManager = remember {
        CategoryOverrideManager(tempRepository.database).apply {
            loadCache()
        }
    }

    // Initialize transaction categorizer
    val transactionCategorizer = remember {
        com.banking.statement.categorization.TransactionCategorizer(merchantDatabase, categoryOverrideManager)
    }

    // Initialize repository with categorizer for auto-categorization on import
    val repository = remember {
        TransactionRepository(driverFactory, transactionCategorizer)
    }

    val coroutineScope = rememberCoroutineScope()

    // Load data on first composition
    remember {
        coroutineScope.launch {
            // Load stats
            updateStats(repository) { newStats ->
                stats = newStats
            }

            // Load transaction data
            loadTransactionData(
                repository = repository,
                categoryOverrideManager = categoryOverrideManager,
                merchantDatabase = merchantDatabase,
                onTransactionsLoaded = { txList, catSpending, monthSummary, income, expenses ->
                    transactions = txList
                    categorySpending = catSpending
                    monthlySummary = monthSummary
                    totalIncome = income
                    totalExpenses = expenses
                }
            )

            // Load accounts data
            loadAccountsData(repository) { accounts ->
                accountsForManagement = accounts
            }
        }
    }

    App(
        // File picker not yet implemented for iOS
        onPickFile = null,
        importState = importState,
        stats = stats,
        transactions = transactions,
        categorySpending = categorySpending,
        monthlySummary = monthlySummary,
        totalIncome = totalIncome,
        totalExpenses = totalExpenses,
        dialogState = null,
        onImportChoice = null,
        onDismissSuccessDialog = null,
        accountsForManagement = accountsForManagement,
        onDeleteAccount = { accountId ->
            coroutineScope.launch {
                withContext(Dispatchers.Default) {
                    repository.deleteAccount(accountId)
                }
                updateStats(repository) { newStats -> stats = newStats }
                loadAccountsData(repository) { accounts -> accountsForManagement = accounts }
                loadTransactionData(
                    repository = repository,
                    categoryOverrideManager = categoryOverrideManager,
                    merchantDatabase = merchantDatabase,
                    onTransactionsLoaded = { txList, catSpending, monthSummary, income, expenses ->
                        transactions = txList
                        categorySpending = catSpending
                        monthlySummary = monthSummary
                        totalIncome = income
                        totalExpenses = expenses
                    }
                )
            }
        },
        onEditAccount = { accountId, newName ->
            coroutineScope.launch {
                withContext(Dispatchers.Default) {
                    repository.updateAccountName(accountId, newName)
                }
                loadAccountsData(repository) { accounts -> accountsForManagement = accounts }
            }
        },
        onClearAllData = {
            coroutineScope.launch {
                withContext(Dispatchers.Default) {
                    repository.clearAllData()
                }
                updateStats(repository) { newStats -> stats = newStats }
                loadAccountsData(repository) { accounts -> accountsForManagement = accounts }
                loadTransactionData(
                    repository = repository,
                    categoryOverrideManager = categoryOverrideManager,
                    merchantDatabase = merchantDatabase,
                    onTransactionsLoaded = { txList, catSpending, monthSummary, income, expenses ->
                        transactions = txList
                        categorySpending = catSpending
                        monthlySummary = monthSummary
                        totalIncome = income
                        totalExpenses = expenses
                    }
                )
            }
        },
        // Share callbacks not yet implemented for iOS
        onShareTransactions = null,
        onShareSpending = null,
        themeMode = currentThemeMode,
        onThemeModeChange = { mode ->
            currentThemeMode = mode
            themePreferences.setThemeMode(mode)
        },
        onCategoryChange = { transaction, newCategory ->
            coroutineScope.launch {
                // Update UI immediately - change all transactions with same match key
                val matchKey = getTransactionMatchKey(transaction)
                transactions = transactions.map { tx ->
                    val txMatchKey = getTransactionMatchKey(tx)
                    if (txMatchKey == matchKey) {
                        tx.copy(category = newCategory)
                    } else {
                        tx
                    }
                }

                // Update category spending
                updateCategorySpending(transactions) { catSpending, income, expenses ->
                    categorySpending = catSpending
                    totalIncome = income
                    totalExpenses = expenses
                }

                // Save to database in background
                withContext(Dispatchers.Default) {
                    categoryOverrideManager.saveOverride(
                        description = transaction.description,
                        counterparty = transaction.counterparty,
                        category = newCategory
                    )
                }
            }
        }
    )
}

private suspend fun updateStats(
    repository: TransactionRepository,
    onUpdate: (DatabaseStats) -> Unit
) {
    val statementsCount = withContext(Dispatchers.Default) {
        repository.getStatementCount().toInt()
    }
    val transactionsCount = withContext(Dispatchers.Default) {
        repository.getTransactionCount().toInt()
    }
    val accountsCount = withContext(Dispatchers.Default) {
        repository.getAccountCount().toInt()
    }
    onUpdate(DatabaseStats(
        totalStatements = statementsCount,
        totalTransactions = transactionsCount,
        totalAccounts = accountsCount
    ))
}

private suspend fun loadTransactionData(
    repository: TransactionRepository,
    categoryOverrideManager: CategoryOverrideManager,
    merchantDatabase: MerchantDatabase,
    onTransactionsLoaded: (
        List<TransactionDisplay>,
        List<CategorySpending>,
        List<MonthlySummary>,
        Double,
        Double
    ) -> Unit
) {
    withContext(Dispatchers.Default) {
        val allTransactions = repository.getAllTransactions()

        // Get account names map for display
        val accountNames = repository.getAccountSummary().associate { it.id to it.name }

        // Convert DB transactions to display format with categorization
        val txList = allTransactions.map { tx ->
            // Priority: 1) User overrides, 2) Saved category, 3) Recalculate
            val category = categoryOverrideManager.findOverride(tx.description, tx.counterparty_name)
                ?: run {
                    // Use saved category if available
                    if (!tx.auto_category.isNullOrBlank()) {
                        TransactionCategory.entries.find { it.name == tx.auto_category }
                            ?: TransactionCategory.OTHER
                    } else {
                        // Fall back to recalculation for old data without saved categories
                        TransactionCategory.categorize(tx.description, tx.counterparty_name).let { keywordCategory ->
                            if (keywordCategory != TransactionCategory.OTHER) {
                                keywordCategory
                            } else if (tx.amount < 0) {
                                // Only use merchant DB for expenses
                                merchantDatabase.findCategory(tx.description, tx.counterparty_name)
                                    ?: TransactionCategory.OTHER
                            } else {
                                TransactionCategory.OTHER
                            }
                        }
                    }
                }
            val date = Instant.fromEpochSeconds(tx.booking_date)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

            TransactionDisplay(
                id = tx.id,
                date = "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}",
                description = tx.description,
                amount = tx.amount,
                currency = tx.currency,
                category = category,
                counterparty = tx.counterparty_name,
                accountId = tx.account_id ?: 0L,
                accountName = tx.account_id?.let { accountNames[it] } ?: ""
            )
        }

        // Calculate category spending
        val spendingByCategory = txList
            .filter { it.amount < 0 } // Only expenses
            .groupBy { it.category }
            .mapValues { (_, txs) ->
                txs.sumOf { it.amount }
            }

        val totalExpensesAmount = spendingByCategory.values.sum()

        val catSpending = spendingByCategory.map { (category, total) ->
            CategorySpending(
                category = category,
                totalAmount = total,
                transactionCount = txList.count { it.category == category && it.amount < 0 },
                percentage = if (totalExpensesAmount != 0.0) {
                    ((total / totalExpensesAmount) * 100).toFloat()
                } else 0f
            )
        }.sortedBy { it.totalAmount }

        // Calculate totals
        val expenses = allTransactions.filter { it.amount < 0 }.sumOf { it.amount }
        val income = allTransactions.filter { it.amount > 0 }.sumOf { it.amount }

        // Calculate monthly summary
        val monthlyData = allTransactions.groupBy { tx ->
            val date = Instant.fromEpochSeconds(tx.booking_date)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
            "${date.year}-${date.monthNumber.toString().padStart(2, '0')}"
        }

        val monthSummary = monthlyData.map { (month, txs) ->
            val monthIncome = txs.filter { it.amount > 0 }.sumOf { it.amount }
            val monthExpenses = txs.filter { it.amount < 0 }.sumOf { it.amount }
            MonthlySummary(
                month = formatMonth(month),
                income = monthIncome,
                expenses = monthExpenses
            )
        }.sortedByDescending { it.month }

        onTransactionsLoaded(txList, catSpending, monthSummary, income, expenses)
    }
}

private suspend fun loadAccountsData(
    repository: TransactionRepository,
    onAccountsLoaded: (List<AccountManagementItem>) -> Unit
) {
    withContext(Dispatchers.Default) {
        val accountSummaries = repository.getAccountSummary()
        val accounts = accountSummaries.map { summary ->
            val statementCount = repository.getStatementCountByAccount(summary.id)
            AccountManagementItem(
                id = summary.id,
                name = summary.name,
                bankName = summary.bank_name,
                iban = summary.iban,
                color = summary.color,
                transactionCount = summary.transaction_count,
                statementCount = statementCount,
                balance = summary.balance
            )
        }
        onAccountsLoaded(accounts)
    }
}

private fun formatMonth(yearMonth: String): String {
    val parts = yearMonth.split("-")
    if (parts.size != 2) return yearMonth
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )
    val monthIndex = parts[1].toIntOrNull()?.minus(1) ?: return yearMonth
    return if (monthIndex in 0..11) {
        "${monthNames[monthIndex]} ${parts[0]}"
    } else yearMonth
}

/**
 * Get a matching key for grouping identical transactions.
 * Uses the full normalized counterparty or description - exact match only.
 */
private fun getTransactionMatchKey(transaction: TransactionDisplay): String {
    // Normalize function - remove special chars, lowercase, trim
    fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9äöüß ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // Use counterparty if available, otherwise description
    val counterparty = transaction.counterparty
    return if (!counterparty.isNullOrBlank()) {
        normalize(counterparty)
    } else {
        normalize(transaction.description)
    }
}

private fun updateCategorySpending(
    transactions: List<TransactionDisplay>,
    onUpdate: (List<CategorySpending>, Double, Double) -> Unit
) {
    // Recalculate category spending from current transactions
    val spendingByCategory = transactions
        .filter { it.amount < 0 }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

    val totalExpensesAmount = spendingByCategory.values.sum()

    val catSpending = spendingByCategory.map { (category, total) ->
        CategorySpending(
            category = category,
            totalAmount = total,
            transactionCount = transactions.count { it.category == category && it.amount < 0 },
            percentage = if (totalExpensesAmount != 0.0) {
                (total / totalExpensesAmount * 100).toFloat()
            } else 0f
        )
    }.sortedBy { it.totalAmount }

    // Update totals
    val expenses = transactions.filter { it.amount < 0 }.sumOf { it.amount }
    val income = transactions.filter { it.amount > 0 }.sumOf { it.amount }

    onUpdate(catSpending, income, expenses)
}
