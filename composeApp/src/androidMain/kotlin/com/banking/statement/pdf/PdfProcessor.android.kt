package com.banking.statement.pdf

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

actual class PdfProcessor {

    actual fun extractText(pdfBytes: ByteArray): String? {
        return try {
            val inputStream = ByteArrayInputStream(pdfBytes)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
