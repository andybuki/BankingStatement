package com.banking.statement

/**
 * Common-main mirror of the ViewModel's PDF viewer state, so the App
 * composable (which lives in commonMain) can receive it without depending
 * on Android-specific types.
 */
data class PdfViewerScreenState(
    val isOpen: Boolean = false,
    val statementId: Long? = null,
    val filePath: String? = null,
    val fileName: String = "",
    val initialPage: Int = 0,
    val highlightSnippet: String? = null,
    val highlightTitle: String? = null,
    /** "x,y,w,h" fractional page coords for the matched line, drawn as an
     *  amber rectangle over the rendered page. Null hides the overlay. */
    val highlightBbox: String? = null
)
