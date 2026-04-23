package com.banking.statement

import androidx.compose.runtime.Composable
import com.banking.statement.ui.ImportChoice

/**
 * Platform-specific dialog rendering for the import flow.
 * Implementations live in androidMain and iosMain.
 */
@Composable
expect fun HandleImportDialogs(
    dialogState: Any?,
    onImportChoice: ((ImportChoice) -> Unit)?,
    onDismissSuccessDialog: (() -> Unit)?,
    onRetryImport: (() -> Unit)? = null,
    onDismissErrorDialog: (() -> Unit)? = null,
    onRateApp: (() -> Unit)? = null,
    onRateLater: (() -> Unit)? = null,
    onRateNever: (() -> Unit)? = null
)
