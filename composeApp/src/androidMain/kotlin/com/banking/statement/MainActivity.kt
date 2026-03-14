package com.banking.statement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.banking.statement.ui.BankSelectionDialog
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * Thin Activity shell. All state and business logic lives in [MainViewModel].
 * File processing is handled by [FileImportProcessor].
 * Data classes are in [ImportModels.kt].
 */
class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.processFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Initialize PDFBox
        PDFBoxResourceLoader.init(applicationContext)

        // Create ViewModel
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(this)
        )[MainViewModel::class.java]

        setContent {
            val importState by viewModel.importState.collectAsState()
            val stats by viewModel.stats.collectAsState()
            val financialState by viewModel.financialState.collectAsState()
            val dialogState by viewModel.dialogState.collectAsState()
            val accountsForManagement by viewModel.accountsForManagement.collectAsState()
            val appSettings by viewModel.appSettings.collectAsState()

            App(
                onPickFile = { filePickerLauncher.launch("*/*") },
                importState = importState,
                stats = stats,
                transactions = financialState.transactions,
                categorySpending = financialState.categorySpending,
                monthlySummary = financialState.monthlySummary,
                totalIncome = financialState.totalIncome,
                totalExpenses = financialState.totalExpenses,
                dialogState = dialogState,
                onImportChoice = { choice -> viewModel.handleImportChoice(choice) },
                onDismissSuccessDialog = { viewModel.dismissSuccessDialog() },
                accountsForManagement = accountsForManagement,
                onDeleteAccount = { accountId -> viewModel.deleteAccount(accountId) },
                onEditAccount = { accountId, newName -> viewModel.editAccount(accountId, newName) },
                onClearAllData = { viewModel.clearAllData() },
                onShareTransactions = { format, txList, accountName ->
                    viewModel.shareTransactions(format, txList, accountName)
                },
                onShareSpending = { format, data ->
                    viewModel.shareSpending(format, data)
                },
                themeMode = appSettings.themeMode,
                onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
                onCategoryChange = { transaction, newCategory ->
                    viewModel.handleCategoryChange(transaction, newCategory)
                },
                customCategories = appSettings.customCategories,
                onCustomCategoryChange = { transaction, categoryId ->
                    viewModel.handleCustomCategoryChange(transaction, categoryId)
                },
                onAddCustomCategory = { name, icon, color ->
                    viewModel.addCustomCategory(name, icon, color)
                },
                onEditCustomCategory = { id, name, icon, color ->
                    viewModel.editCustomCategory(id, name, icon, color)
                },
                onDeleteCustomCategory = { id ->
                    viewModel.deleteCustomCategory(id)
                },
                showTutorial = appSettings.showTutorial,
                onDismissTutorial = { viewModel.dismissTutorial() },
                onEmailClick = { email -> viewModel.openEmailClient(email) }
            )

            // Bank selection dialog
            if (dialogState.showBankSelectionDialog) {
                BankSelectionDialog(
                    detectedBanks = dialogState.detectedBanks,
                    onBankSelected = { bankName -> viewModel.handleBankSelection(bankName) },
                    onDismiss = { viewModel.cancelBankSelection() }
                )
            }
        }
    }
}
