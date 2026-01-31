package com.bankwise.app.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream

actual class PdfProcessor {

    /**
     * Extract text from PDF with improved layout preservation for bank statements.
     * Uses position-based sorting and spacing adjustments to maintain table structure.
     */
    actual fun extractText(pdfBytes: ByteArray): String? {
        return try {
            val inputStream = ByteArrayInputStream(pdfBytes)
            val document = PDDocument.load(inputStream)

            val stripper = PDFTextStripper().apply {
                // Sort text by position (important for table layout!)
                sortByPosition = true

                // Add spacing between words to preserve column separation
                spacingTolerance = 0.5f

                // Use space as word separator
                wordSeparator = " "

                // Preserve line breaks
                lineSeparator = "\n"

                // Add extra spacing for paragraph breaks
                paragraphStart = "\n"
                paragraphEnd = "\n"

                // Don't drop whitespace-only lines (important for layout)
                addMoreFormatting = true
            }

            val text = stripper.getText(document)
            document.close()

            // Post-process: normalize whitespace while preserving structure
            text.lines()
                .map { line ->
                    // Collapse multiple spaces to single space, but keep line structure
                    line.replace(Regex("\\s{3,}"), "  ") // Keep double space for columns
                        .trim()
                }
                .joinToString("\n")

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Alternative extraction mode optimized for tabular data.
     * Try this if standard extraction doesn't work well.
     */
    fun extractTextTabular(pdfBytes: ByteArray): String? {
        return try {
            val inputStream = ByteArrayInputStream(pdfBytes)
            val document = PDDocument.load(inputStream)

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                // Higher spacing tolerance for better column separation
                spacingTolerance = 1.0f
                // Average character tolerance for word boundaries
                averageCharTolerance = 0.5f
                wordSeparator = "\t"  // Tab separator for columns
                lineSeparator = "\n"
            }

            val text = stripper.getText(document)
            document.close()
            text
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extract text page by page for better control.
     * Returns a list of text for each page.
     */
    fun extractTextByPage(pdfBytes: ByteArray): List<String>? {
        return try {
            val inputStream = ByteArrayInputStream(pdfBytes)
            val document = PDDocument.load(inputStream)
            val pageCount = document.numberOfPages

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
                spacingTolerance = 0.5f
            }

            val pages = mutableListOf<String>()
            for (page in 1..pageCount) {
                stripper.startPage = page
                stripper.endPage = page
                pages.add(stripper.getText(document))
            }

            document.close()
            pages
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get raw extracted text for debugging purposes.
     * This shows exactly what the PDF contains without any processing.
     */
    fun extractTextRaw(pdfBytes: ByteArray): String? {
        return try {
            val inputStream = ByteArrayInputStream(pdfBytes)
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            // Minimal processing - just extract
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
