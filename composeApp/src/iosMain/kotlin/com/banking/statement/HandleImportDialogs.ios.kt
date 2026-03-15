package com.banking.statement

import androidx.compose.runtime.Composable
import com.banking.statement.ui.ImportChoice

@Composable
actual fun HandleImportDialogs(
    dialogState: Any?,
    onImportChoice: ((ImportChoice) -> Unit)?,
    onDismissSuccessDialog: (() -> Unit)?,
    onRetryImport: (() -> Unit)?,
    onDismissErrorDialog: (() -> Unit)?
) {
    // iOS implementation - to be implemented when iOS support is added
    // For now, this is a no-op stub
}
