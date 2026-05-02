package com.banking.statement.categorization

/**
 * Database for loading category keywords from external CSV files.
 * Supports multiple languages/countries based on IBAN country code.
 */
class KeywordDatabase : KeywordLookup {

    // Map of category name to list of keywords
    private var keywordMap: Map<TransactionCategory, List<String>> = emptyMap()
    private var loadedCountryCode: String? = null

    /**
     * Check if keywords are loaded
     */
    override fun isLoaded(): Boolean = keywordMap.isNotEmpty()

    /**
     * Get the currently loaded country code
     */
    fun getLoadedCountryCode(): String? = loadedCountryCode

    /**
     * Load keywords from CSV content.
     * Format: category,keyword (one per line, with header)
     */
    fun loadFromCsv(csvContent: String, countryCode: String) {
        val lines = csvContent.lines()
        if (lines.isEmpty()) return

        val map = mutableMapOf<TransactionCategory, MutableList<String>>()

        // Skip header line
        val dataLines = if (lines.first().lowercase().contains("category")) {
            lines.drop(1)
        } else {
            lines
        }

        for (line in dataLines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val parts = trimmed.split(",", limit = 2)
            if (parts.size < 2) continue

            val categoryName = parts[0].trim().uppercase()
            val keyword = parts[1].trim().lowercase()

            if (keyword.isBlank()) continue

            // Find matching category
            val category = try {
                TransactionCategory.valueOf(categoryName)
            } catch (e: Exception) {
                null
            }

            if (category != null && category != TransactionCategory.OTHER) {
                map.getOrPut(category) { mutableListOf() }.add(keyword)
            }
        }

        keywordMap = map
        loadedCountryCode = countryCode.lowercase()
    }

    /**
     * Find the best matching category for a transaction.
     * Returns null if no match found.
     *
     * Matching strategy:
     * 1. Use word-boundary matching to avoid false positives (e.g., "miete" should not match "vermieter")
     * 2. Prioritize longer/more specific keywords
     * 3. Break ties by category priority (e.g., SALARY beats RESTAURANT for equal-length keywords)
     */
    override fun findCategory(description: String, counterparty: String?): TransactionCategory? {
        if (keywordMap.isEmpty()) return null

        val searchText = "${description.lowercase()} ${counterparty?.lowercase() ?: ""}"

        // Normalize search text: replace special chars with spaces for word boundary matching
        val normalizedText = searchText
            .replace(Regex("[^a-z0-9äöüß]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Split into words for word-boundary matching
        val words = normalizedText.split(" ").filter { it.isNotBlank() }
        val wordsSet = words.toSet()

        var bestMatch: TransactionCategory? = null
        var bestKeywordLength = 0
        var bestPriority = -1

        for ((category, keywords) in keywordMap) {
            val categoryPriority = priorityOf(category)
            for (keyword in keywords) {
                // Normalize keyword the same way
                val normalizedKeyword = keyword
                    .replace(Regex("[^a-z0-9äöüß]"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                val keywordWords = normalizedKeyword.split(" ").filter { it.isNotBlank() }

                // Skip single-word keywords that are too short (< 3 chars) to avoid
                // false positives from generic abbreviations like "ag", "kg", etc.
                if (keywordWords.size == 1 && normalizedKeyword.length < 3) continue

                // Check if all words of the keyword are present in the search text
                val allWordsMatch = keywordWords.all { kw -> wordsSet.contains(kw) }

                // For multi-word keywords, also verify they appear in sequence
                val matches = if (keywordWords.size == 1) {
                    allWordsMatch
                } else {
                    allWordsMatch && normalizedText.contains(normalizedKeyword)
                }

                if (matches) {
                    val keywordLength = normalizedKeyword.length
                    // Longer keyword wins; ties broken by category priority
                    if (keywordLength > bestKeywordLength ||
                        (keywordLength == bestKeywordLength && categoryPriority > bestPriority)) {
                        bestKeywordLength = keywordLength
                        bestPriority = categoryPriority
                        bestMatch = category
                    }
                }
            }
        }

        return bestMatch
    }

    /**
     * Get all keywords for a specific category
     */
    fun getKeywordsForCategory(category: TransactionCategory): List<String> {
        return keywordMap[category] ?: emptyList()
    }

    /**
     * Get total number of keywords loaded
     */
    fun getKeywordCount(): Int {
        return keywordMap.values.sumOf { it.size }
    }

    /**
     * Clear loaded keywords
     */
    fun clear() {
        keywordMap = emptyMap()
        loadedCountryCode = null
    }

    companion object {
        /**
         * Category priority for tie-breaking when two keywords have equal length.
         * Higher value = higher priority. Income/fixed-cost categories rank above
         * discretionary spend categories so e.g. "rente" (SALARY) beats a same-length
         * restaurant keyword when both appear in the same transaction text.
         */
        fun priorityOf(category: TransactionCategory): Int = when (category) {
            TransactionCategory.SALARY        -> 100
            TransactionCategory.RENT          -> 90
            TransactionCategory.TAXES         -> 85
            TransactionCategory.INSURANCE     -> 80
            TransactionCategory.REFUND        -> 75
            TransactionCategory.INVESTMENT    -> 70
            TransactionCategory.TRANSPORT     -> 65
            TransactionCategory.HEALTH        -> 60
            TransactionCategory.EDUCATION     -> 55
            TransactionCategory.TRAVEL        -> 50
            TransactionCategory.SUBSCRIPTIONS -> 45
            TransactionCategory.SUPERMARKET   -> 40
            TransactionCategory.RESTAURANT    -> 35
            TransactionCategory.SHOPPING      -> 30
            TransactionCategory.ENTERTAINMENT -> 25
            TransactionCategory.TRANSFER      -> 20
            TransactionCategory.OTHER         -> 0
        }

        /**
         * Extract country code from IBAN (first 2 characters)
         */
        fun getCountryCodeFromIban(iban: String?): String {
            if (iban.isNullOrBlank()) return "de" // Default to German
            val cleaned = iban.replace(" ", "").uppercase()
            return if (cleaned.length >= 2) {
                cleaned.substring(0, 2).lowercase()
            } else {
                "de"
            }
        }

        /**
         * Get the keyword file name for a country code
         */
        fun getKeywordFileName(countryCode: String): String {
            return "keywords_${countryCode.lowercase()}.csv"
        }

        /**
         * Get the merchant file name for a country code
         */
        fun getMerchantFileName(countryCode: String): String {
            return "merchants_${countryCode.lowercase()}.csv"
        }
    }
}
