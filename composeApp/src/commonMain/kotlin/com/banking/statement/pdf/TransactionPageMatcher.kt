package com.banking.statement.pdf

/**
 * Best-effort matcher that finds which page of a multi-page PDF text each
 * transaction came from. Used right after a successful import to populate
 * `transactions.source_page` / `transactions.source_line_snippet`, which the
 * "view source" feature uses to jump the PDF viewer to the relevant page.
 *
 * The strategy is conservative: we look for the transaction's counterparty
 * name (or a distinctive slice of its description) inside each page's text,
 * preferably on a line that also contains the transaction's amount. If
 * nothing matches we leave the page as null and fall back to page 0 at
 * view-time.
 */
object TransactionPageMatcher {

    data class Match(val pageIndex: Int, val lineSnippet: String)

    /**
     * @param pageTexts text of each page in the source PDF (page 0 first)
     * @param description transaction description (may be multi-line)
     * @param counterparty transaction counterparty name, if any
     * @param amount signed amount of the transaction
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

        val amountNeedle = formatAmountForSearch(amount)

        // Two-pass: first look for a line that matches BOTH a needle and the
        // amount (strong signal). If that fails, fall back to needle-only.
        pageTexts.forEachIndexed { pageIndex, pageText ->
            val lines = pageText.lines()
            val strong = lines.firstOrNull { line ->
                val lower = line.lowercase()
                amountNeedle != null && lower.contains(amountNeedle) &&
                    needles.any { lower.contains(it) }
            }
            if (strong != null) {
                return Match(pageIndex, strong.trim().take(160))
            }
        }

        pageTexts.forEachIndexed { pageIndex, pageText ->
            val lines = pageText.lines()
            val weak = lines.firstOrNull { line ->
                val lower = line.lowercase()
                needles.any { lower.contains(it) }
            }
            if (weak != null) {
                return Match(pageIndex, weak.trim().take(160))
            }
        }

        return null
    }

    private fun buildNeedles(description: String, counterparty: String?): List<String> {
        val candidates = mutableListOf<String>()
        counterparty?.let { candidates += it }
        candidates += description
        return candidates
            .asSequence()
            .flatMap { it.split(Regex("""[\s,;/]+""")).asSequence() }
            .map { it.lowercase().filter { ch -> ch.isLetterOrDigit() } }
            .filter { it.length >= 4 }
            .distinct()
            .take(6)
            .toList()
    }

    /**
     * Build a locale-tolerant substring for the amount. Bank PDFs format with
     * either '.' or ',' as decimal separator, so we match on the digits only
     * and let the page text contain the separator of its choice.
     */
    private fun formatAmountForSearch(amount: Double): String? {
        val abs = kotlin.math.abs(amount)
        if (abs == 0.0) return null
        val cents = kotlin.math.round(abs * 100).toLong()
        val whole = cents / 100
        val frac = (cents % 100).toString().padStart(2, '0')
        // Keep separator-agnostic: "<whole>.<frac>" and "<whole>,<frac>" — we
        // return the comma form because all supported statement formats in
        // this repo use the German style, but callers lowercase before match.
        return "$whole,$frac"
    }
}
