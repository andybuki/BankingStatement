package com.banking.statement.pdf

import androidx.compose.ui.graphics.ImageBitmap

/**
 * iOS stub for the PDF page renderer. PDFKit integration (via
 * CGPDFDocument / PDFDocument) is not yet implemented; the viewer will
 * display a "preview unavailable" message on iOS until this is wired up.
 */
actual class PdfPageRenderer {
    actual fun open(filePath: String): Boolean = false
    actual fun pageCount(): Int = 0
    actual fun renderPage(pageIndex: Int, targetWidth: Int): ImageBitmap? = null
    actual fun close() { /* no-op */ }
}
