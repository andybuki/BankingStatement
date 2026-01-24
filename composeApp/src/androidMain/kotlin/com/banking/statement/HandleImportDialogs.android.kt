package com.banking.statement

import androidx.compose.runtime.Composable
import com.banking.statement.ui.ImportAccountDialog
import com.banking.statement.ui.ImportChoice
import com.banking.statement.ui.ImportSuccessDialog

@Composable
actual fun HandleImportDialogs(
    dialogState: Any?,
    onImportChoice: ((ImportChoice) -> Unit)?,
    onDismissSuccessDialog: (() -> Unit)?
) {
    // Cast to the Android-specific ImportDialogState
    val state = dialogState as? ImportDialogState ?: return

    // Show account selection dialog
    if (state.showAccountDialog && state.pendingImport != null) {
        val pending = state.pendingImport
        ImportAccountDialog(
            bankName = pending.parseResult.bankName,
            iban = pending.parseResult.accountIban,
            statementPeriod = pending.parseResult.statementPeriod,
            transactionCount = pending.parseResult.transactions.size,
            existingAccounts = state.existingAccounts,
            suggestedAccountName = pending.parseResult.bankName,
            onChoice = { choice ->
                onImportChoice?.invoke(choice)
            }
        )
    }

    // Show success dialog
    if (state.showSuccessDialog && state.importResult != null) {
        val result = state.importResult
        // Get account name - for new accounts it's the bank name, for existing it would need to be looked up
        val accountName = if (result.isNewAccount) {
            state.pendingImport?.parseResult?.bankName ?: "Account"
        } else {
            state.existingAccounts.find { it.id == result.accountId }?.name ?: "Account"
        }

        ImportSuccessDialog(
            accountName = accountName,
            transactionsImported = result.transactionsImported,
            duplicatesSkipped = result.duplicatesSkipped,
            isNewAccount = result.isNewAccount,
            isDuplicateStatement = result.isDuplicateStatement,
            onDismiss = {
                onDismissSuccessDialog?.invoke()
            }
        )
    }
}
