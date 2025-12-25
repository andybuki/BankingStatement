package com.banking.statement.categorization

import com.banking.statement.db.BankingDatabase

/**
 * Manages user category overrides - manual corrections that take priority
 * over automatic categorization from merchant database and keywords.
 */
class CategoryOverrideManager(
    private val database: BankingDatabase
) {
    private var overrideCache: MutableMap<String, TransactionCategory>? = null

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
     * Extract primary pattern - prefer counterparty for stability
     */
    private fun extractPrimaryPattern(counterparty: String?): String? {
        if (counterparty.isNullOrBlank()) return null
        val normalized = normalizePattern(counterparty)
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
        val primaryPattern = extractPrimaryPattern(counterparty)
        if (!primaryPattern.isNullOrBlank()) {
            database.bankingDatabaseQueries.insertCategoryOverride(primaryPattern, category.name)
            val cache = overrideCache ?: mutableMapOf()
            cache[primaryPattern] = category
            overrideCache = cache
        } else {
            // Fall back to full pattern if no counterparty
            val fullPattern = extractFullPattern(description, counterparty)
            if (fullPattern.isNotBlank()) {
                database.bankingDatabaseQueries.insertCategoryOverride(fullPattern, category.name)
                val cache = overrideCache ?: mutableMapOf()
                cache[fullPattern] = category
                overrideCache = cache
            }
        }
    }

    /**
     * Find a user override for a transaction.
     * Checks counterparty-only pattern first, then falls back to full pattern.
     */
    fun findOverride(description: String, counterparty: String?): TransactionCategory? {
        // First try counterparty-only pattern (most reliable)
        val primaryPattern = extractPrimaryPattern(counterparty)
        if (!primaryPattern.isNullOrBlank()) {
            // Check cache first
            overrideCache?.get(primaryPattern)?.let { return it }

            // Check database
            val categoryName = database.bankingDatabaseQueries.getCategoryOverrideByPattern(primaryPattern).executeAsOneOrNull()
            if (categoryName != null) {
                val category = TransactionCategory.entries.find { it.name == categoryName }
                if (category != null) {
                    // Update cache
                    val cache = overrideCache ?: mutableMapOf()
                    cache[primaryPattern] = category
                    overrideCache = cache
                    return category
                }
            }
        }

        // Fall back to full pattern match
        val fullPattern = extractFullPattern(description, counterparty)
        if (fullPattern.isBlank()) return null

        // Check cache
        overrideCache?.get(fullPattern)?.let { return it }

        // Check database
        val categoryName = database.bankingDatabaseQueries.getCategoryOverrideByPattern(fullPattern).executeAsOneOrNull()
        if (categoryName != null) {
            val category = TransactionCategory.entries.find { it.name == categoryName }
            if (category != null) {
                // Update cache
                val cache = overrideCache ?: mutableMapOf()
                cache[fullPattern] = category
                overrideCache = cache
                return category
            }
        }

        return null
    }

    /**
     * Load all overrides into cache for fast lookup
     */
    fun loadCache() {
        val cache = mutableMapOf<String, TransactionCategory>()

        try {
            database.bankingDatabaseQueries.getAllCategoryOverrides().executeAsList().forEach { row ->
                val category = TransactionCategory.entries.find { it.name == row.category_name }
                if (category != null) {
                    cache[row.pattern] = category
                }
            }
        } catch (e: Exception) {
            // Table might not exist yet on first run
        }

        overrideCache = cache
    }

    /**
     * Delete an override
     */
    fun deleteOverride(description: String, counterparty: String?) {
        // Try to delete both patterns
        val primaryPattern = extractPrimaryPattern(counterparty)
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
