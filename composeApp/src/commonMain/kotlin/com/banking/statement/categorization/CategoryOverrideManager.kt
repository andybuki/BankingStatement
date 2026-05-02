package com.banking.statement.categorization

import com.banking.statement.db.BankingDatabase
import com.banking.statement.ui.TransactionDisplay

/**
 * Result of looking up a category override - can be predefined or custom
 */
sealed class CategoryOverrideResult {
    data class Predefined(val category: TransactionCategory) : CategoryOverrideResult()
    data class Custom(val categoryId: Long) : CategoryOverrideResult()
}

/**
 * Manages user category overrides - manual corrections that take priority
 * over automatic categorization from merchant database and keywords.
 */
class CategoryOverrideManager(
    private val database: BankingDatabase
) {
    private var overrideCache: MutableMap<String, TransactionCategory>? = null
    private var customOverrideCache: MutableMap<String, Long>? = null

    companion object {
        private const val CUSTOM_PREFIX = "custom:"
    }

    /**
     * Normalize text for matching (same logic as MerchantDatabase)
     */
    private fun normalizePattern(text: String): String {
        return text.lowercase()
            .replace(Regex("[^a-z0-9äöüß ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Extract primary pattern - prefer counterparty for stability.
     * For PayPal transactions, uses extracted merchant name instead of generic PayPal name.
     */
    private fun extractPrimaryPattern(description: String, counterparty: String?): String? {
        if (counterparty.isNullOrBlank() && !description.lowercase().contains("paypal")) return null

        val counterpartyLower = counterparty?.lowercase() ?: ""
        val descriptionLower = description.lowercase()

        // For PayPal, use the smart display name (e.g., "PayPal · Wolt")
        val effectiveCounterparty = if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
            TransactionDisplay.extractDisplayName(counterparty, description)
        } else {
            counterparty
        }

        if (effectiveCounterparty.isNullOrBlank()) return null
        val normalized = normalizePattern(effectiveCounterparty)
        return if (normalized.isNotBlank()) normalized else null
    }

    /**
     * Extract fallback pattern - full description + counterparty
     */
    private fun extractFullPattern(description: String, counterparty: String?): String {
        val combined = "$description ${counterparty ?: ""}"
        return normalizePattern(combined)
    }

    /**
     * Extract a generalized merchant-token pattern that strips store numbers,
     * city suffixes and noise words. Saving this alongside the primary pattern
     * lets a single user correction apply to all branches/variants of the
     * same merchant ("REWE BERLIN 047" → "REWE MUNCHEN 042" both match "rewe").
     *
     * Returns null if no meaningful merchant token can be extracted.
     */
    private fun extractTokenPattern(description: String, counterparty: String?): String? {
        // Prefer counterparty for token extraction (more stable than description)
        val source = when {
            !counterparty.isNullOrBlank() -> counterparty
            description.isNotBlank() -> description
            else -> return null
        }
        val token = MerchantNormalizer.extractMerchantToken(source) ?: return null
        // Require at least 4 chars to avoid generic short tokens (e.g. "ag", "co")
        return if (token.length >= 4) token else null
    }

    /**
     * Save a user override for a pattern.
     * Prioritizes counterparty-only pattern for better matching across imports.
     *
     * Also saves a generalized merchant-token pattern (e.g. "rewe") so a
     * single correction applies to every branch/variant of the same merchant
     * in future imports — see [extractTokenPattern].
     */
    fun saveOverride(description: String, counterparty: String?, category: TransactionCategory) {
        // Try to save counterparty-only pattern first (more stable)
        val primaryPattern = extractPrimaryPattern(description, counterparty)
        if (!primaryPattern.isNullOrBlank()) {
            database.bankingDatabaseQueries.insertCategoryOverride(primaryPattern, category.name)
            val cache = overrideCache ?: mutableMapOf()
            cache[primaryPattern] = category
            overrideCache = cache
            // Remove from custom cache if exists
            customOverrideCache?.remove(primaryPattern)
        } else {
            // Fall back to full pattern if no counterparty
            val fullPattern = extractFullPattern(description, counterparty)
            if (fullPattern.isNotBlank()) {
                database.bankingDatabaseQueries.insertCategoryOverride(fullPattern, category.name)
                val cache = overrideCache ?: mutableMapOf()
                cache[fullPattern] = category
                overrideCache = cache
                // Remove from custom cache if exists
                customOverrideCache?.remove(fullPattern)
            }
        }

        // Always save a generalized merchant-token rule alongside the precise
        // pattern, so corrections generalize to other branches automatically.
        saveTokenPattern(description, counterparty, category.name)
    }

    /**
     * Save a custom category override for a pattern.
     */
    fun saveCustomCategoryOverride(description: String, counterparty: String?, customCategoryId: Long) {
        val categoryValue = "$CUSTOM_PREFIX$customCategoryId"

        // Try to save counterparty-only pattern first (more stable)
        val primaryPattern = extractPrimaryPattern(description, counterparty)
        if (!primaryPattern.isNullOrBlank()) {
            database.bankingDatabaseQueries.insertCategoryOverride(primaryPattern, categoryValue)
            val cache = customOverrideCache ?: mutableMapOf()
            cache[primaryPattern] = customCategoryId
            customOverrideCache = cache
            // Remove from regular cache if exists
            overrideCache?.remove(primaryPattern)
        } else {
            // Fall back to full pattern if no counterparty
            val fullPattern = extractFullPattern(description, counterparty)
            if (fullPattern.isNotBlank()) {
                database.bankingDatabaseQueries.insertCategoryOverride(fullPattern, categoryValue)
                val cache = customOverrideCache ?: mutableMapOf()
                cache[fullPattern] = customCategoryId
                customOverrideCache = cache
                // Remove from regular cache if exists
                overrideCache?.remove(fullPattern)
            }
        }

        // Save the generalized token rule so the correction applies to all
        // future branches/variants of this merchant.
        saveTokenPattern(description, counterparty, categoryValue, customCategoryId = customCategoryId)
    }

    /**
     * Persist a generalized merchant-token rule. Called from both the
     * predefined and custom override saves so a single user correction
     * applies to all variants of a merchant.
     */
    private fun saveTokenPattern(
        description: String,
        counterparty: String?,
        categoryValue: String,
        customCategoryId: Long? = null
    ) {
        val tokenPattern = extractTokenPattern(description, counterparty) ?: return
        // Don't overwrite a more-specific override that already covers this token
        val existing = database.bankingDatabaseQueries
            .getCategoryOverrideByPattern(tokenPattern)
            .executeAsOneOrNull()
        if (existing != null && existing == categoryValue) return

        database.bankingDatabaseQueries.insertCategoryOverride(tokenPattern, categoryValue)

        if (customCategoryId != null) {
            val cache = customOverrideCache ?: mutableMapOf()
            cache[tokenPattern] = customCategoryId
            customOverrideCache = cache
            overrideCache?.remove(tokenPattern)
        } else {
            val category = TransactionCategory.entries.find { it.name == categoryValue }
            if (category != null) {
                val cache = overrideCache ?: mutableMapOf()
                cache[tokenPattern] = category
                overrideCache = cache
                customOverrideCache?.remove(tokenPattern)
            }
        }
    }

    /**
     * Find a user override for a transaction.
     * Checks counterparty-only pattern first, then falls back to full pattern.
     */
    fun findOverride(description: String, counterparty: String?): TransactionCategory? {
        val result = findOverrideWithCustom(description, counterparty)
        return when (result) {
            is CategoryOverrideResult.Predefined -> result.category
            is CategoryOverrideResult.Custom -> null  // Caller should use findOverrideWithCustom for custom support
            null -> null
        }
    }

    /**
     * Find a user override for a transaction, supporting both predefined and custom categories.
     * Returns CategoryOverrideResult which can be Predefined or Custom.
     */
    fun findOverrideWithCustom(description: String, counterparty: String?): CategoryOverrideResult? {
        // First try counterparty-only pattern (most reliable)
        val primaryPattern = extractPrimaryPattern(description, counterparty)
        if (!primaryPattern.isNullOrBlank()) {
            // Check custom cache first
            customOverrideCache?.get(primaryPattern)?.let {
                return CategoryOverrideResult.Custom(it)
            }
            // Check regular cache
            overrideCache?.get(primaryPattern)?.let {
                return CategoryOverrideResult.Predefined(it)
            }

            // Check database
            val categoryName = database.bankingDatabaseQueries.getCategoryOverrideByPattern(primaryPattern).executeAsOneOrNull()
            if (categoryName != null) {
                // Check if it's a custom category
                if (categoryName.startsWith(CUSTOM_PREFIX)) {
                    val customId = categoryName.removePrefix(CUSTOM_PREFIX).toLongOrNull()
                    if (customId != null) {
                        val cache = customOverrideCache ?: mutableMapOf()
                        cache[primaryPattern] = customId
                        customOverrideCache = cache
                        return CategoryOverrideResult.Custom(customId)
                    }
                } else {
                    val category = TransactionCategory.entries.find { it.name == categoryName }
                    if (category != null) {
                        val cache = overrideCache ?: mutableMapOf()
                        cache[primaryPattern] = category
                        overrideCache = cache
                        return CategoryOverrideResult.Predefined(category)
                    }
                }
            }
        }

        // Fall back to full pattern match
        val fullPattern = extractFullPattern(description, counterparty)
        if (fullPattern.isBlank()) return null

        // Check custom cache first
        customOverrideCache?.get(fullPattern)?.let {
            return CategoryOverrideResult.Custom(it)
        }
        // Check regular cache
        overrideCache?.get(fullPattern)?.let {
            return CategoryOverrideResult.Predefined(it)
        }

        // Check database
        val categoryName = database.bankingDatabaseQueries.getCategoryOverrideByPattern(fullPattern).executeAsOneOrNull()
        if (categoryName != null) {
            // Check if it's a custom category
            if (categoryName.startsWith(CUSTOM_PREFIX)) {
                val customId = categoryName.removePrefix(CUSTOM_PREFIX).toLongOrNull()
                if (customId != null) {
                    val cache = customOverrideCache ?: mutableMapOf()
                    cache[fullPattern] = customId
                    customOverrideCache = cache
                    return CategoryOverrideResult.Custom(customId)
                }
            } else {
                val category = TransactionCategory.entries.find { it.name == categoryName }
                if (category != null) {
                    val cache = overrideCache ?: mutableMapOf()
                    cache[fullPattern] = category
                    overrideCache = cache
                    return CategoryOverrideResult.Predefined(category)
                }
            }
        }

        // Final fallback — generalized merchant token. Lets a single user
        // correction on one transaction apply to every other variant of the
        // same merchant in subsequent imports (e.g. different store numbers,
        // cities or branch suffixes).
        val tokenPattern = extractTokenPattern(description, counterparty)
        if (!tokenPattern.isNullOrBlank()) {
            customOverrideCache?.get(tokenPattern)?.let {
                return CategoryOverrideResult.Custom(it)
            }
            overrideCache?.get(tokenPattern)?.let {
                return CategoryOverrideResult.Predefined(it)
            }

            val tokenCategoryName = database.bankingDatabaseQueries
                .getCategoryOverrideByPattern(tokenPattern)
                .executeAsOneOrNull()
            if (tokenCategoryName != null) {
                if (tokenCategoryName.startsWith(CUSTOM_PREFIX)) {
                    val customId = tokenCategoryName.removePrefix(CUSTOM_PREFIX).toLongOrNull()
                    if (customId != null) {
                        val cache = customOverrideCache ?: mutableMapOf()
                        cache[tokenPattern] = customId
                        customOverrideCache = cache
                        return CategoryOverrideResult.Custom(customId)
                    }
                } else {
                    val category = TransactionCategory.entries.find { it.name == tokenCategoryName }
                    if (category != null) {
                        val cache = overrideCache ?: mutableMapOf()
                        cache[tokenPattern] = category
                        overrideCache = cache
                        return CategoryOverrideResult.Predefined(category)
                    }
                }
            }
        }

        return null
    }

    /**
     * Load all overrides into cache for fast lookup
     */
    fun loadCache() {
        val predefinedCache = mutableMapOf<String, TransactionCategory>()
        val customCache = mutableMapOf<String, Long>()

        try {
            database.bankingDatabaseQueries.getAllCategoryOverrides().executeAsList().forEach { row ->
                if (row.category_name.startsWith(CUSTOM_PREFIX)) {
                    // Custom category
                    val customId = row.category_name.removePrefix(CUSTOM_PREFIX).toLongOrNull()
                    if (customId != null) {
                        customCache[row.pattern] = customId
                    }
                } else {
                    // Predefined category
                    val category = TransactionCategory.entries.find { it.name == row.category_name }
                    if (category != null) {
                        predefinedCache[row.pattern] = category
                    }
                }
            }
        } catch (e: Exception) {
            // Table might not exist yet on first run
        }

        overrideCache = predefinedCache
        customOverrideCache = customCache
    }

    /**
     * Delete an override
     */
    fun deleteOverride(description: String, counterparty: String?) {
        // Try to delete all three patterns (primary, full, token)
        val primaryPattern = extractPrimaryPattern(description, counterparty)
        if (!primaryPattern.isNullOrBlank()) {
            database.bankingDatabaseQueries.deleteCategoryOverride(primaryPattern)
            overrideCache?.remove(primaryPattern)
            customOverrideCache?.remove(primaryPattern)
        }

        val fullPattern = extractFullPattern(description, counterparty)
        if (fullPattern.isNotBlank()) {
            database.bankingDatabaseQueries.deleteCategoryOverride(fullPattern)
            overrideCache?.remove(fullPattern)
            customOverrideCache?.remove(fullPattern)
        }

        val tokenPattern = extractTokenPattern(description, counterparty)
        if (!tokenPattern.isNullOrBlank()) {
            database.bankingDatabaseQueries.deleteCategoryOverride(tokenPattern)
            overrideCache?.remove(tokenPattern)
            customOverrideCache?.remove(tokenPattern)
        }
    }

    /**
     * Clear all overrides
     */
    fun clearAll() {
        database.bankingDatabaseQueries.clearCategoryOverrides()
        overrideCache?.clear()
    }
}
