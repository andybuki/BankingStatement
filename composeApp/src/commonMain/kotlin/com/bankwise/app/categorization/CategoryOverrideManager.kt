package com.bankwise.app.categorization

import com.bankwise.app.db.BankingDatabase
import com.bankwise.app.ui.TransactionDisplay

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
     * Save a user override for a pattern.
     * Prioritizes counterparty-only pattern for better matching across imports.
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
        // Try to delete both patterns
        val primaryPattern = extractPrimaryPattern(description, counterparty)
        if (!primaryPattern.isNullOrBlank()) {
            database.bankingDatabaseQueries.deleteCategoryOverride(primaryPattern)
            overrideCache?.remove(primaryPattern)
        }

        val fullPattern = extractFullPattern(description, counterparty)
        if (fullPattern.isNotBlank()) {
            database.bankingDatabaseQueries.deleteCategoryOverride(fullPattern)
            overrideCache?.remove(fullPattern)
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
