package com.bankwise.app.parser.banks

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for 1822direkt Bank statements (Frankfurter Sparkasse subsidiary)
 *
 * Format:
 * Buchung     Vorgang                      Auftraggeber / Empfänger         Umsatz
 * Wertstellung Kto. Auftraggeber/Empfänger  Verwendungszweck
 *
 * 16.11.2017   SEPA-Basislastschrift       Stadtverwaltung Roßleben        -30,82€
 * 16.11.2017   DE67 8205 5000 3400 0046 50  KK003382 Steuerv 15.11.17
 *
 * Amount format: -30,82€, +9,00€, -1.000,00€ (German: dot thousand, comma decimal)
 */
class DirectBank1822Parser : GermanBankParser() {
    override val bankName = "1822direkt"

    // CERTAIN: unique identifiers
    private val certainIdentifiers = listOf(
        "1822direkt",
        "1822 direkt",
        "heladef1822",      // BIC for 1822direkt
        "50050201"          // BLZ for Frankfurter Sparkasse/1822direkt
    )

    // HIGH: strong indicators
    private val highIdentifiers = listOf(
        "frankfurter sparkasse",
        "1822direct",
        "heaborh"           // BIC contains this
    )

    // MEDIUM: common patterns in 1822direkt statements
    private val mediumIdentifiers = listOf(
        "sepa-basislastschrift",
        "sepa eingang",
        "gaa,spk.netz",
        "buchung",
        "wertstellung",
        "kto. auftraggeber"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return certainIdentifiers.any { lower.contains(it) } ||
               (highIdentifiers.count { lower.contains(it) } >= 1 &&
                mediumIdentifiers.count { lower.contains(it) } >= 2)
    }

    override fun getConfidence(pdfText: String): Pair<DetectionConfidence, List<String>> {
        return calculateConfidence(pdfText, certainIdentifiers, highIdentifiers, mediumIdentifiers)
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extract1822Period(pdfText)

            // Try 1822direkt specific format first
            var transactions = parse1822Format(lines)

            // Fallback to generic parser
            if (transactions.size < 2) {
                val genericTransactions = parseComprehensiveFormat(lines)
                if (genericTransactions.size > transactions.size) {
                    transactions = genericTransactions
                }
            }

            return if (transactions.isNotEmpty()) {
                ParseResult(
                    success = true,
                    bankName = bankName,
                    accountIban = accountIban,
                    statementPeriod = statementPeriod,
                    transactions = transactions
                )
            } else {
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from 1822direkt PDF"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing 1822direkt PDF: ${e.message}"
            )
        }
    }

    /**
     * Extract statement period from 1822direkt header
     */
    private fun extract1822Period(text: String): String? {
        // Look for date range patterns
        val periodPattern = Regex("""(\d{2}\.\d{2}\.\d{4})\s*[-–bis]+\s*(\d{2}\.\d{2}\.\d{4})""")
        val match = periodPattern.find(text)
        return match?.let { "${it.groupValues[1]} - ${it.groupValues[2]}" }
    }

    /**
     * Parse 1822direkt format:
     * Line 1: DD.MM.YYYY  TransactionType  Counterparty  Amount€
     * Line 2: DD.MM.YYYY  IBAN             Description
     */
    private fun parse1822Format(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()

        // Amount pattern: +/-Amount€ or Amount€ (German format)
        // Examples: -30,82€, +9,00€, -1.000,00€
        // Note: PDF may use Unicode minus (−) instead of ASCII hyphen (-)
        val amountPattern = Regex("""([+\-−]?\d{1,3}(?:\.\d{3})*,\d{2})\s*€""")

        // Date pattern at start of line
        val dateLinePattern = Regex("""^(\d{2}\.\d{2}\.\d{4})\s+(.+)$""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Skip empty lines and headers
            if (line.isEmpty() || is1822HeaderLine(line)) {
                i++
                continue
            }

            // Try to match a line starting with date and containing amount
            val dateMatch = dateLinePattern.find(line)
            val amountMatch = amountPattern.find(line)

            if (dateMatch != null && amountMatch != null) {
                val bookingDateStr = dateMatch.groupValues[1]
                val restOfLine = dateMatch.groupValues[2]

                // Parse amount (German format)
                val amountStr = amountMatch.groupValues[1]
                val amount = parseGermanAmountString(amountStr)

                // Parse booking date
                val bookingDate = parseGermanDate(bookingDateStr)

                if (bookingDate != null && amount != null) {
                    // Extract transaction type and counterparty from before amount
                    val beforeAmount = restOfLine.substringBefore(amountMatch.value).trim()
                    val (transactionType, counterparty) = extractTypeAndCounterparty(beforeAmount)

                    // Look for second line with value date and description
                    var valueDate = bookingDate
                    val descriptionParts = mutableListOf<String>()
                    var counterpartyIban: String? = null

                    var j = i + 1
                    while (j < lines.size && j < i + 5) {
                        val nextLine = lines[j].trim()

                        if (nextLine.isEmpty()) {
                            j++
                            continue
                        }

                        // Check if this is a new transaction (has date AND amount)
                        if (dateLinePattern.containsMatchIn(nextLine) && amountPattern.containsMatchIn(nextLine)) {
                            break
                        }

                        // Check for value date line
                        val valueDateMatch = Regex("""^(\d{2}\.\d{2}\.\d{4})\s+(.*)$""").find(nextLine)
                        if (valueDateMatch != null) {
                            valueDate = parseGermanDate(valueDateMatch.groupValues[1]) ?: valueDate
                            val rest = valueDateMatch.groupValues[2].trim()

                            // Check for IBAN in the rest
                            val ibanMatch = Regex("""([A-Z]{2}\d{2}\s*(?:\d{4}\s*){4,6}\d{0,2})""").find(rest)
                            if (ibanMatch != null) {
                                counterpartyIban = ibanMatch.value.replace(" ", "")
                                val afterIban = rest.substringAfter(ibanMatch.value).trim()
                                if (afterIban.isNotEmpty()) {
                                    descriptionParts.add(afterIban)
                                }
                            } else {
                                descriptionParts.add(rest)
                            }
                        } else if (!is1822HeaderLine(nextLine)) {
                            // Additional description line
                            descriptionParts.add(nextLine)
                        }

                        j++
                    }

                    val description = descriptionParts.joinToString(" ").trim()
                    val cleanCounterparty = clean1822Counterparty(counterparty)

                    transactions.add(
                        ParsedTransaction(
                            bookingDate = bookingDate,
                            valueDate = valueDate,
                            amount = amount,
                            currency = "EUR",
                            description = description.ifEmpty { transactionType },
                            counterpartyName = cleanCounterparty.ifEmpty { null },
                            counterpartyIban = counterpartyIban,
                            transactionType = transactionType,
                            rawText = line + "\n" + descriptionParts.joinToString("\n")
                        )
                    )

                    i = j
                    continue
                }
            }

            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.counterpartyName?.take(20)}" }
    }

    /**
     * Parse German amount string (e.g., "-1.000,50" or "30,82")
     */
    private fun parseGermanAmountString(amountStr: String): Double? {
        return try {
            // German format: dot as thousand separator, comma as decimal
            // Handle both Unicode minus (−) and ASCII hyphen (-)
            val normalized = amountStr
                .replace("−", "-")     // Convert Unicode minus to ASCII
                .replace(".", "")      // Remove thousand separators
                .replace(",", ".")     // Convert decimal comma to dot
            normalized.toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract transaction type and counterparty from text
     */
    private fun extractTypeAndCounterparty(text: String): Pair<String, String> {
        // Known transaction types in 1822direkt
        val transactionTypes = listOf(
            "SEPA-Basislastschrift",
            "SEPA Eingang",
            "SEPA-Überweisung",
            "SEPA Überweisung",
            "GAA,Spk.Netz",
            "GAA Spk.Netz",
            "Dauerauftrag",
            "Kartenzahlung",
            "Lastschrift",
            "Gutschrift",
            "Überweisung"
        )

        for (type in transactionTypes) {
            if (text.contains(type, ignoreCase = true)) {
                val parts = text.split(Regex("""\s{2,}"""))
                val typeIndex = parts.indexOfFirst { it.contains(type, ignoreCase = true) }

                return if (typeIndex >= 0 && parts.size > typeIndex + 1) {
                    Pair(type, parts.drop(typeIndex + 1).joinToString(" ").trim())
                } else if (typeIndex >= 0) {
                    val afterType = text.substringAfter(type, "").trim()
                    Pair(type, afterType)
                } else {
                    Pair(type, "")
                }
            }
        }

        // No known type found - try to split by multiple spaces
        val parts = text.split(Regex("""\s{2,}"""))
        return if (parts.size >= 2) {
            Pair(parts[0].trim(), parts.drop(1).joinToString(" ").trim())
        } else {
            Pair("", text)
        }
    }

    /**
     * Check if line is a 1822direkt header line
     */
    private fun is1822HeaderLine(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("buchung") && lower.contains("vorgang") ||
               lower.contains("wertstellung") && lower.contains("kto.") ||
               lower.contains("auftraggeber") && lower.contains("empfänger") ||
               lower.contains("verwendungszweck") ||
               lower.contains("kontostand") ||
               lower.contains("saldo")
    }

    /**
     * Clean 1822direkt counterparty name
     */
    private fun clean1822Counterparty(name: String): String {
        var cleaned = name.trim()

        // Remove IBAN if present
        cleaned = cleaned.replace(Regex("""[A-Z]{2}\d{2}\s*(?:\d{4}\s*)+"""), "").trim()

        // Remove trailing numbers that might be reference numbers
        cleaned = cleaned.replace(Regex("""\s+\d{5,}$"""), "").trim()

        return cleaned
    }
}
