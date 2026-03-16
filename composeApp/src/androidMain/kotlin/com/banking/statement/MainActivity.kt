package com.banking.statement

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.banking.statement.ui.BankSelectionDialog
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.BankingStatementTheme
import com.banking.statement.ui.theme.ThemeMode
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Thin Activity shell. All state and business logic lives in [MainViewModel].
 * File processing is handled by [FileImportProcessor].
 * Data classes are in [ImportModels.kt].
 */
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModel()

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

        setContent {
            val importState by viewModel.importState.collectAsState()
            val stats by viewModel.stats.collectAsState()
            val financialState by viewModel.financialState.collectAsState()
            val dialogState by viewModel.dialogState.collectAsState()
            val accountsForManagement by viewModel.accountsForManagement.collectAsState()
            val appSettings by viewModel.appSettings.collectAsState()

            // Biometric lock state
            var isAuthenticated by remember { mutableStateOf(!viewModel.isBiometricLockEnabled()) }
            var authError by remember { mutableStateOf<String?>(null) }

            // Trigger biometric prompt on first composition if lock is enabled
            LaunchedEffect(Unit) {
                if (viewModel.isBiometricLockEnabled()) {
                    viewModel.getBiometricLockManager().authenticate(
                        activity = this@MainActivity,
                        onSuccess = {
                            isAuthenticated = true
                            authError = null
                        },
                        onError = { error ->
                            authError = error
                        }
                    )
                }
            }

            BankingStatementTheme(themeMode = appSettings.themeMode) {
                AnimatedVisibility(
                    visible = !isAuthenticated,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BiometricLockScreen(
                        errorMessage = authError,
                        onRetry = {
                            authError = null
                            viewModel.getBiometricLockManager().authenticate(
                                activity = this@MainActivity,
                                onSuccess = {
                                    isAuthenticated = true
                                    authError = null
                                },
                                onError = { error ->
                                    authError = error
                                }
                            )
                        }
                    )
                }

                AnimatedVisibility(
                    visible = isAuthenticated,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
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
                        onRetryImport = { viewModel.retryImport() },
                        onDismissErrorDialog = { viewModel.dismissErrorDialog() },
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
                        showOnboarding = appSettings.showOnboarding,
                        onCompleteOnboarding = { viewModel.completeOnboarding() },
                        showTutorial = appSettings.showTutorial,
                        onDismissTutorial = { viewModel.dismissTutorial() },
                        onEmailClick = { email -> viewModel.openEmailClient(email) },
                        biometricLockEnabled = appSettings.biometricLockEnabled,
                        biometricAvailable = appSettings.biometricAvailable,
                        onBiometricLockChange = { enabled ->
                            viewModel.setBiometricLockEnabled(enabled)
                        },
                        hasMoreTransactions = financialState.hasMoreTransactions,
                        isLoadingMoreTransactions = financialState.isLoadingMore,
                        onLoadMoreTransactions = { viewModel.loadMoreTransactions() }
                    )
                }
            }

            // Bank selection dialog
            if (isAuthenticated && dialogState.showBankSelectionDialog) {
                BankSelectionDialog(
                    detectedBanks = dialogState.detectedBanks,
                    onBankSelected = { bankName -> viewModel.handleBankSelection(bankName) },
                    onDismiss = { viewModel.cancelBankSelection() }
                )
            }
        }
    }
}

/**
 * Full-screen lock overlay shown when biometric authentication is required.
 */
@Composable
private fun BiometricLockScreen(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.MainBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = AppColors.Primary
            )

            Text(
                text = "Bankwise",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = AppColors.HeaderText
            )

            Text(
                text = "Authenticate to access your financial data",
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.HeaderSecondaryText,
                textAlign = TextAlign.Center
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.ButtonPrimary,
                    contentColor = AppColors.ButtonPrimaryText
                )
            ) {
                Text("Unlock")
            }
        }
    }
}
