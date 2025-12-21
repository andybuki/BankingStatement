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

    // In-memory cache of merchants for fast contains matching
    private var merchantCache: List<Pair<String, String>>? = null // (normalized_name, category_code)

    /**
     * Check if merchant database is loaded
     */
    fun isLoaded(): Boolean {
        return try {
            database.bankingDatabaseQueries.getMerchantCount().executeAsOne() > 0
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
        val lines = csvContent.lines().filter { it.isNotBlank() }
        val totalLines = lines.size

        // Check if first line is a header (contains non-category codes)
        val hasHeader = lines.firstOrNull()?.let { firstLine ->
            val parts = parseCsvLine(firstLine)
            parts.isNotEmpty() && !categoryCodeMap.containsKey(parts[0])
        } ?: false

        val startIndex = if (hasHeader) 1 else 0

        // Build cache while loading
        val cacheBuilder = mutableListOf<Pair<String, String>>()

        database.bankingDatabaseQueries.transaction {
            // Clear existing data
            database.bankingDatabaseQueries.clearMerchants()

            var count = 0
            lines.forEachIndexed { index, line ->
                if (index < startIndex) return@forEachIndexed // skip header if present

                val parts = parseCsvLine(line)
                if (parts.size >= 3) {
                    val categoryCode = parts[0]
                    val countryCode = parts[1]
                    val name = parts[2]
                    val nameNormalized = normalizeName(name)

                    if (categoryCode.isNotBlank() && name.isNotBlank() && nameNormalized.length >= 3) {
                        database.bankingDatabaseQueries.insertMerchant(
                            category_code = categoryCode,
                            country_code = countryCode,
                            name = name,
                            name_normalized = nameNormalized
                        )
                        cacheBuilder.add(nameNormalized to categoryCode)
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

        // Sort cache by name length descending (longer matches first)
        merchantCache = cacheBuilder.sortedByDescending { it.first.length }
    }

    /**
     * Ensure cache is loaded - rebuild from database if needed
     */
    fun ensureCacheLoaded() {
        if (merchantCache != null) return
        if (!isLoaded()) return

        try {
            val merchants = database.bankingDatabaseQueries
                .getAllMerchantsForCache()
                .executeAsList()

            merchantCache = merchants
                .map { it.name_normalized to it.category_code }
                .sortedByDescending { it.first.length }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Find category for a transaction description/counterparty.
     * Returns null if no match found.
     */
    fun findCategory(description: String, counterparty: String? = null): TransactionCategory? {
        // Skip if no merchants loaded
        if (!isLoaded()) return null

        // Ensure cache is populated
        ensureCacheLoaded()

        val searchText = normalizeName("$description ${counterparty ?: ""}")
        if (searchText.isBlank()) return null

        // Split search text into words for word-boundary matching
        val searchWords = searchText.split(" ").filter { it.isNotBlank() }

        // Use in-memory cache for matching
        val cache = merchantCache
        if (cache != null) {
            // Find first merchant name that matches as complete word(s)
            // Cache is sorted by length desc, so longer matches are found first
            for ((merchantName, categoryCode) in cache) {
                if (matchesAsWord(searchText, searchWords, merchantName)) {
                    return categoryCodeMap[categoryCode]
                }
            }
        }

        return null
    }

    /**
     * Check if merchant name matches as complete word(s) in search text.
     * "lidl" matches "lidl sagt danke" but "mie" does NOT match "miete"
     */
    private fun matchesAsWord(searchText: String, searchWords: List<String>, merchantName: String): Boolean {
        val merchantWords = merchantName.split(" ").filter { it.isNotBlank() }

        // Single word merchant - must match a complete word in search
        if (merchantWords.size == 1) {
            return searchWords.contains(merchantName)
        }

        // Multi-word merchant - all words must be present as complete words
        // and the phrase should appear in order
        if (merchantWords.all { word -> searchWords.contains(word) }) {
            // Also verify the words appear in sequence
            return searchText.contains(merchantName)
        }

        return false
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
