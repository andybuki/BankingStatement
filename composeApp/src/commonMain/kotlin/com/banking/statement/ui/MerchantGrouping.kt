package com.banking.statement.ui

import com.banking.statement.categorization.MerchantNormalizer

/**
 * Groups transactions by canonical merchant identity.
 *
 * Raw counterparty strings vary across statements — store numbers, branch
 * suffixes, payment-processor prefixes and OCR drift all produce different
 * literal strings for the same brand. Grouping the merchants list by the
 * raw counterparty therefore fragments recurring brands ("REWE BERLIN 047"
 * vs "REWE Berlin Filiale 0123") into many one-off entries, while genuine
 * one-time merchants look identical to recurring ones.
 *
 * This helper derives a stable canonical key per transaction (via
 * [MerchantNormalizer.extractMerchantToken]) and picks a clean display
 * name for each group.
 */
object MerchantGrouping {

    /**
     * Stable bucket key for a transaction's merchant identity. Falls back
     * to a normalized counterparty (or description) when no usable token
     * can be extracted, so transactions still group sensibly by raw text.
     */
    fun canonicalKey(counterparty: String?, description: String): String {
        if (!counterparty.isNullOrBlank()) {
            MerchantNormalizer.extractMerchantToken(counterparty)?.let { return it }
        }
        if (description.isNotBlank()) {
            MerchantNormalizer.extractMerchantToken(description)?.let { return it }
        }
        // Fall back to a best-effort normalized form so we never lose data.
        return MerchantNormalizer.normalizeLight(counterparty ?: description).ifBlank {
            (counterparty ?: description).lowercase().trim()
        }
    }

    /**
     * Group merchant transactions by canonical key and return one entry per
     * merchant with a chosen human-readable display name.
     *
     * The display name is picked from the group's actual counterparty
     * strings: the most frequent one wins (ties broken by length so a
     * fuller, more informative form takes precedence). This preserves the
     * user's original capitalization and format rather than emitting a
     * synthetic lowercase token.
     */
    fun groupByMerchant(
        transactions: List<TransactionDisplay>
    ): Map<String, List<TransactionDisplay>> {
        if (transactions.isEmpty()) return emptyMap()

        val byKey = transactions
            .filter { !it.counterparty.isNullOrBlank() || it.description.isNotBlank() }
            .groupBy { canonicalKey(it.counterparty, it.description) }
            .filterKeys { it.isNotBlank() }

        val out = LinkedHashMap<String, List<TransactionDisplay>>(byKey.size)
        for ((_, group) in byKey) {
            val name = pickDisplayName(group)
            // Two different canonical keys could still produce the same
            // display name in pathological cases — merge defensively.
            val merged = out[name].orEmpty() + group
            out[name] = merged
        }
        return out
    }

    /**
     * Choose the best display name for a group of transactions sharing a
     * canonical key. Strategy:
     *  1. Most frequent non-blank counterparty wins.
     *  2. Ties broken by longer string (more informative).
     *  3. If no counterparty is present anywhere, fall back to the first
     *     non-blank description.
     */
    fun pickDisplayName(group: List<TransactionDisplay>): String {
        val counterparties = group.mapNotNull { it.counterparty?.takeIf { c -> c.isNotBlank() } }
        if (counterparties.isNotEmpty()) {
            val best = counterparties
                .groupingBy { it }
                .eachCount()
                .entries
                .maxWithOrNull(
                    compareBy<Map.Entry<String, Int>> { it.value }
                        .thenBy { it.key.length }
                )
                ?.key
            if (!best.isNullOrBlank()) return best
        }
        return group.firstOrNull { it.description.isNotBlank() }?.description
            ?: group.first().counterparty.orEmpty()
    }
}
