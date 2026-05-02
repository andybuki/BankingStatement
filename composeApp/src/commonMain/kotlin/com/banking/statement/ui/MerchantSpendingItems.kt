package com.banking.statement.ui

// ============================================================
// Merchant data aggregators used by MerchantsScreen.
// UI rendering for merchant rows lives in MerchantsScreen.kt
// (RankedMerchantRow) — designed to match the MoneyLupe
// MLMerchantsScreen mockup.
// ============================================================

/**
 * Bucket transactions into (year-month) buckets keyed by canonical merchant.
 *
 * Display names are resolved *globally* across every transaction first, then
 * applied to each month's bucket. This keeps the same merchant labelled
 * consistently across months — without it, a brand whose most-frequent
 * counterparty differs from month to month (e.g. branch number drift) would
 * appear as several distinct merchants in trend / history calculations.
 */
private fun buildMerchantMonthlyData(
    transactions: List<TransactionDisplay>
): List<MerchantMonthlyData> {
    val expenses = transactions.filter {
        it.amount < 0 && (!it.counterparty.isNullOrBlank() || it.description.isNotBlank())
    }
    if (expenses.isEmpty()) return emptyList()

    // Step 1: pick a stable display name per canonical merchant key, using
    // the full set of transactions so the choice stays consistent across
    // every month bucket below.
    val keyToDisplayName: Map<String, String> = expenses
        .groupBy { MerchantGrouping.canonicalKey(it.counterparty, it.description) }
        .filterKeys { it.isNotBlank() }
        .mapValues { (_, group) -> MerchantGrouping.pickDisplayName(group) }

    // Step 2: aggregate per (display name, month).
    val byDisplayAndMonth = HashMap<Pair<String, String>, MutableList<TransactionDisplay>>()
    for (tx in expenses) {
        val month = monthKey(tx.date) ?: continue
        val canonicalKey = MerchantGrouping.canonicalKey(tx.counterparty, tx.description)
        val displayName = keyToDisplayName[canonicalKey] ?: continue
        byDisplayAndMonth.getOrPut(displayName to month) { mutableListOf() }.add(tx)
    }

    return byDisplayAndMonth.map { (key, txs) ->
        val (displayName, month) = key
        MerchantMonthlyData(
            month = month,
            merchantName = displayName,
            category = txs.first().category,
            totalAmount = txs.sumOf { it.amount },
            transactionCount = txs.size
        )
    }
}

private fun monthKey(date: String): String? {
    val parts = date.split(".")
    return if (parts.size >= 3) "${parts[2]}-${parts[1]}" else null
}

/**
 * Calculate merchant trends from transactions.
 */
fun calculateMerchantTrendsFromTransactions(transactions: List<TransactionDisplay>): List<MerchantTrend> {
    val monthlyData = buildMerchantMonthlyData(transactions)
    return MerchantTrendCalculator.calculateMerchantTrends(monthlyData, topN = 10)
}

/**
 * Calculate merchant spending history from transactions.
 */
fun calculateMerchantHistoryFromTransactions(transactions: List<TransactionDisplay>): List<MerchantHistory> {
    val monthlyData = buildMerchantMonthlyData(transactions)
    return calculateMerchantHistory(monthlyData, topN = 10)
}
