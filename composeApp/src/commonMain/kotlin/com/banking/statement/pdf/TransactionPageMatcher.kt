package com.banking.statement.pdf

/**
 * Best-effort matcher that finds the page AND specific line of a multi-page
 * PDF a transaction came from. Used right after a successful import to
 * populate `transactions.source_page` / `source_line_snippet` /
 * `source_bbox`, which the in-app PDF viewer uses to render a highlight
 * rectangle directly over the line on the rendered page.
 *
 * The strategy is conservative:
 *  - Tokenise the transaction's counterparty + description into "needles"
 *    (lower-cased, ≥3-char alphanumeric stems) and require the line to
 *    contain at least one of them.
 *  - Strongly prefer lines that ALSO contain a string close to the
 *    transaction amount (in either German `1.234,56` or English
 *    `1,234.56` formats).
 *  - On failure, leave page null and let the viewer fall back to page 0.
 */
object TransactionPageMatcher {

    data class Match(
        val pageIndex: Int,
        val lineSnippet: String,
        /** "x,y,w,h" fractional coordinates, or null if positions weren't available. */
        val bbox: String?
    )

    /**
     * Match against per-page lines with bounding boxes. Returns a Match
     * including bbox so the viewer can draw a highlight rectangle.
     */
    fun matchWithBoxes(
        pageLines: List<List<PdfLineBox>>,
        description: String,
        counterparty: String?,
        amount: Double
    ): Match? {
        if (pageLines.isEmpty()) return null
        val needles = buildNeedles(description, counterparty)
        if (needles.isEmpty()) return null

        val amountForms = formatAmountForSearch(amount)

        // Strong: needle + amount on the same line.
        pageLines.forEachIndexed { pageIndex, lines ->
            lines.firstOrNull { line ->
                val lower = line.text.lowercase()
                amountForms.isNotEmpty() &&
                    amountForms.any { lower.contains(it) } &&
                    needles.any { lower.contains(it) }
            }?.let { return Match(pageIndex, line(it), bbox(it)) }
        }

        // Weak: any line containing a needle.
        pageLines.forEachIndexed { pageIndex, lines ->
            lines.firstOrNull { line ->
                val lower = line.text.lowercase()
                needles.any { lower.contains(it) }
            }?.let { return Match(pageIndex, line(it), bbox(it)) }
        }

        return null
    }

    /**
     * Page-only fallback when bounding boxes aren't available (e.g. iOS).
     * Returns a Match with bbox = null.
     */
    fun match(
        pageTexts: List<String>,
        description: String,
        counterparty: String?,
        amount: Double
    ): Match? {
        if (pageTexts.isEmpty()) return null
        val needles = buildNeedles(description, counterparty)
        if (needles.isEmpty()) return null

        val amountForms = formatAmountForSearch(amount)

        pageTexts.forEachIndexed { pageIndex, pageText ->
            val lines = pageText.lines()
            val strong = lines.firstOrNull { line ->
                val lower = line.lowercase()
                amountForms.isNotEmpty() &&
                    amountForms.any { lower.contains(it) } &&
                    needles.any { lower.contains(it) }
            }
            if (strong != null) {
                return Match(pageIndex, strong.trim().take(160), null)
            }
        }

        pageTexts.forEachIndexed { pageIndex, pageText ->
            val lines = pageText.lines()
            val weak = lines.firstOrNull { line ->
                val lower = line.lowercase()
                needles.any { lower.contains(it) }
            }
            if (weak != null) {
                return Match(pageIndex, weak.trim().take(160), null)
            }
        }

        return null
    }

    private fun line(box: PdfLineBox): String = box.text.trim().take(160)

    private fun bbox(box: PdfLineBox): String =
        "${fmt(box.xFrac)},${fmt(box.yFrac)},${fmt(box.wFrac)},${fmt(box.hFrac)}"

    private fun fmt(v: Float): String {
        // 4 decimals is plenty for fractional page coordinates and keeps
        // the column compact.
        val rounded = (v * 10000f).toInt() / 10000f
        return rounded.toString()
    }

    private fun buildNeedles(description: String, counterparty: String?): List<String> {
        val candidates = mutableListOf<String>()
        counterparty?.let { candidates += it }
        candidates += description
        return candidates
            .asSequence()
            .flatMap { it.split(Regex("""[\s,;/.:()\[\]\-]+""")).asSequence() }
            .map { it.lowercase().filter { ch -> ch.isLetterOrDigit() } }
            .filter { it.length >= 3 }
            .filter { tok -> NOISE_NEEDLES.none { it == tok } }
            .distinct()
            .take(8)
            .toList()
    }

    /**
     * Build amount substrings to search for. Bank PDFs vary between German
     * `1.234,56` and English `1,234.56`, so we try both with and without
     * the thousands separator. We also try a no-separator variant for
     * smaller amounts.
     */
    private fun formatAmountForSearch(amount: Double): List<String> {
        val abs = kotlin.math.abs(amount)
        if (abs == 0.0) return emptyList()
        val cents = kotlin.math.round(abs * 100).toLong()
        val whole = cents / 100
        val frac = (cents % 100).toString().padStart(2, '0')
        val wholeStr = whole.toString()
        val variants = mutableSetOf<String>()
        variants += "$wholeStr,$frac"
        variants += "$wholeStr.$frac"
        if (whole >= 1000) {
            // German thousands separator: 1.234,56
            variants += "${groupThousands(wholeStr, '.')},$frac"
            // English thousands separator: 1,234.56
            variants += "${groupThousands(wholeStr, ',')}.$frac"
        }
        return variants.map { it.lowercase() }
    }

    private fun groupThousands(whole: String, sep: Char): String {
        val sb = StringBuilder()
        val len = whole.length
        for (i in whole.indices) {
            sb.append(whole[i])
            val fromEnd = len - i - 1
            if (fromEnd > 0 && fromEnd % 3 == 0) sb.append(sep)
        }
        return sb.toString()
    }

    /**
     * Common boilerplate words that show up in transaction descriptions but
     * also on every PDF and would cause false matches on header/footer rows.
     */
    private val NOISE_NEEDLES = setOf(
        "sepa", "lastschrift", "ueberweisung", "uberweisung", "kartenzahlung",
        "transfer", "payment", "direct", "debit", "card", "buchungstag",
        "wertstellung", "valuta", "betrag", "umsatz", "girokonto", "konto",
        "iban", "bic", "ref", "ende", "saldo", "balance", "datum", "date",
        "kontoauszug", "statement", "vorgangsart", "verwendungszweck",
        "auftraggeber", "empfaenger", "empfanger", "buchung"
    )
}
