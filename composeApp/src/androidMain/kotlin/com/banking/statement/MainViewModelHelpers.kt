package com.banking.statement

import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.TransactionDisplay

/**
 * Pure helper functions extracted from [MainViewModel].
 * They do not reference ViewModel instance state and can be tested in isolation.
 */

internal fun formatMonth(yearMonth: String): String {
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
 * Derive a normalized match key for a transaction, used to group related overrides.
 * PayPal descriptions are normalized through [TransactionDisplay.extractDisplayName];
 * everything else falls back to counterparty (preferred) or description.
 */
internal fun getTransactionMatchKey(transaction: TransactionDisplay): String {
    fun normalize(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9äöüß ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    val counterparty = transaction.counterparty
    val description = transaction.description
    val counterpartyLower = counterparty?.lowercase() ?: ""
    val descriptionLower = description.lowercase()

    if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
        val displayName = TransactionDisplay.extractDisplayName(counterparty, description)
        return normalize(displayName)
    }

    return if (!counterparty.isNullOrBlank()) {
        normalize(counterparty)
    } else {
        normalize(description)
    }
}

/**
 * Recomputes derived financial fields (spending, income, expenses) from transactions.
 * Returns the updated state for use in atomic [kotlinx.coroutines.flow.MutableStateFlow.update] calls.
 */
internal fun recomputeFinancialState(state: FinancialUiState): FinancialUiState {
    val spendingByCategory = state.transactions
        .filter { it.amount < 0 }
        .groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }

    val totalExpensesAmount = spendingByCategory.values.sum()

    val computedSpending = spendingByCategory.map { (cat, total) ->
        CategorySpending(
            category = cat,
            totalAmount = total,
            transactionCount = state.transactions.count { it.category == cat && it.amount < 0 },
            percentage = if (totalExpensesAmount != 0.0) {
                (total / totalExpensesAmount * 100).toFloat()
            } else 0f
        )
    }.sortedBy { it.totalAmount }

    return state.copy(
        categorySpending = computedSpending,
        totalExpenses = state.transactions.filter { it.amount < 0 }.sumOf { it.amount },
        totalIncome = state.transactions.filter { it.amount > 0 }.sumOf { it.amount }
    )
}
