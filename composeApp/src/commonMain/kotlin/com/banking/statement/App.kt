package com.banking.statement

import androidx.compose.runtime.Composable
import com.banking.statement.ui.TransactionListScreen

@Composable
fun App(
    transactions: List<TransactionDisplay> = emptyList(),
    categorySpending: List<CategorySpending> = emptyList(),
    monthlySummary: List<MonthlySummary> = emptyList(),
    totalIncome: Double = 0.0,
    totalExpenses: Double = 0.0,
    accountsForManagement: List<AccountManagementItem> = emptyList(),
    onDeleteAccount: ((Long) -> Unit)? = null,
    onEditAccount: ((Long, String) -> Unit)? = null,
    onClearAllData: (() -> Unit)? = null,
    onShareTransactions: ((ExportFormat, List<TransactionDisplay>, String?) -> Unit)? = null,
    onAccountSelected: (Long?) -> Unit = {}, // NEW
) {
    // ... other UI scaffolding ...

    // Transaction list screen
    TransactionListScreen(
        transactions = transactions,
        accounts = accountsForManagement.map { AccountFilterOption(it.id, it.name) },
        selectedAccountId = null, // parent may pass actual selected state when wiring from Activity/ViewController
        onAccountSelected = onAccountSelected,
        customCategories = emptyList(),
        onShare = onShareTransactions
    )
}
