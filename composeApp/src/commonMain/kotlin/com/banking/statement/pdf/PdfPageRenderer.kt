package com.banking.statement.pdf

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-specific PDF page renderer used by the in-app PDF viewer.
 * Opens a PDF file by absolute path, exposes its page count, and renders
 * individual pages as [ImageBitmap]s sized to a target width.
 *
 * Implementations:
 *  - Android: backed by [android.graphics.pdf.PdfRenderer]
 *  - iOS: backed by PDFKit (stub for now — see iOS PdfPageRenderer)
 *
 * Consumers must call [close] when they are done; the Compose viewer wraps
 * this in a DisposableEffect.
 */
expect class PdfPageRenderer() {
    /** Opens the PDF at [filePath]. Returns true on success. */
    fun open(filePath: String): Boolean

    /** Total number of pages in the currently open document, or 0 if none is open. */
    fun pageCount(): Int

    /**
     * Render the given [pageIndex] (0-based) to an [ImageBitmap] with width
     * [targetWidth] px, preserving aspect ratio. Returns null on failure or
     * if rendering is not supported on this platform yet.
     */
    fun renderPage(pageIndex: Int, targetWidth: Int): ImageBitmap?

    /** Releases any native resources. Safe to call multiple times. */
    fun close()
}
