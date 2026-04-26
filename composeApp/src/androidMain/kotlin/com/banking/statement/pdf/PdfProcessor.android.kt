package com.banking.statement.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
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
     * Extract text page by page for source-linking (common interface).
     * Returns null when PDFBox cannot open the document.
     */
    actual fun extractPages(pdfBytes: ByteArray): List<String>? = extractTextByPage(pdfBytes)

    /**
     * Extract per-line text + bounding box per page. Used to highlight
     * the exact line a transaction came from in the in-app PDF viewer.
     */
    actual fun extractPageLines(pdfBytes: ByteArray): List<List<PdfLineBox>>? {
        return try {
            val inputStream = ByteArrayInputStream(pdfBytes)
            val document = PDDocument.load(inputStream)
            val stripper = LineBoxStripper().apply {
                sortByPosition = true
                spacingTolerance = 0.5f
            }
            // Triggers stripper.processPages → per-page line collection.
            stripper.getText(document)
            val result = stripper.pages.map { page ->
                page.toList()
            }
            document.close()
            result
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

/**
 * Custom PDFTextStripper that captures per-line bounding boxes alongside
 * the line text. Coordinates are converted to fractional values in the
 * top-left coordinate system used by the rendered page image.
 */
private class LineBoxStripper : PDFTextStripper() {
    val pages: MutableList<MutableList<PdfLineBox>> = mutableListOf()
    private var pageWidthPts: Float = 0f
    private var pageHeightPts: Float = 0f
    private val pendingPositions: MutableList<TextPosition> = mutableListOf()

    override fun startPage(page: PDPage) {
        pages.add(mutableListOf())
        val box = page.mediaBox
        pageWidthPts = box.width
        pageHeightPts = box.height
        pendingPositions.clear()
        super.startPage(page)
    }

    override fun writeString(text: String, textPositions: List<TextPosition>) {
        if (textPositions.isNotEmpty()) {
            pendingPositions.addAll(textPositions)
        }
        super.writeString(text, textPositions)
    }

    override fun writeLineSeparator() {
        flushPendingLine()
        super.writeLineSeparator()
    }

    override fun endPage(page: PDPage) {
        flushPendingLine()
        super.endPage(page)
    }

    private fun flushPendingLine() {
        if (pendingPositions.isEmpty()) return
        if (pageWidthPts <= 0f || pageHeightPts <= 0f) {
            pendingPositions.clear()
            return
        }
        val text = pendingPositions.joinToString("") { it.unicode ?: "" }.trim()
        if (text.isBlank()) {
            pendingPositions.clear()
            return
        }
        // PDFBox with sortByPosition=true reports y in the upper-left
        // coordinate system: y is the BASELINE of the glyph from the page
        // top, height is glyph height. So line top = y - height.
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minTop = Float.POSITIVE_INFINITY
        var maxBottom = Float.NEGATIVE_INFINITY
        pendingPositions.forEach { tp ->
            val x = tp.x
            val endX = tp.x + tp.width
            val baseline = tp.y
            val height = tp.height.takeIf { it > 0f } ?: tp.fontSizeInPt
            val top = baseline - height
            if (x < minX) minX = x
            if (endX > maxX) maxX = endX
            if (top < minTop) minTop = top
            if (baseline > maxBottom) maxBottom = baseline
        }
        // Pad vertically a touch so the highlight is comfortably bigger
        // than the glyphs themselves.
        val padY = (maxBottom - minTop).coerceAtLeast(1f) * 0.20f
        val xFrac = (minX / pageWidthPts).coerceIn(0f, 1f)
        val wFrac = ((maxX - minX) / pageWidthPts).coerceIn(0.001f, 1f)
        val yFrac = ((minTop - padY) / pageHeightPts).coerceIn(0f, 1f)
        val hFrac = ((maxBottom - minTop + 2 * padY) / pageHeightPts).coerceIn(0.001f, 1f)
        pages.last().add(PdfLineBox(text, xFrac, yFrac, wFrac, hFrac))
        pendingPositions.clear()
    }
}
