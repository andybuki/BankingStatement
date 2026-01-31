package com.banking.statement.pdf

expect class PdfProcessor() {
    fun extractText(pdfBytes: ByteArray): String?
    fun isPdfFile(bytes: ByteArray): Boolean
}
