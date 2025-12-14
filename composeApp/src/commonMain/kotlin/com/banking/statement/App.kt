package com.banking.statement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banking.statement.pdf.FilePickerResult
import com.banking.statement.pdf.PdfProcessor
import com.banking.statement.validation.BankStatementValidator
import com.banking.statement.validation.ValidationResult
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App(
    onPickFile: (() -> Unit)? = null,
    selectedFile: FilePickerResult? = null,
    onFileProcessed: () -> Unit = {}
) {
    MaterialTheme {
        var validationResult by remember { mutableStateOf<ValidationResult?>(null) }
        var isProcessing by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        // Process file when selected
        LaunchedEffect(selectedFile) {
            selectedFile?.let { file ->
                isProcessing = true
                errorMessage = null
                validationResult = null

                try {
                    val pdfProcessor = PdfProcessor()

                    // Check if it's a PDF file
                    if (!pdfProcessor.isPdfFile(file.bytes)) {
                        errorMessage = "Selected file is not a PDF"
                        isProcessing = false
                        onFileProcessed()
                        return@LaunchedEffect
                    }

                    // Extract text from PDF
                    val text = pdfProcessor.extractText(file.bytes)

                    // Validate as bank statement
                    val validator = BankStatementValidator()
                    validationResult = validator.validate(text)

                } catch (e: Exception) {
                    errorMessage = "Error processing PDF: ${e.message}"
                }

                isProcessing = false
                onFileProcessed()
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(48.dp))

                // Title
                Text(
                    text = "Bank Statement Analyzer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Upload your bank statement PDF to validate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Upload Button
                Button(
                    onClick = { onPickFile?.invoke() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isProcessing && onPickFile != null,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Processing...")
                    } else {
                        Text(
                            text = "Upload Bank Statement",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Supported format: PDF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Error Message
                AnimatedVisibility(visible = errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✗",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Validation Result
                AnimatedVisibility(visible = validationResult != null) {
                    validationResult?.let { result ->
                        ValidationResultCard(result = result)
                    }
                }
            }
        }
    }
}

@Composable
fun ValidationResultCard(result: ValidationResult) {
    val containerColor = if (result.isValid) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }

    val contentColor = if (result.isValid) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    val icon = if (result.isValid) "✓" else "✗"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineMedium,
                    color = contentColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (result.isValid) "Valid Bank Statement" else "Not a Bank Statement",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = "Score: ${result.score}/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            if (result.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = contentColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                // Details
                Text(
                    text = "Detection Details:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                result.details.forEach { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.9f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            if (!result.isValid) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tip: Make sure the PDF contains readable text (not a scanned image) and is an actual bank statement.",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
