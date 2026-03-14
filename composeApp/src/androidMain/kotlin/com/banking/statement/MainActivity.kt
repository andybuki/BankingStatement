package com.banking.statement

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
            App(
                onPickFile = { filePickerLauncher.launch("*/*") },
                importState = viewModel.importState,
                stats = viewModel.stats,
                transactions = viewModel.transactions,
                categorySpending = viewModel.categorySpending,
                monthlySummary = viewModel.monthlySummary,
                totalIncome = viewModel.totalIncome,
                totalExpenses = viewModel.totalExpenses,
                dialogState = viewModel.dialogState,
                onImportChoice = { choice -> viewModel.handleImportChoice(choice) },
                onDismissSuccessDialog = { viewModel.dismissSuccessDialog() },
                accountsForManagement = viewModel.accountsForManagement,
                onDeleteAccount = { accountId -> viewModel.deleteAccount(accountId) },
                onEditAccount = { accountId, newName -> viewModel.editAccount(accountId, newName) },
                onClearAllData = { viewModel.clearAllData() },
                onShareTransactions = { format, txList, accountName ->
                    viewModel.shareTransactions(format, txList, accountName)
                },
                onShareSpending = { format, data ->
                    viewModel.shareSpending(format, data)
                },
                themeMode = viewModel.currentThemeMode,
                onThemeModeChange = { mode -> viewModel.setThemeMode(mode) },
                onCategoryChange = { transaction, newCategory ->
                    viewModel.handleCategoryChange(transaction, newCategory)
                },
                customCategories = viewModel.customCategories,
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
                showTutorial = viewModel.showTutorial,
                onDismissTutorial = { viewModel.dismissTutorial() },
                onEmailClick = { email -> viewModel.openEmailClient(email) }
            )

            // Bank selection dialog
            if (viewModel.dialogState.showBankSelectionDialog) {
                BankSelectionDialog(
                    detectedBanks = viewModel.dialogState.detectedBanks,
                    onBankSelected = { bankName -> viewModel.handleBankSelection(bankName) },
                    onDismiss = { viewModel.cancelBankSelection() }
                )
            }
        }
    }
}
