# Performance Optimization Guide

## Current Bottlenecks

1. **Merchant Database Loading**: ~200K entries take significant time to load
2. **Redundant Entries**: Many merchants already covered by keywords
3. **Repeated Text Normalization**: Same text normalized multiple times
4. **Sequential Search**: Linear search through all merchants

## Optimization Strategies

### 1. Filter Redundant Merchant Entries ✅ (Highest Impact)

**Problem**: Merchant DB has many entries like "Restaurant ABC", "Cafe XYZ" that are already handled by keyword "restaurant", "cafe".

**Solution**: Use the provided Python script to filter merchants:

```bash
cd /home/user/BankingStatement
python scripts/filter_merchants.py \
    composeApp/src/androidMain/assets/files/merchants.csv \
    composeApp/src/androidMain/assets/files/merchants_filtered.csv
```

**Expected Results**:
- 50-70% reduction in merchant database size
- 2-3x faster loading time
- 2-3x faster categorization

**What to Keep**:
- Brand names (Lidl, REWE, Edeka, McDonald's, etc.)
- Specific merchant names without generic keywords
- Regional chains

**What to Remove**:
- Generic entries: "Restaurant Müller", "Cafe Schmidt"
- Entries with obvious keywords: "Tankstelle Nord", "Apotheke Süd"
- Duplicates and variations

### 2. Pre-Normalize Keywords (Medium Impact)

**Implementation**: Use `KeywordDatabaseOptimized.kt` (already created)

**Improvements**:
- Pre-normalize all keywords on load
- Sort by length (longest first) for early termination
- Cache normalized transaction text
- Simple LRU cache for repeated transactions

**Expected Results**:
- 40-50% faster keyword matching
- Reduced CPU usage during categorization

### 3. Optimize Merchant Database Lookup (Medium-High Impact)

**Current**: Linear search through all merchants

**Optimizations**:

#### A. Index by First Letter
```kotlin
// In MerchantDatabase.kt
private var merchantIndex: Map<Char, List<Pair<String, String>>> = emptyMap()

fun ensureCacheLoaded() {
    if (merchantCache != null) return
    if (!isLoaded()) return

    val merchants = database.bankingDatabaseQueries
        .getAllMerchantsForCache()
        .executeAsList()

    // Group by first letter for faster lookup
    merchantIndex = merchants
        .groupBy { it.name_normalized.firstOrNull() ?: ' ' }
        .mapValues { (_, list) ->
            list.map { it.name_normalized to it.category_code }
                .sortedByDescending { it.first.length }
        }
}

fun findCategory(description: String, counterparty: String? = null): TransactionCategory? {
    if (!isLoaded()) return null
    ensureCacheLoaded()

    val searchText = normalizeName("$description ${counterparty ?: ""}")
    if (searchText.isBlank()) return null

    val searchWords = searchText.split(" ").filter { it.isNotBlank() }

    // Try each word as potential merchant name
    for (word in searchWords) {
        val firstChar = word.firstOrNull() ?: continue
        val candidates = merchantIndex[firstChar] ?: continue

        for ((merchantName, categoryCode) in candidates) {
            if (matchesAsWord(searchText, searchWords, merchantName)) {
                return categoryCodeMap[categoryCode]
            }
        }
    }

    return null
}
```

**Expected Results**:
- 5-10x faster merchant lookup
- Especially beneficial with large merchant databases

#### B. Lazy Load Merchant Database
```kotlin
// In MainActivity.kt
// Only load merchants when first needed
private fun loadMerchantDatabaseLazy() {
    // Don't load on app start
    // Load only when:
    // 1. First import happens
    // 2. User views spending breakdown
}
```

### 4. Database Optimizations (Low-Medium Impact)

#### A. Add Index for auto_category
```sql
-- In BankingDatabase.sq
CREATE INDEX idx_transactions_auto_category ON transactions(auto_category);
```

#### B. Batch Updates
```kotlin
// Instead of updating transactions one by one
fun fixMiscategorizedSupermarkets(): Int {
    if (transactionCategorizer == null) return 0

    val updates = mutableListOf<Pair<Long, String>>()

    // Collect all updates first
    transactions.forEach { tx ->
        if (needsUpdate(tx)) {
            val newCategory = transactionCategorizer.categorize(parsedTx)
            updates.add(tx.id to newCategory.name)
        }
    }

    // Batch update
    database.transaction {
        updates.forEach { (id, category) ->
            queries.updateTransactionCategory(null, category, id)
        }
    }

    return updates.size
}
```

### 5. Parallelization (Medium Impact)

```kotlin
// In MainActivity.kt - Parallelize merchant loading
coroutineScope.launch(Dispatchers.IO) {
    // Load both in parallel
    val keywordJob = async { loadKeywordDatabase() }
    val merchantJob = async { loadMerchantDatabase() }

    keywordJob.await()
    merchantJob.await()

    // Then run migrations
    val backfillJob = async { repository.backfillAutoCategories() }
    val fixJob = async { repository.fixMiscategorizedSupermarkets() }

    backfillJob.await()
    fixJob.await()
}
```

### 6. Progressive Loading (UI Improvement)

```kotlin
// Show UI immediately, load data progressively
override fun onCreate(savedInstanceState: Bundle?) {
    // 1. Show UI with empty state immediately
    setContent { App(...) }

    // 2. Load critical data first
    coroutineScope.launch {
        loadKeywordDatabase()
        loadRecentTransactions() // Only last 30 days

        // 3. Load full data in background
        withContext(Dispatchers.IO) {
            loadAllTransactions()
            loadMerchantDatabase()
        }
    }
}
```

## Summary of Expected Performance Gains

| Optimization | Difficulty | Impact | Time Saved |
|-------------|-----------|--------|------------|
| Filter Merchant DB | Easy | High | 60-70% load time |
| Pre-normalize Keywords | Medium | Medium | 40-50% keyword matching |
| Index Merchant Lookup | Medium | High | 80-90% merchant search |
| Lazy Load Merchants | Easy | Medium | 100% on startup (if no import) |
| Batch Updates | Easy | Low | 20-30% migration time |
| Parallelize Loading | Easy | Medium | 30-40% overall startup |

## Implementation Priority

1. ✅ **Filter Merchant Database** - Run the Python script now
2. **Switch to KeywordDatabaseOptimized** - Replace in MainActivity
3. **Add Merchant Index** - Modify MerchantDatabase.kt
4. **Add Database Index** - Update BankingDatabase.sq
5. **Lazy Load Merchants** - Defer until first import

## Measuring Performance

Add timing logs to measure improvements:

```kotlin
val startTime = System.currentTimeMillis()
merchantDatabase.loadFromCsv(csvContent)
val duration = System.currentTimeMillis() - startTime
Log.d("Performance", "Merchant DB loaded in ${duration}ms")
```

## Additional Ideas

### A. Two-Tier Merchant Database
- **Tier 1 (Fast)**: Top 1000 most common merchants (Lidl, REWE, etc.)
- **Tier 2 (Full)**: Complete database, loaded on demand

### B. Bloom Filter for Quick Rejection
```kotlin
// Check if word MIGHT be a merchant before searching
private val merchantBloomFilter: BloomFilter

fun mightBeMerchant(word: String): Boolean {
    return merchantBloomFilter.mightContain(word)
}
```

### C. Regular Expression Pre-compilation
```kotlin
// Instead of creating regex on every call
private val normalizationRegex = Regex("[^a-z0-9äöüß]")
private val whitespaceRegex = Regex("\\s+")

private fun normalizeName(name: String): String {
    return name.lowercase()
        .replace(normalizationRegex, " ")
        .replace(whitespaceRegex, " ")
        .trim()
}
```

### D. Use Native SQL for Merchant Lookup
```sql
-- Faster than in-memory matching for very large datasets
SELECT category_code FROM merchants
WHERE name_normalized IN (?, ?, ?, ?)
ORDER BY LENGTH(name_normalized) DESC
LIMIT 1;
```

## Questions to Consider

1. **How often do users import new statements?**
   - If rarely: Lazy load merchants ✅
   - If frequently: Keep in memory

2. **What percentage of transactions match merchants vs keywords?**
   - If <10%: Merchant DB overhead not worth it
   - If >30%: Optimize merchant lookup heavily

3. **Is startup time or categorization time the bigger issue?**
   - Startup: Lazy load, progressive loading
   - Categorization: Optimize matching algorithms

4. **Can we reduce merchant DB size to <10K entries?**
   - If yes: Keep simple linear search
   - If no: Implement indexing/trie structure
