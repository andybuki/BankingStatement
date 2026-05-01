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

    // Pure-numeric tokens (store numbers, branch IDs, embedded dates).
    // Matches whole-word digits only — "c24" stays intact because there's no
    // word boundary between the letter and digits.
    private val numericTokenRegex = Regex("\\b\\d+\\b")
    private val mixedAlphaNumRegex = Regex("\\b(?:filiale|fil|nr|no|store|shop|markt)[\\s.-]?\\d+\\b")

    // Multi-word payment-processor prefixes that need to be stripped before
    // word-level noise filtering, since the individual words ("apple", "pay",
    // "google") would otherwise be mistaken for the merchant brand.
    private val multiWordProcessorRegex = Regex(
        "\\b(apple\\s*pay|google\\s*pay|samsung\\s*pay|garmin\\s*pay|" +
            "kontaktlose?\\s*zahlung|contactless\\s*payment)\\b"
    )

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
     *  2. strip multi-word processor prefixes ("apple pay", "google pay")
     *  3. strip mixed alpha-numeric branch markers ("filiale 1234")
     *  4. replace special chars with space
     *  5. drop pure-numeric tokens (store/branch numbers, dates)
     *  6. drop noise words and city names
     *  7. collapse whitespace
     */
    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""

        var text = raw.lowercase()
        text = text.replace(multiWordProcessorRegex, " ")
        text = text.replace(mixedAlphaNumRegex, " ")
        text = text.replace(specialCharsRegex, " ")
        text = text.replace(numericTokenRegex, " ")
        text = text.replace(whitespaceRegex, " ").trim()

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
     * Extract the most likely canonical merchant token from the raw text.
     * Returns null if no meaningful token remains after stripping noise.
     *
     * The heuristic: after [normalize], take the FIRST distinctive word.
     * In practice, brand names sit at the front of bank-statement merchant
     * strings ("REWE BERLIN 047", "ALNATURA Filiale 12", "Amazon Mktp DE"),
     * with descriptors and locations trailing. Picking the longest word
     * fragments grouping for any brand whose descriptor outsizes the brand
     * itself ("ALNATURA Produktgenossenschaft" → "produktgenossenschaft"
     * vs "ALNATURA Filiale 3" → "alnatura").
     *
     * Pure-digit words and 1-character tokens are skipped so dates or short
     * filler in descriptions don't become the canonical key.
     */
    fun extractMerchantToken(raw: String): String? {
        val normalized = normalize(raw)
        if (normalized.isBlank()) return null

        val words = normalized.split(" ").filter { word ->
            word.length >= 2 && !word.all { it.isDigit() }
        }
        return words.firstOrNull()
    }
}
