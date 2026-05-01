package com.banking.statement.categorization

import com.banking.statement.db.BankingDatabase

/**
 * Database-backed merchant lookup for transaction categorization.
 * Uses a pre-loaded CSV of known merchants mapped to categories.
 *
 * Performance optimizations:
 * - Single-word merchants indexed in HashMap for O(1) lookup
 * - Multi-word merchants indexed by first word for O(n/k) lookup
 * - Pre-compiled regex for normalization
 * - Lazy-load cache only on first findCategory call
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
        "ft" to TransactionCategory.SUBSCRIPTIONS, // fitness -> subscriptions
        "md" to TransactionCategory.HEALTH,        // medical/health
        "sm" to TransactionCategory.SUPERMARKET,   // supermarket
        "el" to TransactionCategory.SHOPPING,      // electronics
        "be" to TransactionCategory.SHOPPING,      // beauty
        "cl" to TransactionCategory.SHOPPING,      // clothing
        "bk" to TransactionCategory.RESTAURANT,    // bakery
        "tr" to TransactionCategory.TRAVEL         // travel
    )

    // Pre-compiled regex for normalization (avoids re-compilation on every call)
    private val specialCharsRegex = Regex("[^a-z0-9äöüß\\s]")
    private val whitespaceRegex = Regex("\\s+")

    // O(1) lookup for single-word merchants: word -> categoryCode
    private var singleWordIndex: HashMap<String, String>? = null

    // Multi-word merchants indexed by first word: firstWord -> list of (fullPhrase, categoryCode)
    // Each list is sorted by phrase length descending (longer matches first)
    private var multiWordIndex: HashMap<String, MutableList<Pair<String, String>>>? = null

    // First-character bucket of single-word merchants for fuzzy fallback.
    // We keep these separate to bound the cost of Levenshtein scans — a query
    // word only fuzzy-matches against merchants in nearby letter buckets.
    private var fuzzyBuckets: HashMap<Char, MutableList<String>>? = null

    // Curated aliases (alias -> canonical name) loaded from MerchantAliases.
    // Resolved against the loaded merchant index at cache-build time so an
    // alias matches whatever category the canonical merchant maps to.
    private val aliasOverrides = HashMap<String, String>()

    // Track whether cache has been initialized
    private var cacheInitialized = false

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

        // Build indexed caches while loading
        val singleWords = HashMap<String, String>(totalLines)
        val multiWords = HashMap<String, MutableList<Pair<String, String>>>()
        val fuzzy = HashMap<Char, MutableList<String>>()

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

                        // Index into appropriate structure
                        indexMerchant(nameNormalized, categoryCode, singleWords, multiWords, fuzzy)
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

        // Sort multi-word lists by phrase length descending (longer matches first)
        for ((_, list) in multiWords) {
            list.sortByDescending { it.first.length }
        }

        // Apply curated aliases — each alias inherits the canonical merchant's
        // category, so e.g. "rewe" → "rewe markt" both resolve to SUPERMARKET.
        applyAliases(singleWords, multiWords, fuzzy)

        singleWordIndex = singleWords
        multiWordIndex = multiWords
        fuzzyBuckets = fuzzy
        cacheInitialized = true
    }

    /**
     * Index a merchant into the appropriate lookup structure.
     */
    private fun indexMerchant(
        nameNormalized: String,
        categoryCode: String,
        singleWords: HashMap<String, String>,
        multiWords: HashMap<String, MutableList<Pair<String, String>>>,
        fuzzy: HashMap<Char, MutableList<String>>
    ) {
        val words = nameNormalized.split(" ").filter { it.isNotBlank() }
        if (words.size == 1) {
            // Single word: direct HashMap lookup
            // Keep the first category code if duplicate (longer name would have been multi-word)
            singleWords.putIfAbsent(nameNormalized, categoryCode)
            // Bucket by first character for fuzzy fallback (only longer names
            // are worth fuzzy matching — short brands have too many neighbors)
            if (nameNormalized.length >= 4) {
                fuzzy.getOrPut(nameNormalized[0]) { mutableListOf() }
                    .add(nameNormalized)
            }
        } else if (words.size > 1) {
            // Multi-word: index by first word
            val firstWord = words[0]
            multiWords.getOrPut(firstWord) { mutableListOf() }
                .add(nameNormalized to categoryCode)
        }
    }

    /**
     * Resolve curated aliases against the loaded merchant index. Each alias
     * is added to the single-word index (and fuzzy bucket) pointing to the
     * canonical merchant's category — so OCR'd or shortened forms still
     * categorize correctly even when they aren't in the merchant CSV.
     */
    private fun applyAliases(
        singleWords: HashMap<String, String>,
        multiWords: HashMap<String, MutableList<Pair<String, String>>>,
        fuzzy: HashMap<Char, MutableList<String>>
    ) {
        for ((alias, canonical) in MerchantAliases.aliases + aliasOverrides) {
            val aliasNorm = normalizeName(alias)
            val canonicalNorm = normalizeName(canonical)
            if (aliasNorm.isBlank() || canonicalNorm.isBlank()) continue
            if (singleWords.containsKey(aliasNorm)) continue

            // Resolve canonical to a category code
            val code = singleWords[canonicalNorm]
                ?: multiWords[canonicalNorm.split(" ").first()]
                    ?.firstOrNull { it.first == canonicalNorm }?.second
                ?: continue

            val aliasWords = aliasNorm.split(" ").filter { it.isNotBlank() }
            if (aliasWords.size == 1) {
                singleWords[aliasNorm] = code
                if (aliasNorm.length >= 4) {
                    fuzzy.getOrPut(aliasNorm[0]) { mutableListOf() }
                        .add(aliasNorm)
                }
            } else if (aliasWords.size > 1) {
                multiWords.getOrPut(aliasWords[0]) { mutableListOf() }
                    .add(aliasNorm to code)
            }
        }
        // Re-sort any multi-word buckets we touched
        for ((_, list) in multiWords) {
            list.sortByDescending { it.first.length }
        }
    }

    /**
     * Register an alias at runtime (e.g. from user-corrected merchant names).
     * Takes effect on the next [ensureCacheLoaded] / [loadFromCsv] cycle, or
     * immediately for the in-memory caches.
     */
    fun registerAlias(alias: String, canonical: String) {
        val aliasNorm = normalizeName(alias)
        val canonicalNorm = normalizeName(canonical)
        if (aliasNorm.isBlank() || canonicalNorm.isBlank()) return
        aliasOverrides[aliasNorm] = canonicalNorm

        val singles = singleWordIndex
        val multis = multiWordIndex
        val fuzzy = fuzzyBuckets
        if (singles != null && multis != null && fuzzy != null) {
            applyAliases(singles, multis, fuzzy)
        }
    }

    /**
     * Lazy-load cache from database on first access.
     * Only called if cache wasn't built during loadFromCsv.
     */
    fun ensureCacheLoaded() {
        if (cacheInitialized) return
        if (!isLoaded()) return

        try {
            val merchants = database.bankingDatabaseQueries
                .getAllMerchantsForCache()
                .executeAsList()

            val singleWords = HashMap<String, String>(merchants.size)
            val multiWords = HashMap<String, MutableList<Pair<String, String>>>()
            val fuzzy = HashMap<Char, MutableList<String>>()

            for (merchant in merchants) {
                indexMerchant(merchant.name_normalized, merchant.category_code, singleWords, multiWords, fuzzy)
            }

            // Sort multi-word lists by phrase length descending
            for ((_, list) in multiWords) {
                list.sortByDescending { it.first.length }
            }

            applyAliases(singleWords, multiWords, fuzzy)

            singleWordIndex = singleWords
            multiWordIndex = multiWords
            fuzzyBuckets = fuzzy
            cacheInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Find category for a transaction description/counterparty.
     * Returns null if no match found.
     *
     * Uses HashMap for O(1) single-word lookup and first-word index
     * for efficient multi-word matching.
     */
    fun findCategory(description: String, counterparty: String? = null): TransactionCategory? {
        // Skip if no merchants loaded
        if (!isLoaded()) return null

        // Lazy-load cache on first call
        ensureCacheLoaded()

        val rawCombined = "$description ${counterparty ?: ""}"
        val searchText = normalizeName(rawCombined)
        if (searchText.isBlank()) return null

        // Build a noise-free search text by stripping store numbers, city
        // suffixes and processor prefixes. Fuzzy matching runs against this
        // tokenized form so OCR'd "REWE BERLIN 047" → "rewe" before scoring.
        val cleanedText = MerchantNormalizer.normalize(rawCombined)
        val searchWords = searchText.split(" ").filter { it.isNotBlank() }
        val searchWordsSet = searchWords.toSet()
        val cleanedWords = cleanedText.split(" ")
            .filter { it.isNotBlank() }
            .toSet()

        // Track best match (longest phrase wins)
        var bestCategoryCode: String? = null
        var bestMatchLength = 0

        // 1) Check multi-word merchants first (longer matches = higher priority)
        val multiIdx = multiWordIndex
        if (multiIdx != null) {
            for (word in searchWordsSet) {
                val candidates = multiIdx[word] ?: continue
                // Candidates are sorted by length descending
                for ((phrase, categoryCode) in candidates) {
                    if (phrase.length <= bestMatchLength) break // early exit, rest are shorter
                    val phraseWords = phrase.split(" ").filter { it.isNotBlank() }
                    if (phraseWords.all { it in searchWordsSet } && searchText.contains(phrase)) {
                        bestCategoryCode = categoryCode
                        bestMatchLength = phrase.length
                        break // this candidate list is sorted, first match is longest
                    }
                }
            }
        }

        // 2) Check single-word merchants via HashMap O(1) lookup
        val singleIdx = singleWordIndex
        if (singleIdx != null) {
            for (word in searchWordsSet) {
                if (word.length > bestMatchLength) {
                    val categoryCode = singleIdx[word]
                    if (categoryCode != null) {
                        bestCategoryCode = categoryCode
                        bestMatchLength = word.length
                    }
                }
            }
        }

        if (bestCategoryCode != null) {
            return categoryCodeMap[bestCategoryCode]
        }

        // 3) Fuzzy fallback — only kicks in when no exact match was found.
        // Searches single-word merchants in the same first-letter bucket(s)
        // as each cleaned query token, using combined trigram + Levenshtein
        // similarity. Threshold is conservative to avoid false positives.
        return fuzzyFindCategory(cleanedWords)
    }

    /**
     * Fuzzy fallback: for each cleaned query word ≥4 chars, scan single-word
     * merchants in the same first-letter bucket and return the best match
     * above the similarity threshold. Bucket lookup keeps this cheap even
     * for 100K+ merchant databases.
     */
    private fun fuzzyFindCategory(queryWords: Set<String>): TransactionCategory? {
        val buckets = fuzzyBuckets ?: return null
        val singles = singleWordIndex ?: return null
        if (queryWords.isEmpty()) return null

        var bestCode: String? = null
        var bestScore = FuzzyMatcher.DEFAULT_SIMILARITY_THRESHOLD

        for (word in queryWords) {
            if (word.length < 4) continue

            // Check the same-letter bucket and (cheaply) the adjacent ones,
            // since OCR errors occasionally swap the first character.
            val candidates = buckets[word[0]] ?: continue
            val match = FuzzyMatcher.bestMatch(
                query = word,
                candidates = candidates,
                threshold = bestScore,
                keyOf = { it }
            ) ?: continue

            if (match.score > bestScore) {
                val code = singles[match.candidate] ?: continue
                bestCode = code
                bestScore = match.score
            }
        }

        return bestCode?.let { categoryCodeMap[it] }
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
            .replace(specialCharsRegex, " ")
            .replace(whitespaceRegex, " ")
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
                "ft" -> TransactionCategory.SUBSCRIPTIONS // fitness -> subscriptions
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
