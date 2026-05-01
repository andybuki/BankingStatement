package com.banking.statement.categorization

/**
 * Normalizes raw transaction descriptions and counterparty names into a
 * canonical merchant token suitable for matching.
 *
 * Real-world bank statements (and especially OCR'd PDFs) decorate merchant
 * names with store numbers, branch identifiers, payment-processor prefixes,
 * city suffixes and transaction-type words. This class strips that noise so
 * "REWE BERLIN 047", "REWE Filiale 1234" and "Kartenzahlung REWE//Munchen/DE"
 * all collapse to the canonical token "rewe".
 */
object MerchantNormalizer {

    private val specialCharsRegex = Regex("[^a-z0-9äöüß\\s]")
    private val whitespaceRegex = Regex("\\s+")

    // Numeric tokens (store numbers, branch IDs) — anything 3+ digits or with a
    // letter+digit mix like "fil047". Pure single/double digits often appear in
    // legitimate names (e.g. "C24") so we keep those.
    private val numericTokenRegex = Regex("\\b\\d{3,}\\b")
    private val mixedAlphaNumRegex = Regex("\\b(?:filiale|fil|nr|no|store|shop|markt)[\\s.-]?\\d+\\b")
    private val trailingDigitsRegex = Regex("\\s\\d+$")

    /** Words that decorate a merchant token but are not part of the brand. */
    private val noiseWords: Set<String> = setOf(
        // Transaction type / processor prefixes
        "kartenzahlung", "lastschrift", "ueberweisung", "überweisung", "sepa",
        "girocard", "ec", "vpay", "v-pay", "visa", "mastercard", "maestro",
        "debit", "credit", "kreditkarte", "ec-kartenzahlung",
        "paypal", "applepay", "googlepay", "klarna", "sofort", "stripe",
        "bargeldauszahlung", "bargeldeinzahlung", "auszahlung", "einzahlung",
        "gutschrift", "abbuchung", "entgelt",
        // Generic suffixes
        "gmbh", "ag", "kg", "ohg", "co", "gbr", "ug", "se", "inc", "ltd", "llc",
        "filiale", "filiali", "fil", "nr", "no", "store", "shop", "markt",
        "branch", "outlet",
        // Country codes / common location indicators
        "de", "at", "ch", "fr", "nl", "es", "it", "uk", "us", "deu", "deutschland",
        "germany", "austria", "switzerland",
        // Generic words that often appear after the brand
        "sagt", "danke", "ihren", "einkauf", "shopping"
    )

    /** Common DE/EU city names that frequently get appended to merchant tokens. */
    private val commonCities: Set<String> = setOf(
        "berlin", "hamburg", "muenchen", "münchen", "munich", "koeln", "köln",
        "cologne", "frankfurt", "stuttgart", "duesseldorf", "düsseldorf",
        "dortmund", "essen", "leipzig", "bremen", "dresden", "hannover",
        "nuernberg", "nürnberg", "nuremberg", "wien", "vienna", "zuerich",
        "zürich", "zurich", "basel", "bern", "amsterdam", "rotterdam",
        "paris", "lyon", "madrid", "barcelona", "rome", "milano", "milan",
        "london", "manchester", "edinburgh", "dublin", "warszawa", "warsaw",
        "praha", "prague", "budapest", "brussels", "antwerpen", "antwerp",
        "lisbon", "lisboa", "porto", "stockholm", "oslo", "copenhagen",
        "helsinki"
    )

    /**
     * Aggressively normalize raw text into a canonical merchant token.
     *
     * Pipeline:
     *  1. lowercase
     *  2. strip mixed alpha-numeric branch markers ("filiale 1234")
     *  3. replace special chars with space
     *  4. drop pure numeric tokens of 3+ digits (store/branch numbers)
     *  5. drop noise words and city names
     *  6. collapse whitespace
     */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        var text = raw.lowercase()
        text = text.replace(mixedAlphaNumRegex, " ")
        text = text.replace(specialCharsRegex, " ")
        text = text.replace(numericTokenRegex, " ")
        text = text.replace(whitespaceRegex, " ").trim()
        text = text.replace(trailingDigitsRegex, "")

        if (text.isBlank()) return ""

        val words = text.split(" ").filter { it.isNotBlank() }
        val cleaned = words.filter { word ->
            word.length > 1 &&
                word !in noiseWords &&
                word !in commonCities
        }

        return cleaned.joinToString(" ").trim()
    }

    /**
     * Light normalization — same character cleanup as [normalize] but keeps
     * all words. Useful when matching against the merchant database where
     * contextual words (e.g. "rewe markt") still help disambiguate.
     */
    fun normalizeLight(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lowercase()
            .replace(specialCharsRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    /**
     * Extract the most likely canonical merchant token (single word or short
     * phrase) from the raw text. Returns null if no meaningful token remains
     * after stripping noise.
     *
     * The heuristic: after [normalize], take the longest remaining word —
     * brand names tend to be the most distinctive token in a description.
     * If multiple words remain and none dominates, return the first two.
     */
    fun extractMerchantToken(raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null

        val words = normalized.split(" ").filter { it.length >= 3 }
        if (words.isEmpty()) return null
        if (words.size == 1) return words[0]

        // Prefer the longest distinctive word as the merchant brand
        val longest = words.maxByOrNull { it.length } ?: return words.first()
        return if (longest.length >= 5) longest else words.take(2).joinToString(" ")
    }
}
