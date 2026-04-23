package com.banking.statement.pdf

actual class PdfProcessor {

    actual fun extractText(pdfBytes: ByteArray): String? {
        // TODO: Implement using PDFKit on iOS
        // This is a stub for iOS - will be implemented later
        return null
    }

    actual fun extractPages(pdfBytes: ByteArray): List<String>? {
        // TODO: Implement using PDFKit on iOS.
        return null
    }

    actual fun isPdfFile(bytes: ByteArray): Boolean {
        // Check for PDF magic bytes: %PDF-
        if (bytes.size < 5) return false
        return bytes[0] == 0x25.toByte() && // %
               bytes[1] == 0x50.toByte() && // P
               bytes[2] == 0x44.toByte() && // D
               bytes[3] == 0x46.toByte() && // F
               bytes[4] == 0x2D.toByte()    // -
    }
}
