@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.banking.statement.db

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/**
 * Result of checking if an import matches an existing account
 */
sealed class AccountMatchResult {
    /** IBAN matches an existing account - auto-add */
    data class IbanMatch(val account: Accounts) : AccountMatchResult()

    /** Same bank found but different/no IBAN - ask user */
    data class BankMatch(val account: Accounts) : AccountMatchResult()

    /** No matching account found - new bank */
    data object NoMatch : AccountMatchResult()
}

/**
 * Result of an import operation
 */
data class ImportResult(
    val statementId: Long,
    val accountId: Long,
    val transactionsImported: Int,
    val duplicatesSkipped: Int,
    val isNewAccount: Boolean,
    val isDuplicateStatement: Boolean = false
)

class TransactionRepository(
    driverFactory: DatabaseDriverFactory,
    transactionCategorizer: com.banking.statement.categorization.TransactionCategorizer? = null
) {
    val database = BankingDatabase(driverFactory.createDriver())
    private val queries = database.bankingDatabaseQueries

    /**
     * The categorizer can be set after construction to break the circular dependency:
     * Repository creates Database -> Database needed for MerchantDB/OverrideManager -> those create Categorizer -> Categorizer needed by Repository
     */
    var transactionCategorizer: com.banking.statement.categorization.TransactionCategorizer? = transactionCategorizer

    // ==================== Account Operations ====================

    /**
     * Check if the import matches an existing account
     */
    fun findMatchingAccount(bankName: String, iban: String?): AccountMatchResult {
        // First, try to match by IBAN (exact match)
        if (!iban.isNullOrBlank()) {
            val ibanMatch = queries.getAccountByIban(iban).executeAsOneOrNull()
            if (ibanMatch != null) {
                return AccountMatchResult.IbanMatch(ibanMatch)
            }
        }

        // Then, try to match by bank name
        val bankMatch = queries.getAccountByBankName(bankName).executeAsOneOrNull()
        if (bankMatch != null) {
            return AccountMatchResult.BankMatch(bankMatch)
        }

        return AccountMatchResult.NoMatch
    }

    /**
     * Get all accounts
     */
    fun getAllAccounts(): List<Accounts> {
        return queries.getAllAccounts().executeAsList()
    }

    /**
     * Get account summary with balances
     */
    fun getAccountSummary(): List<GetAccountSummary> {
        return queries.getAccountSummary().executeAsList()
    }

    /**
     * Create a new account
     */
    fun createAccount(
        name: String,
        bankName: String,
        iban: String?,
        currency: String = "EUR",
        color: String? = null,
        icon: String? = null
    ): Long {
        queries.insertAccount(
            name = name,
            bank_name = bankName,
            iban = iban,
            currency = currency,
            color = color,
            icon = icon
        )
        return queries.getLastInsertedAccountId().executeAsOne()
    }

    /**
     * Update an existing account
     */
    fun updateAccount(accountId: Long, name: String, color: String?, icon: String?) {
        queries.updateAccount(name, color, icon, accountId)
    }

    /**
     * Deactivate an account (soft delete)
     */
    fun deactivateAccount(accountId: Long) {
        queries.deactivateAccount(accountId)
    }

    /**
     * Delete an account and all its data (hard delete)
     */
    fun deleteAccount(accountId: Long) {
        // Delete transactions first
        queries.deleteTransactionsByAccount(accountId)
        // Delete statements
        queries.deleteStatementsByAccount(accountId)
        // Delete account
        queries.deleteAccount(accountId)
    }

    /**
     * Clear all data from the database
     */
    fun clearAllData() {
        queries.deleteAllTransactions()
        queries.deleteAllStatements()
        queries.deleteAllAccounts()
    }

    /**
     * Update account name
     */
    fun updateAccountName(accountId: Long, newName: String) {
        val account = queries.getAccountById(accountId).executeAsOneOrNull()
        if (account != null) {
            queries.updateAccount(newName, account.color, account.icon, accountId)
        }
    }

    /**
     * Get account count
     */
    fun getAccountCount(): Long {
        return queries.getAccountCount().executeAsOne()
    }

    /**
     * Get statement count for an account
     */
    fun getStatementCountByAccount(accountId: Long): Long {
        return queries.getStatementCountByAccount(accountId).executeAsOne()
    }

    // ==================== Import Operations ====================

    /**
     * Save import to an existing account
     */
    fun saveImportToAccount(
        accountId: Long,
        parseResult: ParseResult,
        fileName: String,
        filePath: String?,
        sourceType: String
    ): ImportResult {
        // Check for duplicate statement first
        val existingStatement = queries.findDuplicateStatement(
            account_id = accountId,
            file_name = fileName
        ).executeAsOneOrNull()

        if (existingStatement != null) {
            // Statement already exists - don't create a duplicate
            return ImportResult(
                statementId = existingStatement,
                accountId = accountId,
                transactionsImported = 0,
                duplicatesSkipped = parseResult.transactions.size,
                isNewAccount = false,
                isDuplicateStatement = true
            )
        }

        val importDate = Clock.System.now().epochSeconds

        // Insert statement record
        queries.insertStatement(
            account_id = accountId,
            file_name = fileName,
            file_path = filePath,
            source_type = sourceType,
            bank_name = parseResult.bankName,
            account_iban = parseResult.accountIban,
            import_date = importDate,
            statement_period = parseResult.statementPeriod
        )

        val statementId = queries.getLastInsertedStatementId().executeAsOne()

        // Insert all transactions with duplicate detection
        var imported = 0
        var duplicates = 0

        parseResult.transactions.forEach { transaction ->
            val isDuplicate = checkDuplicate(accountId, transaction)
            if (!isDuplicate) {
                insertTransaction(statementId, accountId, transaction, isDuplicate = false)
                imported++
            } else {
                // Still insert but mark as duplicate for reference
                insertTransaction(statementId, accountId, transaction, isDuplicate = true)
                duplicates++
            }
        }

        return ImportResult(
            statementId = statementId,
            accountId = accountId,
            transactionsImported = imported,
            duplicatesSkipped = duplicates,
            isNewAccount = false
        )
    }

    /**
     * Save import and create a new account
     */
    fun saveImportWithNewAccount(
        accountName: String,
        parseResult: ParseResult,
        fileName: String,
        filePath: String?,
        sourceType: String,
        accountColor: String? = null
    ): ImportResult {
        // Create new account
        val accountId = createAccount(
            name = accountName,
            bankName = parseResult.bankName,
            iban = parseResult.accountIban,
            currency = parseResult.transactions.firstOrNull()?.currency ?: "EUR",
            color = accountColor
        )

        // Save the import
        val result = saveImportToAccount(
            accountId = accountId,
            parseResult = parseResult,
            fileName = fileName,
            filePath = filePath,
            sourceType = sourceType
        )

        return result.copy(isNewAccount = true)
    }

    /**
     * Check if a transaction is a duplicate
     */
    private fun checkDuplicate(accountId: Long, transaction: ParsedTransaction): Boolean {
        val existing = queries.findDuplicateTransaction(
            account_id = accountId,
            booking_date = transaction.bookingDate.toEpochSeconds(),
            amount = transaction.amount,
            description = transaction.description
        ).executeAsOneOrNull()

        return existing != null
    }

    private fun insertTransaction(
        statementId: Long,
        accountId: Long,
        transaction: ParsedTransaction,
        isDuplicate: Boolean
    ) {
        // Calculate category using categorizer if available
        val autoCategory = transactionCategorizer?.categorize(transaction)?.name

        queries.insertTransaction(
            statement_id = statementId,
            account_id = accountId,
            transaction_id = transaction.transactionId,
            booking_date = transaction.bookingDate.toEpochSeconds(),
            value_date = transaction.valueDate?.toEpochSeconds(),
            amount = transaction.amount,
            currency = transaction.currency,
            balance = transaction.balance,
            description = transaction.description,
            counterparty_name = transaction.counterpartyName,
            counterparty_iban = transaction.counterpartyIban,
            remittance_info = transaction.remittanceInfo,
            transaction_type = transaction.transactionType,
            bank_transaction_code = transaction.bankTransactionCode,
            category_id = null,
            auto_category = autoCategory,
            raw_text = transaction.rawText,
            is_duplicate = if (isDuplicate) 1L else 0L
        )
    }

    // ==================== Statement Operations ====================

    fun getAllStatements(): List<Statements> {
        return queries.getAllStatements().executeAsList()
    }

    fun getStatementsByAccount(accountId: Long): List<Statements> {
        return queries.getStatementsByAccount(accountId).executeAsList()
    }

    fun deleteStatement(statementId: Long) {
        queries.deleteTransactionsByStatement(statementId)
        queries.deleteStatement(statementId)
    }

    fun getStatementCount(): Long {
        return queries.getStatementCount().executeAsOne()
    }

    fun getLatestImportDate(): Long? {
        return queries.getLatestImportDate().executeAsOneOrNull()?.MAX
    }

    // ==================== Transaction Operations ====================

    fun getAllTransactions(): List<Transactions> {
        return queries.getAllTransactions().executeAsList()
    }

    fun getTransactionsPaged(limit: Long, offset: Long): List<Transactions> {
        return queries.getTransactionsPaged(limit, offset).executeAsList()
    }

    fun getTransactionsByAccount(accountId: Long): List<Transactions> {
        return queries.getTransactionsByAccount(accountId).executeAsList()
    }

    fun getTransactionsByStatement(statementId: Long): List<Transactions> {
        return queries.getTransactionsByStatement(statementId).executeAsList()
    }

    fun getTransactionsByDateRange(startDate: LocalDate, endDate: LocalDate): List<Transactions> {
        return queries.getTransactionsByDateRange(
            startDate.toEpochSeconds(),
            endDate.toEpochSeconds()
        ).executeAsList()
    }

    fun getTransactionCount(): Long {
        return queries.getTransactionCount().executeAsOne()
    }

    fun updateTransactionCategory(transactionId: Long, categoryId: Long?, categoryName: String?) {
        queries.updateTransactionCategory(categoryId, categoryName, transactionId)
    }

    // ==================== Category Operations ====================

    fun getAllCategories(): List<Categories> {
        return queries.getAllCategories().executeAsList()
    }

    fun insertCategory(name: String, icon: String?, color: String?) {
        queries.insertCategory(name, icon, color, null)
    }

    /**
     * Insert a new custom category and return its ID
     */
    fun insertCategoryAndGetId(name: String, icon: String?, color: String?): Long {
        queries.insertCategory(name, icon, color, null)
        return queries.getLastInsertedCategoryId().executeAsOne()
    }

    /**
     * Get a category by ID
     */
    fun getCategoryById(id: Long): Categories? {
        return queries.getCategoryById(id).executeAsOneOrNull()
    }

    /**
     * Get a category by name
     */
    fun getCategoryByName(name: String): Categories? {
        return queries.getCategoryByName(name).executeAsOneOrNull()
    }

    /**
     * Update an existing category
     */
    fun updateCategory(id: Long, name: String, icon: String?, color: String?) {
        queries.updateCategory(name, icon, color, id)
    }

    /**
     * Delete a custom category and reset affected transactions to "other" category
     */
    fun deleteCategory(id: Long) {
        // First, reset all transactions with this category to "OTHER"
        queries.resetTransactionsCategoryToOther(id)
        // Then delete the category
        queries.deleteCategory(id)
    }

    // ==================== Summary Operations ====================

    fun getMonthlySpending(): List<GetMonthlySpending> {
        return queries.getMonthlySpending().executeAsList()
    }

    fun getMonthlySpendingByAccount(accountId: Long): List<GetMonthlySpendingByAccount> {
        return queries.getMonthlySpendingByAccount(accountId).executeAsList()
    }

    fun getTotalBalance(): Double {
        return queries.getTotalBalance().executeAsOneOrNull()?.total ?: 0.0
    }

    fun getTotalBalanceByAccount(accountId: Long): Double {
        return queries.getTotalBalanceByAccount(accountId).executeAsOneOrNull()?.total ?: 0.0
    }

    /**
     * Get category spending totals via SQL aggregation (avoids loading all transactions)
     */
    fun getCategorySpendingTotals(): List<GetCategorySpendingTotals> {
        return queries.getCategorySpendingTotals().executeAsList()
    }

    /**
     * Get income/expenses totals via SQL aggregation
     */
    fun getIncomeExpensesTotals(): GetIncomeExpensesTotals? {
        return queries.getIncomeExpensesTotals().executeAsOneOrNull()
    }

    /**
     * Get category spending grouped by month for trend analysis
     */
    fun getCategorySpendingByMonth(): List<GetCategorySpendingByMonth> {
        return queries.getCategorySpendingByMonth().executeAsList()
    }

    /**
     * Get category spending grouped by month for a specific account
     */
    fun getCategorySpendingByMonthAndAccount(accountId: Long): List<GetCategorySpendingByMonthAndAccount> {
        return queries.getCategorySpendingByMonthAndAccount(accountId).executeAsList()
    }

    /**
     * Get merchant spending trends (e.g., Lidl, Edeka, REWE per month)
     */
    fun getMerchantSpendingByMonth(): List<GetMerchantSpendingByMonth> {
        return queries.getMerchantSpendingByMonth().executeAsList()
    }

    /**
     * Get top merchants by total spending
     */
    fun getTopMerchants(): List<GetTopMerchants> {
        return queries.getTopMerchants().executeAsList()
    }

    /**
     * Backfill auto_category for existing transactions that don't have it set.
     * This is needed after the category persistence feature was added.
     */
    fun backfillAutoCategories(): Int {
        val categorizer = transactionCategorizer ?: return 0

        // Get all transactions without auto_category
        val transactions = queries.getAllTransactions().executeAsList()
            .filter { it.auto_category.isNullOrBlank() }

        println("🔄 Backfilling ${transactions.size} transactions without auto_category")

        // Update each transaction with calculated category
        transactions.forEach { tx ->
            val parsedTx = com.banking.statement.parser.ParsedTransaction(
                transactionId = tx.transaction_id,
                bookingDate = kotlinx.datetime.Instant.fromEpochSeconds(tx.booking_date)
                    .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date,
                valueDate = tx.value_date?.let {
                    kotlinx.datetime.Instant.fromEpochSeconds(it)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                },
                amount = tx.amount,
                currency = tx.currency,
                balance = tx.balance,
                description = tx.description,
                counterpartyName = tx.counterparty_name,
                counterpartyIban = tx.counterparty_iban,
                remittanceInfo = tx.remittance_info,
                transactionType = tx.transaction_type,
                bankTransactionCode = tx.bank_transaction_code,
                rawText = tx.raw_text
            )

            val category = categorizer.categorize(parsedTx)
            queries.updateTransactionCategory(null, category.name, tx.id)
        }

        println("✅ Backfilled ${transactions.size} transactions")
        return transactions.size
    }

    /**
     * Fix miscategorized supermarket transactions.
     * Due to a bug in the keyword matching algorithm, some supermarket transactions
     * (like Lidl, REWE, Edeka) were incorrectly categorized as RESTAURANT.
     *
     * This migration:
     * 1. Finds all RESTAURANT transactions containing well-known supermarket names
     * 2. Re-categorizes them using the improved keyword matching
     * 3. Updates the auto_category field
     */
    fun fixMiscategorizedSupermarkets(): Int {
        val categorizer = transactionCategorizer ?: return 0

        // Well-known supermarket keywords that should never be RESTAURANT
        val supermarketKeywords = listOf(
            "lidl", "rewe", "edeka", "aldi", "penny", "netto", "kaufland"
        )

        // Get all transactions categorized as RESTAURANT
        val transactions = queries.getAllTransactions().executeAsList()
            .filter { it.auto_category == "RESTAURANT" }

        var fixed = 0

        transactions.forEach { tx ->
            // Check if description or counterparty contains any supermarket keyword
            val searchText = "${tx.description.lowercase()} ${tx.counterparty_name?.lowercase() ?: ""}"
            val containsSupermarket = supermarketKeywords.any { keyword ->
                // Use word-boundary matching
                val words = searchText
                    .replace(Regex("[^a-z0-9äöüß]"), " ")
                    .split(" ")
                    .filter { it.isNotBlank() }
                words.contains(keyword)
            }

            if (containsSupermarket) {
                // Re-categorize using improved algorithm
                val parsedTx = com.banking.statement.parser.ParsedTransaction(
                    transactionId = tx.transaction_id,
                    bookingDate = kotlinx.datetime.Instant.fromEpochSeconds(tx.booking_date)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date,
                    valueDate = tx.value_date?.let {
                        kotlinx.datetime.Instant.fromEpochSeconds(it)
                            .toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date
                    },
                    amount = tx.amount,
                    currency = tx.currency,
                    balance = tx.balance,
                    description = tx.description,
                    counterpartyName = tx.counterparty_name,
                    counterpartyIban = tx.counterparty_iban,
                    remittanceInfo = tx.remittance_info,
                    transactionType = tx.transaction_type,
                    bankTransactionCode = tx.bank_transaction_code,
                    rawText = tx.raw_text
                )

                val newCategory = categorizer.categorize(parsedTx)

                // Only update if category actually changed
                if (newCategory.name != tx.auto_category) {
                    queries.updateTransactionCategory(null, newCategory.name, tx.id)
                    println("  ✓ Fixed: ${tx.description} → ${newCategory.name}")
                    fixed++
                }
            }
        }

        if (fixed > 0) {
            println("✅ Fixed $fixed miscategorized supermarket transactions")
        } else {
            println("✅ No miscategorized supermarket transactions found")
        }

        return fixed
    }

    // ==================== Helper Functions ====================

    private fun LocalDate.toEpochSeconds(): Long {
        return this.atStartOfDayIn(TimeZone.UTC).epochSeconds
    }
}
