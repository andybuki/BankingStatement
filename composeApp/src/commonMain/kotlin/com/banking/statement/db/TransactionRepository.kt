package com.banking.statement.db

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class TransactionRepository(
    driverFactory: DatabaseDriverFactory
) {
    private val database = BankingDatabase(driverFactory.createDriver())
    private val queries = database.bankingDatabaseQueries

    fun saveImport(
        parseResult: ParseResult,
        fileName: String,
        filePath: String?,
        sourceType: String
    ): Long {
        val importDate = Clock.System.now().epochSeconds

        // Insert statement record
        queries.insertStatement(
            file_name = fileName,
            file_path = filePath,
            source_type = sourceType,
            bank_name = parseResult.bankName,
            account_iban = parseResult.accountIban,
            import_date = importDate,
            statement_period = parseResult.statementPeriod
        )

        val statementId = queries.getLastInsertedStatementId().executeAsOne()

        // Insert all transactions
        parseResult.transactions.forEach { transaction ->
            insertTransaction(statementId, transaction)
        }

        return statementId
    }

    private fun insertTransaction(statementId: Long, transaction: ParsedTransaction) {
        queries.insertTransaction(
            statement_id = statementId,
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
            raw_text = transaction.rawText
        )
    }

    fun getAllStatements(): List<Statements> {
        return queries.getAllStatements().executeAsList()
    }

    fun getTransactionsByStatement(statementId: Long): List<Transactions> {
        return queries.getTransactionsByStatement(statementId).executeAsList()
    }

    fun getAllTransactions(): List<Transactions> {
        return queries.getAllTransactions().executeAsList()
    }

    fun getTransactionsByDateRange(startDate: LocalDate, endDate: LocalDate): List<Transactions> {
        return queries.getTransactionsByDateRange(
            startDate.toEpochSeconds(),
            endDate.toEpochSeconds()
        ).executeAsList()
    }

    fun deleteStatement(statementId: Long) {
        queries.deleteTransactionsByStatement(statementId)
        queries.deleteStatement(statementId)
    }

    fun getTransactionCount(): Long {
        return queries.getAllTransactions().executeAsList().size.toLong()
    }

    fun getStatementCount(): Long {
        return queries.getAllStatements().executeAsList().size.toLong()
    }

    // Category operations
    fun getAllCategories(): List<Categories> {
        return queries.getAllCategories().executeAsList()
    }

    fun insertCategory(name: String, icon: String?, color: String?) {
        queries.insertCategory(name, icon, color, null)
    }

    fun updateTransactionCategory(transactionId: Long, categoryId: Long?) {
        queries.updateTransactionCategory(categoryId, transactionId)
    }

    // Summary queries
    fun getMonthlySpending(): List<GetMonthlySpending> {
        return queries.getMonthlySpending().executeAsList()
    }

    private fun LocalDate.toEpochSeconds(): Long {
        return this.atStartOfDayIn(TimeZone.UTC).epochSeconds
    }
}
