# Feature Implementation Guide

Quick implementation guides for top priority features based on competitive analysis.

---

## 🎯 Priority 1: Visual Charts & Graphs

### Why It Matters
- Users process visual data 60,000x faster than text
- Charts make patterns immediately obvious
- Competitors ALL have this feature

### Implementation Options

#### Option A: Vico (Recommended - Native Compose)
```kotlin
dependencies {
    implementation("com.patrykandpatrick.vico:compose:1.13.1")
}
```

**Pros:**
- Pure Compose/Kotlin Multiplatform
- Beautiful, Material Design 3
- Highly customizable
- Good documentation

**Example - Pie Chart:**
```kotlin
@Composable
fun CategorySpendingPieChart(categorySpending: List<CategorySpending>) {
    val chartEntryModel = entryModelOf(
        categorySpending.map { entry(it.category.ordinal, it.percentage) }
    )

    Chart(
        chart = pieChart(),
        model = chartEntryModel,
        modifier = Modifier.height(200.dp)
    )
}
```

#### Option B: Compose Charts (Simpler)
```kotlin
dependencies {
    implementation("io.github.thechance101:chart:Beta-0.0.5")
}
```

**Pros:**
- Very simple API
- Lightweight
- Good for basic charts

### Recommended Charts

1. **Spending Overview Screen:**
   - Pie/Donut chart for category breakdown
   - Line chart for monthly spending trends
   - Bar chart for income vs expenses

2. **Transaction Screen:**
   - Line chart showing daily spending
   - Bar chart for top merchants

3. **Home Screen:**
   - Sparkline mini-chart showing last 7 days

### Database Changes
```sql
-- No database changes needed - use existing data!
```

### UI Changes
```kotlin
// In SpendingOverviewScreen.kt
@Composable
fun SpendingChartsSection(categorySpending: List<CategorySpending>) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Spending by Category", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(16.dp))

            // Pie chart
            PieChart(
                data = categorySpending.map {
                    PieChartData(it.category.displayName, it.percentage)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Legend
            CategoryLegend(categorySpending)
        }
    }
}
```

---

## 🎯 Priority 2: Budget Tracking

### Why It Matters
- #1 feature request in personal finance apps
- Drives user engagement (check budget daily)
- Creates "aha moments" when users see they're overspending

### Database Schema
```sql
-- Add to BankingDatabase.sq

CREATE TABLE budgets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    amount REAL NOT NULL,
    period TEXT NOT NULL DEFAULT 'MONTHLY', -- WEEKLY, MONTHLY, YEARLY
    start_date INTEGER NOT NULL,
    end_date INTEGER,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
);

CREATE INDEX idx_budgets_category ON budgets(category);
CREATE INDEX idx_budgets_active ON budgets(is_active);

-- Queries
getBudgetForCategory:
SELECT * FROM budgets
WHERE category = ? AND is_active = 1
ORDER BY created_at DESC
LIMIT 1;

getAllActiveBudgets:
SELECT * FROM budgets WHERE is_active = 1;

insertBudget:
INSERT INTO budgets(category, amount, period, start_date)
VALUES (?, ?, ?, ?);

updateBudget:
UPDATE budgets SET amount = ?, period = ?, is_active = ? WHERE id = ?;
```

### Data Model
```kotlin
// In categorization/Budget.kt
data class Budget(
    val id: Long = 0,
    val category: TransactionCategory,
    val amount: Double,
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isActive: Boolean = true
)

enum class BudgetPeriod {
    WEEKLY,
    MONTHLY,
    YEARLY
}

data class BudgetProgress(
    val budget: Budget,
    val spent: Double,
    val remaining: Double,
    val percentageUsed: Float,
    val isOverBudget: Boolean,
    val daysLeft: Int
) {
    val status: BudgetStatus get() = when {
        percentageUsed >= 100f -> BudgetStatus.OVER
        percentageUsed >= 90f -> BudgetStatus.WARNING
        percentageUsed >= 75f -> BudgetStatus.MODERATE
        else -> BudgetStatus.GOOD
    }
}

enum class BudgetStatus {
    GOOD, MODERATE, WARNING, OVER
}
```

### Repository Functions
```kotlin
// In TransactionRepository.kt
fun getBudgetsWithProgress(
    startDate: LocalDate,
    endDate: LocalDate
): List<BudgetProgress> {
    val budgets = queries.getAllActiveBudgets().executeAsList()

    return budgets.map { budget ->
        // Get spending for this category in the period
        val spent = queries.getCategorySpending(
            category = budget.category,
            startDate = startDate.toEpochSeconds(),
            endDate = endDate.toEpochSeconds()
        ).executeAsOne()

        val remaining = budget.amount - abs(spent)
        val percentageUsed = (abs(spent) / budget.amount * 100).toFloat()
        val daysLeft = (endDate.toEpochDays() - Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()).toInt()

        BudgetProgress(
            budget = budget,
            spent = abs(spent),
            remaining = remaining,
            percentageUsed = percentageUsed,
            isOverBudget = spent > budget.amount,
            daysLeft = daysLeft
        )
    }
}
```

### UI Components
```kotlin
// Budget progress card
@Composable
fun BudgetCard(progress: BudgetProgress) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (progress.status) {
                BudgetStatus.GOOD -> MaterialTheme.colorScheme.surfaceVariant
                BudgetStatus.MODERATE -> Color(0xFFFFF3E0)
                BudgetStatus.WARNING -> Color(0xFFFFE0B2)
                BudgetStatus.OVER -> Color(0xFFFFCDD2)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    progress.budget.category.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${progress.percentageUsed.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (progress.isOverBudget)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = (progress.percentageUsed / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (progress.isOverBudget)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "€${String.format("%.2f", progress.spent)} / €${String.format("%.2f", progress.budget.amount)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "${progress.daysLeft} days left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progress.isOverBudget) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "⚠️ Over budget by €${String.format("%.2f", -progress.remaining)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// Budget setting dialog
@Composable
fun SetBudgetDialog(
    category: TransactionCategory,
    currentBudget: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amount by remember { mutableStateOf(currentBudget?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Budget for ${category.displayName}") },
        text = {
            Column {
                Text("Set your monthly budget for this category")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Budget Amount (€)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    amount.toDoubleOrNull()?.let { onSave(it) }
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

### Integration in Spending Screen
```kotlin
// Add to SpendingOverviewScreen.kt
@Composable
fun SpendingOverviewScreen(
    // ... existing parameters
    budgets: List<BudgetProgress> = emptyList(),
    onSetBudget: (TransactionCategory, Double) -> Unit = { _, _ -> }
) {
    Column {
        // Existing content...

        // Budget section
        if (budgets.isNotEmpty()) {
            Text(
                "Budgets",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            budgets.forEach { progress ->
                BudgetCard(progress)
            }
        }

        // Category spending with budget set option
        categorySpending.forEach { category ->
            CategoryCard(
                category = category,
                onSetBudget = { onSetBudget(category.category, 0.0) }
            )
        }
    }
}
```

---

## 🎯 Priority 3: Recurring Transaction Detection

### Why It Matters
- Identifies subscriptions automatically
- Helps users find forgotten subscriptions
- Enables subscription management features

### Algorithm
```kotlin
// In categorization/RecurringTransactionDetector.kt
class RecurringTransactionDetector {

    data class RecurringTransaction(
        val merchantName: String,
        val category: TransactionCategory,
        val averageAmount: Double,
        val frequency: RecurringFrequency,
        val transactions: List<Transactions>,
        val nextExpectedDate: LocalDate?,
        val lastOccurrence: LocalDate
    )

    enum class RecurringFrequency(val days: Int) {
        WEEKLY(7),
        BIWEEKLY(14),
        MONTHLY(30),
        QUARTERLY(90),
        YEARLY(365)
    }

    /**
     * Detect recurring transactions.
     *
     * Algorithm:
     * 1. Group transactions by merchant/counterparty
     * 2. For each group with 3+ transactions:
     *    - Calculate intervals between transactions
     *    - Check if intervals are consistent (within ±3 days)
     *    - Check if amounts are similar (within ±10%)
     * 3. Classify frequency and predict next occurrence
     */
    fun detectRecurring(
        transactions: List<Transactions>,
        minOccurrences: Int = 3
    ): List<RecurringTransaction> {
        return transactions
            .filter { it.amount < 0 } // Only expenses
            .groupBy { it.counterparty_name ?: it.description }
            .filter { (_, txs) -> txs.size >= minOccurrences }
            .mapNotNull { (merchant, txs) ->
                analyzeRecurrence(merchant, txs)
            }
            .sortedByDescending { it.averageAmount }
    }

    private fun analyzeRecurrence(
        merchant: String,
        transactions: List<Transactions>
    ): RecurringTransaction? {
        if (transactions.size < 3) return null

        // Sort by date
        val sorted = transactions.sortedBy { it.booking_date }

        // Calculate intervals in days
        val intervals = sorted.zipWithNext { a, b ->
            val days = (b.booking_date - a.booking_date) / (24 * 3600)
            days.toInt()
        }

        // Check if intervals are consistent
        val avgInterval = intervals.average()
        val maxDeviation = intervals.maxOfOrNull { abs(it - avgInterval) } ?: 0.0

        if (maxDeviation > 3) return null // Not consistent enough

        // Check if amounts are similar
        val amounts = sorted.map { it.amount }
        val avgAmount = amounts.average()
        val amountVariance = amounts.maxOfOrNull { abs(it - avgAmount) / abs(avgAmount) } ?: 0.0

        if (amountVariance > 0.10) return null // Amounts vary too much

        // Determine frequency
        val frequency = when {
            avgInterval in 5..9 -> RecurringFrequency.WEEKLY
            avgInterval in 12..16 -> RecurringFrequency.BIWEEKLY
            avgInterval in 28..32 -> RecurringFrequency.MONTHLY
            avgInterval in 88..92 -> RecurringFrequency.QUARTERLY
            avgInterval in 360..370 -> RecurringFrequency.YEARLY
            else -> return null // Doesn't match known pattern
        }

        // Predict next occurrence
        val lastDate = Instant.fromEpochSeconds(sorted.last().booking_date)
            .toLocalDateTime(TimeZone.UTC).date
        val nextExpectedDate = lastDate.plus(frequency.days, DateTimeUnit.DAY)

        // Get category from first transaction
        val category = sorted.first().auto_category?.let {
            TransactionCategory.valueOf(it)
        } ?: TransactionCategory.OTHER

        return RecurringTransaction(
            merchantName = merchant,
            category = category,
            averageAmount = abs(avgAmount),
            frequency = frequency,
            transactions = sorted,
            nextExpectedDate = nextExpectedDate,
            lastOccurrence = lastDate
        )
    }
}
```

### UI - Subscriptions Screen
```kotlin
@Composable
fun SubscriptionsScreen(
    recurring: List<RecurringTransaction>,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions & Recurring") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            // Summary card
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Total Monthly Recurring",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "€${recurring.filter { it.frequency == RecurringFrequency.MONTHLY }.sumOf { it.averageAmount }.format()}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Recurring transactions
            items(recurring) { tx ->
                RecurringTransactionCard(tx)
            }
        }
    }
}

@Composable
fun RecurringTransactionCard(recurring: RecurringTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recurring.merchantName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "${recurring.frequency.name.lowercase().capitalize()} • ${recurring.category.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recurring.nextExpectedDate?.let { next ->
                    Text(
                        "Next: ${next.format()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "€${recurring.averageAmount.format()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    "${recurring.transactions.size} payments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

---

## 🎯 Priority 4: Transaction Splitting

### Why It Matters
- Real-world transactions often span multiple categories
- Example: Grocery trip includes food + household items
- Users want accurate category breakdowns

### Database Schema
```sql
-- Add to BankingDatabase.sq

CREATE TABLE transaction_splits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    amount REAL NOT NULL,
    note TEXT,
    created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
);

CREATE INDEX idx_splits_transaction ON transaction_splits(transaction_id);

-- Queries
getSplitsForTransaction:
SELECT * FROM transaction_splits WHERE transaction_id = ?;

insertSplit:
INSERT INTO transaction_splits(transaction_id, category, amount, note)
VALUES (?, ?, ?, ?);

deleteSplitsForTransaction:
DELETE FROM transaction_splits WHERE transaction_id = ?;
```

### Data Model
```kotlin
data class TransactionSplit(
    val id: Long = 0,
    val transactionId: Long,
    val category: TransactionCategory,
    val amount: Double,
    val note: String? = null
)

data class SplitTransaction(
    val transaction: Transactions,
    val splits: List<TransactionSplit>
) {
    val isSplit: Boolean get() = splits.isNotEmpty()
    val remainingAmount: Double get() = transaction.amount - splits.sumOf { it.amount }
}
```

### UI - Split Dialog
```kotlin
@Composable
fun SplitTransactionDialog(
    transaction: TransactionDisplay,
    existingSplits: List<TransactionSplit> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (List<TransactionSplit>) -> Unit
) {
    var splits by remember {
        mutableStateOf(existingSplits.ifEmpty {
            listOf(TransactionSplit(0, transaction.id, TransactionCategory.OTHER, 0.0))
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Transaction") },
        text = {
            LazyColumn {
                item {
                    Text(
                        "Original: €${abs(transaction.amount).format()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                itemsIndexed(splits) { index, split ->
                    SplitEntryRow(
                        split = split,
                        onUpdate = { updated ->
                            splits = splits.toMutableList().apply {
                                set(index, updated)
                            }
                        },
                        onRemove = {
                            splits = splits.toMutableList().apply {
                                removeAt(index)
                            }
                        }
                    )
                }

                item {
                    TextButton(onClick = {
                        splits = splits + TransactionSplit(
                            0, transaction.id, TransactionCategory.OTHER, 0.0
                        )
                    }) {
                        Text("+ Add Split")
                    }

                    val remaining = abs(transaction.amount) - splits.sumOf { abs(it.amount) }
                    Text(
                        "Remaining: €${remaining.format()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remaining < 0)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(splits) },
                enabled = abs(transaction.amount) == splits.sumOf { abs(it.amount) }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
```

---

## 📋 Implementation Checklist

### Week 1: Charts
- [ ] Add Vico dependency
- [ ] Create PieChart component for category spending
- [ ] Create LineChart component for monthly trends
- [ ] Add charts to SpendingOverviewScreen
- [ ] Test on both Android & iOS

### Week 2: Budgets
- [ ] Add budgets table to database
- [ ] Create Budget data models
- [ ] Add budget CRUD queries
- [ ] Implement BudgetRepository functions
- [ ] Create BudgetCard UI component
- [ ] Add budget setting dialog
- [ ] Integrate into SpendingOverviewScreen

### Week 3: Recurring Detection
- [ ] Create RecurringTransactionDetector
- [ ] Implement detection algorithm
- [ ] Add SubscriptionsScreen
- [ ] Create RecurringTransactionCard component
- [ ] Add navigation to subscriptions
- [ ] Test with real data

### Week 4: Transaction Splitting
- [ ] Add transaction_splits table
- [ ] Create TransactionSplit models
- [ ] Implement split CRUD operations
- [ ] Create SplitTransactionDialog
- [ ] Update spending calculations to handle splits
- [ ] Add split indicator in transaction list

---

## 🧪 Testing Recommendations

For each feature:
1. **Unit Tests**: Test business logic (detection algorithms, calculations)
2. **UI Tests**: Test user flows (setting budget, splitting transaction)
3. **Integration Tests**: Test database operations
4. **Performance Tests**: Ensure features don't slow down app

Example test:
```kotlin
class RecurringTransactionDetectorTest {
    @Test
    fun detectMonthlySubscription() {
        val transactions = listOf(
            // Netflix: €12.99 monthly
            createTransaction("Netflix", -12.99, "2024-01-15"),
            createTransaction("Netflix", -12.99, "2024-02-15"),
            createTransaction("Netflix", -12.99, "2024-03-15"),
            createTransaction("Netflix", -12.99, "2024-04-15")
        )

        val detector = RecurringTransactionDetector()
        val recurring = detector.detectRecurring(transactions)

        assertEquals(1, recurring.size)
        assertEquals("Netflix", recurring[0].merchantName)
        assertEquals(RecurringFrequency.MONTHLY, recurring[0].frequency)
        assertEquals(12.99, recurring[0].averageAmount, 0.01)
    }
}
```
