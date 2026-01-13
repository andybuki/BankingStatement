package com.banking.statement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.banking.statement.db.TransactionRepository

class MainActivity : ComponentActivity() {
    private lateinit var repository: TransactionRepository

    private var transactions by mutableStateOf<List<TransactionDisplay>>(emptyList())
    private var categorySpending by mutableStateOf<List<CategorySpending>>(emptyList())
    private var monthlySummary by mutableStateOf<List<MonthlySummary>>(emptyList())
    private var totalIncome by mutableStateOf(0.0)
    private var totalExpenses by mutableStateOf(0.0)

    private var selectedAccountId by mutableStateOf<Long?>(null) // NEW

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TransactionRepository(/* driverFactory, etc. */)

        // Load data (existing code should call loadTransactionData)
        // For brevity, call existing loader that sets transactions/categorySpending/monthlySummary
        loadAllData()

        setContent {
            App(
                transactions = transactions,
                categorySpending = categorySpending,
                monthlySummary = monthlySummary,
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                accountsForManagement = listOf(),
                onAccountSelected = { accountId ->
                    selectedAccountId = accountId
                    recomputeTotalsForSelection()
                }
            )
        }
    }

    private fun loadAllData() {
        // Call existing loadTransactionData repository utility (kept as-is) and set transactions etc.
        // After loading transactions, recompute totals for current selection
        // Example (pseudocode):
        // loadTransactionData(repository, ..., onTransactionsLoaded = { txList, catSpending, monthSummary, income, expenses ->
        //   transactions = txList
        //   categorySpending = catSpending
        //   monthlySummary = monthSummary
        //   recomputeTotalsForSelection()
        // })
    }

    private fun recomputeTotalsForSelection() {
        val filtered = if (selectedAccountId == null) transactions else transactions.filter { it.accountId == selectedAccountId }
        totalIncome = filtered.filter { it.amount > 0 }.sumOf { it.amount }
        totalExpenses = filtered.filter { it.amount < 0 }.sumOf { it.amount }
    }
}
