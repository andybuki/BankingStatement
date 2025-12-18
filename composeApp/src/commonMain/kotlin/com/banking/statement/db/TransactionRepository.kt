@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.banking.statement.db

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

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
    val isNewAccount: Boolean
)

class TransactionRepository(
    driverFactory: DatabaseDriverFactory
) {
    private val database = BankingDatabase(driverFactory.createDriver())
    private val queries = database.bankingDatabaseQueries

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

    // ==================== Transaction Operations ====================

    fun getAllTransactions(): List<Transactions> {
        return queries.getAllTransactions().executeAsList()
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

    fun updateTransactionCategory(transactionId: Long, categoryId: Long?) {
        queries.updateTransactionCategory(categoryId, transactionId)
    }

    // ==================== Category Operations ====================

    fun getAllCategories(): List<Categories> {
        return queries.getAllCategories().executeAsList()
    }

    fun insertCategory(name: String, icon: String?, color: String?) {
        queries.insertCategory(name, icon, color, null)
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

    // ==================== Helper Functions ====================

    private fun LocalDate.toEpochSeconds(): Long {
        return this.atStartOfDayIn(TimeZone.UTC).epochSeconds
    }
}
