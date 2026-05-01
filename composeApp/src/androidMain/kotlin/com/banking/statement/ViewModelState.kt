package com.banking.statement

import com.banking.statement.categorization.CustomCategory
import com.banking.statement.ui.CategorySpending
import com.banking.statement.ui.MonthlySummary
import com.banking.statement.ui.TransactionDisplay
import com.banking.statement.ui.theme.ThemeMode

/**
 * Consolidated UI state for the financial data displayed across screens.
 */
data class FinancialUiState(
    val transactions: List<TransactionDisplay> = emptyList(),
    /**
     * Full list of expense transactions across every imported statement,
     * used by the Merchants tab so its aggregation is independent of the
     * pagination that drives the Activity tab. Without this, the merchants
     * list only sees the first page (most-recent ~100 rows) and recurring
     * brands fragment / under-count after additional months are imported.
     */
    val allMerchantTransactions: List<TransactionDisplay> = emptyList(),
    val categorySpending: List<CategorySpending> = emptyList(),
    val monthlySummary: List<MonthlySummary> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val hasMoreTransactions: Boolean = false,
    val isLoadingMore: Boolean = false
)

/**
 * Consolidated UI state for app-level settings and preferences.
 */
data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showTutorial: Boolean = false,
    val showOnboarding: Boolean = false,
    val customCategories: List<CustomCategory> = emptyList(),
    val biometricLockEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val remindersEnabled: Boolean = true,
    val pdfAccessEnabled: Boolean = true
)

/**
 * Android alias for the common PDF viewer screen state so the ViewModel
 * can emit a StateFlow<PdfViewerUiState> that App.kt can consume.
 */
typealias PdfViewerUiState = PdfViewerScreenState
