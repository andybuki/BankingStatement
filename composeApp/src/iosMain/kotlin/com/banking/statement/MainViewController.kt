package com.banking.statement

import platform.UIKit.UIViewController
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainViewController : UIViewController() {
    private var transactions by mutableStateOf<List<TransactionDisplay>>(emptyList())
    private var categorySpending by mutableStateOf<List<CategorySpending>>(emptyList())
    private var monthlySummary by mutableStateOf<List<MonthlySummary>>(emptyList())
    private var totalIncome by mutableStateOf(0.0)
    private var totalExpenses by mutableStateOf(0.0)

    private var selectedAccountId by mutableStateOf<Long?>(null)

    private fun recomputeTotalsForSelection() {
        val filtered = if (selectedAccountId == null) transactions else transactions.filter { it.accountId == selectedAccountId }
        totalIncome = filtered.filter { it.amount > 0 }.sumOf { it.amount }
        totalExpenses = filtered.filter { it.amount < 0 }.sumOf { it.amount }
    }

    // When wiring Compose host call App(... onAccountSelected = { accountId -> selectedAccountId = accountId; recomputeTotalsForSelection() })
}
