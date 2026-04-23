package com.banking.statement.pdf

expect class PdfProcessor() {
    fun extractText(pdfBytes: ByteArray): String?
    fun isPdfFile(bytes: ByteArray): Boolean

    /**
     * Extract text page by page. Returns null on platforms that don't yet
     * support PDF text extraction (iOS stub). Each list entry contains the
     * extracted text for one page, in document order.
     */
    fun extractPages(pdfBytes: ByteArray): List<String>?
}
