package com.banking.statement.pdf

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy
import platform.PDFKit.PDFDocument

@OptIn(ExperimentalForeignApi::class)
actual class PdfProcessor {

    actual fun extractText(pdfBytes: ByteArray): String? {
        if (pdfBytes.isEmpty()) return null

        // Convert ByteArray to NSData
        val nsData = pdfBytes.usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = pdfBytes.size.toULong()
            )
        }

        // Create PDFDocument from NSData
        val pdfDocument = PDFDocument(data = nsData) ?: run {
            println("PdfProcessor: Failed to create PDF document from data")
            return null
        }

        val pageCount = pdfDocument.pageCount.toInt()
        if (pageCount == 0) {
            println("PdfProcessor: PDF has no pages")
            return null
        }

        val extractedText = StringBuilder()

        for (pageIndex in 0 until pageCount) {
            val page = pdfDocument.pageAtIndex(pageIndex.toULong()) ?: continue
            val pageText = page.string ?: continue

            extractedText.append(pageText)
            if (pageIndex < pageCount - 1) {
                extractedText.append("\n\n")
            }
        }

        val result = extractedText.toString()
        if (result.isBlank()) {
            println("PdfProcessor: No text content found in PDF")
            return null
        }

        return result
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
