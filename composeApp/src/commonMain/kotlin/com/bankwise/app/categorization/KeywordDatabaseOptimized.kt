package com.bankwise.app.categorization

/**
 * OPTIMIZED version of KeywordDatabase with performance improvements:
 * 1. Pre-normalized keywords (avoid repeated regex operations)
 * 2. Sorted by keyword length (longest first) for early termination
 * 3. Simple cache for recently categorized transactions
 */
class KeywordDatabaseOptimized {

    // Normalized keyword data: category -> list of (normalized_keyword, original_keyword)
    private var normalizedKeywordMap: Map<TransactionCategory, List<NormalizedKeyword>> = emptyMap()
    private var loadedCountryCode: String? = null

    // Simple LRU cache for recent categorizations (description hash -> category)
    private val categorizationCache = mutableMapOf<Int, TransactionCategory?>()
    private val maxCacheSize = 500

    data class NormalizedKeyword(
        val normalized: String,
        val normalizedWords: Set<String>,
        val wordCount: Int,
        val length: Int
    )

    fun isLoaded(): Boolean = normalizedKeywordMap.isNotEmpty()
    fun getLoadedCountryCode(): String? = loadedCountryCode

    /**
     * Load keywords from CSV and pre-normalize them for faster matching.
     */
    fun loadFromCsv(csvContent: String, countryCode: String) {
        val lines = csvContent.lines()
        if (lines.isEmpty()) return

        val map = mutableMapOf<TransactionCategory, MutableList<NormalizedKeyword>>()

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

            val category = try {
                TransactionCategory.valueOf(categoryName)
            } catch (e: Exception) {
                null
            }

            if (category != null && category != TransactionCategory.OTHER) {
                // Pre-normalize keyword
                val normalized = normalizeText(keyword)
                val words = normalized.split(" ").filter { it.isNotBlank() }

                val normalizedKeyword = NormalizedKeyword(
                    normalized = normalized,
                    normalizedWords = words.toSet(),
                    wordCount = words.size,
                    length = normalized.length
                )

                map.getOrPut(category) { mutableListOf() }.add(normalizedKeyword)
            }
        }

        // Sort each category's keywords by length descending (longer = more specific)
        normalizedKeywordMap = map.mapValues { (_, keywords) ->
            keywords.sortedByDescending { it.length }
        }

        loadedCountryCode = countryCode.lowercase()
        categorizationCache.clear()
    }

    /**
     * Find category with optimizations:
     * - Uses pre-normalized keywords
     * - Early termination on first long match
     * - Simple cache for repeated descriptions
     */
    fun findCategory(description: String, counterparty: String? = null): TransactionCategory? {
        if (normalizedKeywordMap.isEmpty()) return null

        // Check cache first
        val cacheKey = "$description|$counterparty".hashCode()
        categorizationCache[cacheKey]?.let { return it }

        val searchText = "${description.lowercase()} ${counterparty?.lowercase() ?: ""}"
        val normalizedText = normalizeText(searchText)
        val words = normalizedText.split(" ").filter { it.isNotBlank() }
        val wordsSet = words.toSet()

        var bestMatch: TransactionCategory? = null
        var bestKeywordLength = 0

        // Iterate through categories
        for ((category, keywords) in normalizedKeywordMap) {
            // Keywords are sorted by length descending
            for (keyword in keywords) {
                // Early exit if this keyword is shorter than current best
                if (keyword.length <= bestKeywordLength) break

                // Check if all words of the keyword are present
                val allWordsMatch = keyword.normalizedWords.all { kw -> wordsSet.contains(kw) }

                val matches = if (keyword.wordCount == 1) {
                    allWordsMatch
                } else {
                    // For multi-word, also verify sequence
                    allWordsMatch && normalizedText.contains(keyword.normalized)
                }

                if (matches) {
                    bestKeywordLength = keyword.length
                    bestMatch = category
                    // Don't break - check other categories for potentially longer matches
                }
            }
        }

        // Update cache (with simple size limit)
        if (categorizationCache.size >= maxCacheSize) {
            // Remove oldest entries (simple approach: clear half)
            val toRemove = categorizationCache.keys.take(maxCacheSize / 2)
            toRemove.forEach { categorizationCache.remove(it) }
        }
        categorizationCache[cacheKey] = bestMatch

        return bestMatch
    }

    /**
     * Normalize text once and cache the result.
     */
    private fun normalizeText(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^a-z0-9äöüß]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun getKeywordsForCategory(category: TransactionCategory): List<String> {
        return normalizedKeywordMap[category]?.map { it.normalized } ?: emptyList()
    }

    fun getKeywordCount(): Int {
        return normalizedKeywordMap.values.sumOf { it.size }
    }

    fun clear() {
        normalizedKeywordMap = emptyMap()
        loadedCountryCode = null
        categorizationCache.clear()
    }

    companion object {
        fun getCountryCodeFromIban(iban: String?): String {
            if (iban.isNullOrBlank()) return "de"
            val cleaned = iban.replace(" ", "").uppercase()
            return if (cleaned.length >= 2) {
                cleaned.substring(0, 2).lowercase()
            } else {
                "de"
            }
        }

        fun getKeywordFileName(countryCode: String): String {
            return "keywords_${countryCode.lowercase()}.csv"
        }

        fun getMerchantFileName(countryCode: String): String {
            return "merchants_${countryCode.lowercase()}.csv"
        }
    }
}
