package com.banking.statement.export

import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Export format types
 */
enum class ExportFormat {
    CSV,
    PDF
}

/**
 * Export data type
 */
enum class ExportType {
    TRANSACTIONS,
    SPENDING
}

/**
 * Result of an export operation
 */
data class ExportResult(
    val success: Boolean,
    val filePath: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val errorMessage: String? = null
)

/**
 * Data for spending export
 */
data class SpendingExportData(
    val totalIncome: Double,
    val totalExpenses: Double,
    val categorySpending: List<CategorySpending>,
    val monthlySummary: List<MonthlySummary>
)

/**
 * Manager for generating export content
 */
object ExportManager {

    /**
     * Generate CSV content for transactions
     */
    fun generateTransactionsCsv(
        transactions: List<TransactionDisplay>,
        accountName: String? = null
    ): String {
        val builder = StringBuilder()

        // Header
        builder.appendLine("Date,Description,Counterparty,Category,Amount,Currency,Account")

        // Filter by account if specified
        val filteredTransactions = if (accountName != null && accountName != "All Accounts") {
            transactions.filter { it.accountName == accountName }
        } else {
            transactions
        }

        // Data rows
        for (tx in filteredTransactions) {
            val escapedDescription = escapeCsvField(tx.description)
            val escapedCounterparty = escapeCsvField(tx.counterparty ?: "")
            val escapedCategory = escapeCsvField(tx.category.displayName)
            val escapedAccount = escapeCsvField(tx.accountName)

            builder.appendLine(
                "${tx.date},$escapedDescription,$escapedCounterparty,$escapedCategory,${tx.amount},${tx.currency},$escapedAccount"
            )
        }

        return builder.toString()
    }

    /**
     * Generate CSV content for spending overview
     */
    fun generateSpendingCsv(data: SpendingExportData): String {
        val builder = StringBuilder()

        // Summary section
        builder.appendLine("=== SPENDING OVERVIEW ===")
        builder.appendLine()
        builder.appendLine("Summary")
        builder.appendLine("Type,Amount")
        builder.appendLine("Total Income,${formatAmount(data.totalIncome)}")
        builder.appendLine("Total Expenses,${formatAmount(data.totalExpenses)}")
        builder.appendLine("Net Balance,${formatAmount(data.totalIncome + data.totalExpenses)}")
        builder.appendLine()

        // Category spending section
        builder.appendLine("=== SPENDING BY CATEGORY ===")
        builder.appendLine()
        builder.appendLine("Category,Amount,Transaction Count,Percentage")
        for (category in data.categorySpending.sortedBy { it.totalAmount }) {
            builder.appendLine(
                "${escapeCsvField(category.category.displayName)},${formatAmount(category.totalAmount)},${category.transactionCount},${category.percentage.toInt()}%"
            )
        }
        builder.appendLine()

        // Monthly summary section
        builder.appendLine("=== MONTHLY SUMMARY ===")
        builder.appendLine()
        builder.appendLine("Month,Income,Expenses,Net")
        for (month in data.monthlySummary) {
            builder.appendLine(
                "${escapeCsvField(month.month)},${formatAmount(month.income)},${formatAmount(month.expenses)},${formatAmount(month.netAmount)}"
            )
        }

        return builder.toString()
    }

    /**
     * Generate PDF-ready content for transactions (will be used by platform-specific PDF generator)
     */
    fun generateTransactionsPdfContent(
        transactions: List<TransactionDisplay>,
        accountName: String? = null,
        title: String = "Transactions Export"
    ): PdfContent {
        // Filter by account if specified
        val filteredTransactions = if (accountName != null && accountName != "All Accounts") {
            transactions.filter { it.accountName == accountName }
        } else {
            transactions
        }

        val sections = mutableListOf<PdfSection>()

        // Title section
        sections.add(PdfSection.Title(title))

        // Account filter info
        if (accountName != null && accountName != "All Accounts") {
            sections.add(PdfSection.Subtitle("Account: $accountName"))
        }

        sections.add(PdfSection.Subtitle("${filteredTransactions.size} transactions"))
        sections.add(PdfSection.Spacer)

        // Group transactions by date
        val groupedByDate = filteredTransactions.groupBy { it.date }

        for ((date, txList) in groupedByDate.entries.sortedByDescending { it.key }) {
            sections.add(PdfSection.DateHeader(date))

            for (tx in txList) {
                sections.add(
                    PdfSection.Transaction(
                        description = tx.counterparty ?: tx.description.take(40),
                        category = tx.category.displayName,
                        amount = formatAmountWithCurrency(tx.amount, tx.currency),
                        isPositive = tx.amount >= 0
                    )
                )
            }
            sections.add(PdfSection.Spacer)
        }

        // Summary at bottom
        val totalIncome = filteredTransactions.filter { it.amount > 0 }.sumOf { it.amount }
        val totalExpenses = filteredTransactions.filter { it.amount < 0 }.sumOf { it.amount }

        sections.add(PdfSection.SummaryLine("Total Income", formatAmountWithCurrency(totalIncome, "EUR"), true))
        sections.add(PdfSection.SummaryLine("Total Expenses", formatAmountWithCurrency(totalExpenses, "EUR"), false))
        sections.add(PdfSection.SummaryLine("Net Balance", formatAmountWithCurrency(totalIncome + totalExpenses, "EUR"), totalIncome + totalExpenses >= 0))

        return PdfContent(sections)
    }

    /**
     * Generate PDF-ready content for spending overview
     */
    fun generateSpendingPdfContent(
        data: SpendingExportData,
        title: String = "Spending Overview"
    ): PdfContent {
        val sections = mutableListOf<PdfSection>()

        // Title
        sections.add(PdfSection.Title(title))
        sections.add(PdfSection.Spacer)

        // Summary box
        sections.add(PdfSection.Subtitle("Summary"))
        sections.add(PdfSection.SummaryLine("Total Income", formatAmountWithCurrency(data.totalIncome, "EUR"), true))
        sections.add(PdfSection.SummaryLine("Total Expenses", formatAmountWithCurrency(data.totalExpenses, "EUR"), false))
        sections.add(PdfSection.SummaryLine("Net Balance", formatAmountWithCurrency(data.totalIncome + data.totalExpenses, "EUR"), data.totalIncome + data.totalExpenses >= 0))
        sections.add(PdfSection.Spacer)

        // Category breakdown
        sections.add(PdfSection.Subtitle("Spending by Category"))
        for (category in data.categorySpending.filter { it.totalAmount < 0 }.sortedBy { it.totalAmount }) {
            sections.add(
                PdfSection.CategoryRow(
                    name = category.category.displayName,
                    amount = formatAmountWithCurrency(category.totalAmount, "EUR"),
                    percentage = "${category.percentage.toInt()}%",
                    count = category.transactionCount
                )
            )
        }
        sections.add(PdfSection.Spacer)

        // Monthly summary
        if (data.monthlySummary.isNotEmpty()) {
            sections.add(PdfSection.Subtitle("Monthly Summary"))
            for (month in data.monthlySummary) {
                sections.add(
                    PdfSection.MonthlyRow(
                        month = month.month,
                        income = formatAmountWithCurrency(month.income, "EUR"),
                        expenses = formatAmountWithCurrency(month.expenses, "EUR"),
                        net = formatAmountWithCurrency(month.netAmount, "EUR"),
                        isPositive = month.netAmount >= 0
                    )
                )
            }
        }

        return PdfContent(sections)
    }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /*private fun formatAmount(amount: Double): String {
        return "%.2f".format(amount)
    }*/

    private fun formatAmount(amount: Double): String {
        return ((amount * 100).toInt() / 100.0).toString()
    }

    private fun formatAmountWithCurrency(amount: Double, currency: String): String {
        val symbol = when (currency) {
            "EUR" -> "€"
            "USD" -> "$"
            "GBP" -> "£"
            else -> currency
        }
        //val formatted = "%.2f".format(amount.absoluteValue).replace(".", ",")
        val formatted = ((amount.absoluteValue * 100).roundToInt() / 100.0)
            .toString()
            .replace(".", ",")
        return if (amount >= 0) "+$formatted $symbol" else "-$formatted $symbol"
    }
}

/**
 * PDF content structure for platform-specific rendering
 */
data class PdfContent(val sections: List<PdfSection>)

/**
 * Different types of PDF sections
 */
sealed class PdfSection {
    data class Title(val text: String) : PdfSection()
    data class Subtitle(val text: String) : PdfSection()
    data class DateHeader(val date: String) : PdfSection()
    data class Transaction(
        val description: String,
        val category: String,
        val amount: String,
        val isPositive: Boolean
    ) : PdfSection()
    data class SummaryLine(
        val label: String,
        val value: String,
        val isPositive: Boolean
    ) : PdfSection()
    data class CategoryRow(
        val name: String,
        val amount: String,
        val percentage: String,
        val count: Int
    ) : PdfSection()
    data class MonthlyRow(
        val month: String,
        val income: String,
        val expenses: String,
        val net: String,
        val isPositive: Boolean
    ) : PdfSection()
    object Spacer : PdfSection()
}
