package com.banking.statement.ui

// ============================================================
// Merchant data aggregators used by MerchantsScreen.
// UI rendering for merchant rows lives in MerchantsScreen.kt
// (RankedMerchantRow) — designed to match the MoneyLupe
// MLMerchantsScreen mockup.
// ============================================================

/**
 * Calculate merchant trends from transactions.
 */
fun calculateMerchantTrendsFromTransactions(transactions: List<TransactionDisplay>): List<MerchantTrend> {
    val monthlyData = transactions
        .filter { it.amount < 0 && !it.counterparty.isNullOrBlank() }
        .groupBy { tx ->
            val dateParts = tx.date.split(".")
            if (dateParts.size >= 3) {
                "${dateParts[2]}-${dateParts[1]}" // YYYY-MM format
            } else {
                null
            }
        }
        .filterKeys { it != null }
        .flatMap { (month, txs) ->
            txs.groupBy { it.counterparty!! }
                .map { (merchant, merchantTxs) ->
                    MerchantMonthlyData(
                        month = month!!,
                        merchantName = merchant,
                        category = merchantTxs.first().category,
                        totalAmount = merchantTxs.sumOf { it.amount },
                        transactionCount = merchantTxs.size
                    )
                }
        }

    return MerchantTrendCalculator.calculateMerchantTrends(monthlyData, topN = 10)
}

/**
 * Calculate merchant spending history from transactions.
 */
fun calculateMerchantHistoryFromTransactions(transactions: List<TransactionDisplay>): List<MerchantHistory> {
    val monthlyData = transactions
        .filter { it.amount < 0 && !it.counterparty.isNullOrBlank() }
        .groupBy { tx ->
            val dateParts = tx.date.split(".")
            if (dateParts.size >= 3) {
                "${dateParts[2]}-${dateParts[1]}" // YYYY-MM format
            } else {
                null
            }
        }
        .filterKeys { it != null }
        .flatMap { (month, txs) ->
            txs.groupBy { it.counterparty!! }
                .map { (merchant, merchantTxs) ->
                    MerchantMonthlyData(
                        month = month!!,
                        merchantName = merchant,
                        category = merchantTxs.first().category,
                        totalAmount = merchantTxs.sumOf { it.amount },
                        transactionCount = merchantTxs.size
                    )
                }
        }

    return calculateMerchantHistory(monthlyData, topN = 10)
}
