package com.banking.statement.categorization

import com.banking.statement.db.BankingDatabase

/**
 * Database-backed merchant lookup for transaction categorization.
 * Uses a pre-loaded CSV of known merchants mapped to categories.
 */
class MerchantDatabase(
    private val database: BankingDatabase
) {
    /**
     * Category code mappings from CSV to TransactionCategory
     */
    private val categoryCodeMap = mapOf(
        "fd" to TransactionCategory.RESTAURANT,    // food/dining
        "en" to TransactionCategory.ENTERTAINMENT, // entertainment
        "ft" to TransactionCategory.FITNESS,       // fitness
        "md" to TransactionCategory.HEALTH,        // medical/health
        "sm" to TransactionCategory.SUPERMARKET,   // supermarket
        "el" to TransactionCategory.SHOPPING,      // electronics
        "be" to TransactionCategory.SHOPPING,      // beauty
        "cl" to TransactionCategory.SHOPPING,      // clothing
        "bk" to TransactionCategory.RESTAURANT,    // bakery
        "tr" to TransactionCategory.TRAVEL         // travel
    )

    /**
     * Check if merchant database is loaded
     */
    fun isLoaded(): Boolean {
        return try {
            database.bankingDatabaseQueries.isMerchantsLoaded().executeAsOne() > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the number of merchants loaded
     */
    fun getMerchantCount(): Long {
        return try {
            database.bankingDatabaseQueries.getMerchantCount().executeAsOne()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Load merchants from CSV content.
     * Expected format: c,cc,n (category_code,country_code,name)
     */
    fun loadFromCsv(csvContent: String, onProgress: ((Int, Int) -> Unit)? = null) {
        val lines = csvContent.lines()
        val totalLines = lines.size - 1 // exclude header

        database.bankingDatabaseQueries.transaction {
            // Clear existing data
            database.bankingDatabaseQueries.clearMerchants()

            var count = 0
            lines.forEachIndexed { index, line ->
                if (index == 0) return@forEachIndexed // skip header

                val parts = parseCsvLine(line)
                if (parts.size >= 3) {
                    val categoryCode = parts[0]
                    val countryCode = parts[1]
                    val name = parts[2]
                    val nameNormalized = normalizeName(name)

                    if (categoryCode.isNotBlank() && name.isNotBlank()) {
                        database.bankingDatabaseQueries.insertMerchant(
                            category_code = categoryCode,
                            country_code = countryCode,
                            name = name,
                            name_normalized = nameNormalized
                        )
                        count++

                        // Report progress every 10000 entries
                        if (count % 10000 == 0) {
                            onProgress?.invoke(count, totalLines)
                        }
                    }
                }
            }
            onProgress?.invoke(count, totalLines)
        }
    }

    /**
     * Find category for a transaction description/counterparty.
     * Returns null if no match found.
     */
    fun findCategory(description: String, counterparty: String? = null): TransactionCategory? {
        val searchText = normalizeName("$description ${counterparty ?: ""}")

        // First try exact match on normalized name
        try {
            val exactMatch = database.bankingDatabaseQueries
                .findMerchantByName(searchText)
                .executeAsOneOrNull()

            if (exactMatch != null) {
                return categoryCodeMap[exactMatch.category_code]
            }
        } catch (e: Exception) {
            // Ignore and continue
        }

        // Try contains match - check if any merchant name is contained in the search text
        try {
            val containsMatch = database.bankingDatabaseQueries
                .findMerchantByNameContains(searchText)
                .executeAsOneOrNull()

            if (containsMatch != null) {
                return categoryCodeMap[containsMatch.category_code]
            }
        } catch (e: Exception) {
            // Ignore and continue
        }

        return null
    }

    /**
     * Normalize a name for matching:
     * - lowercase
     * - remove special characters
     * - collapse whitespace
     */
    private fun normalizeName(name: String): String {
        return name
            .lowercase()
            .replace(Regex("[^a-z0-9äöüß\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Parse a CSV line handling quoted fields
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())

        return result
    }

    companion object {
        /**
         * Convert category code to TransactionCategory
         */
        fun categoryFromCode(code: String): TransactionCategory? {
            return when (code) {
                "fd" -> TransactionCategory.RESTAURANT
                "en" -> TransactionCategory.ENTERTAINMENT
                "ft" -> TransactionCategory.FITNESS
                "md" -> TransactionCategory.HEALTH
                "sm" -> TransactionCategory.SUPERMARKET
                "el" -> TransactionCategory.SHOPPING
                "be" -> TransactionCategory.SHOPPING
                "cl" -> TransactionCategory.SHOPPING
                "bk" -> TransactionCategory.RESTAURANT
                "tr" -> TransactionCategory.TRAVEL
                else -> null
            }
        }
    }
}
