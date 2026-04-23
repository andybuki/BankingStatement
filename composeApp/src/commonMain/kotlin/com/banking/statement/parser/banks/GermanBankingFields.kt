package com.banking.statement.parser.banks

// ============================================================
// German Banking Field Patterns (ported from jejik-mt940 PHP)
// These patterns extract structured fields from transaction descriptions
// ============================================================

/**
 * Structured result of parsing German banking field markers from a transaction description.
 */
internal data class GermanTransactionFields(
    val eref: String? = null,
    val kref: String? = null,
    val mref: String? = null,
    val cred: String? = null,
    val svwz: String? = null,
    val iban: String? = null,
    val bic: String? = null,
    val accountHolder: String? = null
)

/**
 * Extract EREF (End-to-end reference) from description
 * Pattern: EREF+ followed by reference text
 */
internal fun extractEref(text: String): String? {
    val pattern = Regex("""EREF\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
    return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Extract KREF (Customer reference) from description
 * Pattern: KREF+ followed by reference text
 */
internal fun extractKref(text: String): String? {
    val pattern = Regex("""KREF\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
    return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Extract MREF (Mandate reference) from description
 * Pattern: MREF+ followed by mandate ID
 */
internal fun extractMref(text: String): String? {
    val pattern = Regex("""MREF\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
    return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Extract CRED (Creditor identifier) from description
 * Pattern: CRED+ followed by creditor ID (usually DE...)
 */
internal fun extractCred(text: String): String? {
    val pattern = Regex("""CRED\+([A-Za-z0-9äöüÄÖÜß./?\-\s,]+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
    return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Extract SVWZ (Verwendungszweck / Payment purpose) from description
 * This is the main payment description text
 */
internal fun extractSvwz(text: String): String? {
    val pattern = Regex("""SVWZ\+(.+?)(?:\s+[A-Z]{4}\+|$)""", RegexOption.IGNORE_CASE)
    return pattern.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Extract IBAN from description
 */
internal fun extractIbanFromDesc(text: String): String? {
    val pattern = Regex("""IBAN\+?:?\s*([A-Z]{2}\d{2}[A-Z0-9]{4,30})""", RegexOption.IGNORE_CASE)
    return pattern.find(text)?.groupValues?.get(1)?.uppercase()?.takeIf { it.length >= 15 }
}

/**
 * Extract BIC from description
 */
internal fun extractBicFromDesc(text: String): String? {
    val pattern = Regex("""BIC\+?:?\s*([A-Z]{4}[A-Z]{2}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)""", RegexOption.IGNORE_CASE)
    return pattern.find(text)?.groupValues?.get(1)?.uppercase()?.takeIf { it.length >= 8 }
}

/**
 * Extract account holder name from description
 * Common patterns: "Auftraggeber:" "Empfänger:" or just name after IBAN
 */
internal fun extractAccountHolder(text: String): String? {
    val patterns = listOf(
        Regex("""(?:Auftraggeber|Empfänger|Zahlungsempfänger|Zahlungspflichtiger)[:\s]+([A-Za-zäöüÄÖÜß\s.\-]+?)(?:\s+IBAN|\s+DE\d|$)""", RegexOption.IGNORE_CASE),
        Regex("""(?:von|an)[:\s]+([A-Za-zäöüÄÖÜß\s.\-]{3,50})""", RegexOption.IGNORE_CASE)
    )
    for (pattern in patterns) {
        val match = pattern.find(text)
        if (match != null) {
            return match.groupValues[1].trim().takeIf { it.length > 2 }
        }
    }
    return null
}

/**
 * Parse all German banking fields from a description text.
 */
internal fun parseGermanFields(text: String): GermanTransactionFields {
    return GermanTransactionFields(
        eref = extractEref(text),
        kref = extractKref(text),
        mref = extractMref(text),
        cred = extractCred(text),
        svwz = extractSvwz(text),
        iban = extractIbanFromDesc(text),
        bic = extractBicFromDesc(text),
        accountHolder = extractAccountHolder(text)
    )
}

/**
 * Build a clean description from German fields.
 * Prioritizes SVWZ (purpose) over raw text.
 */
internal fun buildCleanDescription(rawDescription: String): String {
    val svwz = extractSvwz(rawDescription)
    if (svwz != null && svwz.length > 5) {
        return svwz
    }

    var cleaned = rawDescription
    listOf("EREF+", "KREF+", "MREF+", "CRED+", "SVWZ+", "DEBT+", "IBAN+", "BIC+").forEach { marker ->
        cleaned = cleaned.replace(Regex("""$marker[^\s]*\s*""", RegexOption.IGNORE_CASE), " ")
    }
    return cleaned.replace(Regex("""\s+"""), " ").trim()
}
