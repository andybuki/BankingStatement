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
     * Extract key words from description for pattern matching
     */
    fun extractPattern(description: String, counterparty: String?): String {
        val combined = "$description ${counterparty ?: ""}"
        return normalizePattern(combined)
    }

    /**
     * Save a user override for a pattern
     */
    fun saveOverride(description: String, counterparty: String?, category: TransactionCategory) {
        val pattern = extractPattern(description, counterparty)
        if (pattern.isBlank()) return

        database.bankingDatabaseQueries.insertCategoryOverride(pattern, category.name)

        // Update cache
        val cache = overrideCache ?: mutableMapOf()
        cache[pattern] = category
        overrideCache = cache
    }

    /**
     * Find a user override for a transaction
     */
    fun findOverride(description: String, counterparty: String?): TransactionCategory? {
        val pattern = extractPattern(description, counterparty)
        if (pattern.isBlank()) return null

        // Check cache first
        overrideCache?.get(pattern)?.let { return it }

        // Check database
        val categoryName = database.bankingDatabaseQueries.getCategoryOverrideByPattern(pattern).executeAsOneOrNull()
        if (categoryName != null) {
            val category = TransactionCategory.entries.find { it.name == categoryName }
            if (category != null) {
                // Update cache
                val cache = overrideCache ?: mutableMapOf()
                cache[pattern] = category
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
        val pattern = extractPattern(description, counterparty)
        if (pattern.isBlank()) return

        database.bankingDatabaseQueries.deleteCategoryOverride(pattern)
        overrideCache?.remove(pattern)
    }

    /**
     * Clear all overrides
     */
    fun clearAll() {
        database.bankingDatabaseQueries.clearCategoryOverrides()
        overrideCache?.clear()
    }
}
