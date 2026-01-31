package com.bankwise.app.pdf

expect class PdfProcessor() {
    fun extractText(pdfBytes: ByteArray): String?
    fun isPdfFile(bytes: ByteArray): Boolean
}
