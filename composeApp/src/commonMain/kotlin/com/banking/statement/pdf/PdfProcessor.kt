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

    /**
     * Extract per-line text along with each line's bounding box on its page.
     * Bounding boxes are in fractional coordinates (0..1 of page width/height,
     * top-left origin). Used to draw a highlight rectangle on the exact line
     * a transaction was extracted from.
     */
    fun extractPageLines(pdfBytes: ByteArray): List<List<PdfLineBox>>?
}

/**
 * Single text line on a PDF page, plus its bounding box in fractional
 * page coordinates (top-left origin, x and y in 0..1 of page width/height).
 */
data class PdfLineBox(
    val text: String,
    val xFrac: Float,
    val yFrac: Float,
    val wFrac: Float,
    val hFrac: Float
)
