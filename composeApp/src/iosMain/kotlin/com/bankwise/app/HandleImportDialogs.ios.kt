package com.bankwise.app

import androidx.compose.runtime.Composable
import com.bankwise.app.ui.ImportChoice

@Composable
actual fun HandleImportDialogs(
    dialogState: Any?,
    onImportChoice: ((ImportChoice) -> Unit)?,
    onDismissSuccessDialog: (() -> Unit)?
) {
    // iOS implementation - to be implemented when iOS support is added
    // For now, this is a no-op stub
}
