package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult

/**
 * Collection of smaller German bank parsers that use the generic parsing logic
 */

// ============================================================
// C24 Bank Parser
// ============================================================
class C24Parser : GermanBankParser() {
    override val bankName = "C24 Bank"

    private val identifiers = listOf(
        "c24 bank",
        "c24bank",
        "c24-bank",
        "check24"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "C24 Bank")
    }
}

// ============================================================
// Consorsbank Parser
// ============================================================
class ConsorsbankParser : GermanBankParser() {
    override val bankName = "Consorsbank"

    private val identifiers = listOf(
        "consorsbank",
        "consors",
        "bnp paribas",
        "csdbde71",
        "cortal consors",
        "visa 26466"  // Consorsbank VISA card pattern
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Also check for Consorsbank-specific format: "GUTSCHRIFT DD.MM. XXXX DD.MM. XX,XX+"
        val hasConsorsFormat = Regex("""(GUTSCHRIFT|LASTSCHRIFT)\s+\d{2}\.\d{2}\.\s+\d{4}\s+\d{2}\.\d{2}\.\s+[\d,]+[+-]""")
            .containsMatchIn(pdfText.uppercase())
        return identifiers.any { lower.contains(it) } || hasConsorsFormat
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Consorsbank")
    }
}

// ============================================================
// N26 Parser
// ============================================================
class N26Parser : GermanBankParser() {
    override val bankName = "N26"

    private val identifiers = listOf(
        "n26",
        "n26 bank",
        "ntsbdeb1",
        "number26"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "N26")
    }
}

// ============================================================
// TARGOBANK Parser
// ============================================================
class TargobankParser : GermanBankParser() {
    override val bankName = "TARGOBANK"

    private val identifiers = listOf(
        "targobank",
        "targo bank",
        "cmcidedd",
        "cmcideddxxx",
        "trbkdebb",
        "trbkdebbxxx",
        "targobank.de",
        "citibank"  // Former name
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Also check for Targo-specific format: "Datum Tag Buchungstext Ausgaben Einnahmen"
        val hasTargoFormat = lower.contains("ausgaben") && lower.contains("einnahmen") && lower.contains("guthaben/kredit")
        return identifiers.any { lower.contains(it) } || hasTargoFormat
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "TARGOBANK")
    }
}

// ============================================================
// 1822direkt Parser (Frankfurter Sparkasse subsidiary)
// ============================================================
class DirectBank1822Parser : GermanBankParser() {
    override val bankName = "1822direkt"

    private val identifiers = listOf(
        "1822direkt",
        "1822 direkt",
        "1822direct",
        "frankfurter sparkasse",
        "heaborh", // BIC contains this
        "dsl bank",  // DSL Bank is related to 1822direkt
        "sepa-basislastschrift"  // Common in 1822direkt statements
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "1822direkt")
    }
}

// ============================================================
// Apobank Parser (Deutsche Apotheker- und Ärztebank)
// ============================================================
class ApoBankParser : GermanBankParser() {
    override val bankName = "Apobank"

    private val identifiers = listOf(
        "apobank",
        "apo bank",
        "apotheker",
        "ärztebank",
        "daaededd",  // BIC
        "deutsche apotheker"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Also check for Apobank-specific format: "Wertstellung: DD.MM.YYYY"
        val hasApobankFormat = lower.contains("wertstellung:") && lower.contains("kartenzahlung debitkarte")
        return identifiers.any { lower.contains(it) } || hasApobankFormat
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Apobank")
    }
}

// ============================================================
// Tomorrow Bank Parser (Sustainable/Green Bank)
// ============================================================
class TomorrowBankParser : GermanBankParser() {
    override val bankName = "Tomorrow"

    private val identifiers = listOf(
        "tomorrow",
        "tomorrow bank",
        "tomorrow gmbh",
        "tomorrowbank",
        "sobkdehhxxx"  // BIC
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Tomorrow")
    }
}

// ============================================================
// bunq Parser (Dutch neobank)
// ============================================================
class BunqParser : GermanBankParser() {
    override val bankName = "bunq"

    private val identifiers = listOf(
        "bunq",
        "bunq b.v",
        "bunq bank",
        "bunqnl2a"  // BIC
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "bunq")
    }
}

// ============================================================
// Landesbank Berlin (LBB) Parser
// ============================================================
class LandesbankBerlinParser : GermanBankParser() {
    override val bankName = "Landesbank Berlin"

    // BLZ codes from jejik-mt940 PHP library
    private val allowedBlz = listOf(
        "10050000", "10050005", "10050006", "10050007", "10050008"
    )

    private val identifiers = listOf(
        "landesbank berlin",
        "berliner sparkasse",
        "lbb",
        "beladebe"  // BIC prefix
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Check identifiers
        if (identifiers.any { lower.contains(it) }) return true
        // Check BLZ codes
        return allowedBlz.any { pdfText.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Landesbank Berlin")
    }
}

// ============================================================
// HypoVereinsbank / UniCredit Parser
// ============================================================
class HypoVereinsbankParser : GermanBankParser() {
    override val bankName = "HypoVereinsbank"

    // BLZ codes from jejik-mt940 PHP library (partial list)
    private val allowedBlz = listOf(
        "10020890", "20030000", "70020270", "70020001",
        "20730001", "20730002", "20730003", "20730004", "20730005",
        "20730006", "20730007", "20730008", "20730009", "20730010"
    )

    private val identifiers = listOf(
        "hypovereinsbank",
        "hvb",
        "unicredit",
        "unicredit bank",
        "hypobank",
        "vereinsbank",
        "hvbkdeff",  // BIC
        "hyvedemmxxx"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Check identifiers
        if (identifiers.any { lower.contains(it) }) return true
        // Check BLZ codes
        return allowedBlz.any { pdfText.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "HypoVereinsbank")
    }
}

// ============================================================
// LBBW Parser
// ============================================================
class LbbwParser : GermanBankParser() {
    override val bankName = "LBBW"

    private val identifiers = listOf(
        "lbbw",
        "landesbank baden-württemberg",
        "baden-württembergische bank",
        "bw bank",
        "soladest"  // BIC prefix
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "LBBW")
    }
}

// ============================================================
// Comdirect Parser
// ============================================================
class ComdirectParser : GermanBankParser() {
    override val bankName = "comdirect"

    private val identifiers = listOf(
        "comdirect",
        "comdirect bank",
        "cobadehdxxx",  // BIC
        "cobadehd",
        "20041111"  // BLZ
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "comdirect")
    }
}

// ============================================================
// Santander Consumer Bank Parser
// ============================================================
class SantanderParser : GermanBankParser() {
    override val bankName = "Santander"

    private val identifiers = listOf(
        "santander",
        "santander consumer bank",
        "santander bank",
        "scfbde33",  // BIC
        "31010833"  // BLZ
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Santander")
    }
}

// ============================================================
// Sparda Bank Parser
// ============================================================
class SpardaBankParser : GermanBankParser() {
    override val bankName = "Sparda-Bank"

    private val identifiers = listOf(
        "sparda",
        "sparda-bank",
        "sparda bank",
        "genodef1s"  // BIC prefix for Sparda banks
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Sparda-Bank")
    }
}

// ============================================================
// PSD Bank Parser
// ============================================================
class PsdBankParser : GermanBankParser() {
    override val bankName = "PSD Bank"

    private val identifiers = listOf(
        "psd bank",
        "psd-bank",
        "psdbank",
        "genodef1p"  // BIC prefix for PSD banks
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "PSD Bank")
    }
}

// ============================================================
// Generic German Bank Parser (Fallback)
// ============================================================
/**
 * Generic parser for German bank statements that weren't recognized by specific parsers.
 * Attempts to:
 * 1. Extract bank name from address block (e.g., "Deutsche Bank AG")
 * 2. Parse transactions using multiple strategies
 */
class GenericGermanBankParser : GermanBankParser() {
    override val bankName = "German Bank"

    // Keywords that indicate this is a German bank statement
    private val germanIndicators = listOf(
        "kontoauszug", "girokonto", "sparkonto", "tagesgeld",
        "verwendungszweck", "buchung", "lastschrift", "gutschrift",
        "überweisung", "dauerauftrag", "kartenzahlung", "bargeld",
        "sepa", "blz", "kontonummer", "rechnungsabschluss"
    )

    // Patterns to identify bank names in address blocks
    private val bankNamePatterns = listOf(
        Regex("([A-Za-zäöüÄÖÜß\\s]+(?:Bank|Sparkasse|Volksbank|Raiffeisenbank)(?:\\s+[A-Z]{2,})?)", RegexOption.IGNORE_CASE),
        Regex("([A-Za-zäöüÄÖÜß\\s]+(?:AG|GmbH|eG))", RegexOption.IGNORE_CASE),
        Regex("(Deutsche\\s+Bank)", RegexOption.IGNORE_CASE),
        Regex("(Commerzbank)", RegexOption.IGNORE_CASE),
        Regex("(Postbank)", RegexOption.IGNORE_CASE),
        Regex("(ING-DiBa|ING\\s+DiBa)", RegexOption.IGNORE_CASE),
        Regex("(Comdirect)", RegexOption.IGNORE_CASE),
        Regex("(HypoVereinsbank|HVB)", RegexOption.IGNORE_CASE),
        Regex("(Santander)", RegexOption.IGNORE_CASE)
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        // Check if this looks like a German bank statement
        val hasGermanIndicators = germanIndicators.count { lower.contains(it) } >= 2
        val hasIban = lower.contains("de") && Regex("[a-z]{2}\\d{2}\\s?\\d{4}").containsMatchIn(lower)
        val hasGermanDate = Regex("\\d{2}\\.\\d{2}\\.\\d{4}").containsMatchIn(pdfText)
        val hasGermanAmount = Regex("[+-]?\\s*\\d{1,3}(?:[.]\\d{3})*,\\d{2}").containsMatchIn(pdfText)

        return (hasGermanIndicators || hasIban) && hasGermanDate && hasGermanAmount
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        val detectedBankName = extractBankName(pdfText) ?: "German Bank"
        return parseGermanStatement(pdfText, fileName, detectedBankName)
    }

    /**
     * Try to extract bank name from the PDF text
     * Looks for patterns like "Deutsche Bank AG", "Sparkasse München", etc.
     */
    private fun extractBankName(pdfText: String): String? {
        // Try each pattern
        for (pattern in bankNamePatterns) {
            val match = pattern.find(pdfText)
            if (match != null) {
                val name = match.groupValues[1].trim()
                // Validate the name looks reasonable
                if (name.length in 3..50 && !name.all { it.isDigit() }) {
                    return cleanBankName(name)
                }
            }
        }

        // Try to find bank name in first 20 lines (usually in header/address block)
        val lines = pdfText.lines().take(20)
        for (line in lines) {
            val trimmed = line.trim()
            // Look for lines ending with "Bank", "AG", "Sparkasse", etc.
            if (trimmed.contains("Bank", ignoreCase = true) ||
                trimmed.contains("Sparkasse", ignoreCase = true) ||
                trimmed.endsWith("AG") ||
                trimmed.endsWith("eG")) {
                // Clean up the line
                val cleaned = trimmed
                    .replace(Regex("\\d+"), "")
                    .replace(Regex("[,;:]"), "")
                    .trim()
                if (cleaned.length in 5..50) {
                    return cleanBankName(cleaned)
                }
            }
        }

        return null
    }

    private fun cleanBankName(name: String): String {
        return name
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^(Ihr|Ihre|Die|Der|Das)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .take(50)
    }
}
