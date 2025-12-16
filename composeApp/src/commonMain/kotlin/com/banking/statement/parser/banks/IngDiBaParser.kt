package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Parser for ING DiBa (Germany) bank statements
 *
 * Format characteristics:
 * - Header: ING-DiBa AG, Frankfurt am Main
 * - Account info: IBAN, BIC, Saldo
 * - Transaction format:
 *   DD.MM.YYYY  Lastschrift/Gutschrift CompanyName  -Amount
 *   DD.MM.YYYY  Additional details...
 *   Mandat: XXXXX
 *   Referenz: XXXXX
 */
class IngDiBaParser : BankPdfParser {

    override val bankName = "ING DiBa"

    // Keywords that identify ING DiBa statements
    private val identifiers = listOf(
        "ing-diba",
        "ing diba",
        "ingddeffxxx",  // BIC
        "de29 5001 0517", // Common ING IBAN prefix pattern
        "girokonto nummer"
    )

    // Transaction type keywords
    private val transactionTypes = listOf(
        "Lastschrift", "Gutschrift", "Überweisung", "Dauerauftrag",
        "Gehalt", "Lohn", "Kartenzahlung", "Bargeldauszahlung",
        "Abschluss", "Zinsen", "Entgelt"
    )

    override fun canParse(pdfText: String): Boolean {
        val lowerText = pdfText.lowercase()
        return identifiers.any { lowerText.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        try {
            val transactions = mutableListOf<ParsedTransaction>()
            val lines = pdfText.lines()

            // Extract account info
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Parse transactions
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()

                // Look for transaction start: date followed by transaction type
                val transactionMatch = findTransactionStart(line)
                if (transactionMatch != null) {
                    val (bookingDate, type, initialDesc, amount) = transactionMatch

                    // Collect additional description lines
                    val descriptionParts = mutableListOf<String>()
                    if (initialDesc.isNotBlank()) {
                        descriptionParts.add(initialDesc)
                    }

                    var valueDate: LocalDate? = null
                    var counterparty: String? = null
                    var mandate: String? = null
                    var reference: String? = null

                    // Look at following lines for more details
                    var j = i + 1
                    while (j < lines.size) {
                        val nextLine = lines[j].trim()

                        // Stop if we hit a new transaction or empty section
                        if (nextLine.isEmpty() && j > i + 1) {
                            // Check if next non-empty line is a new transaction
                            val lookAhead = lines.drop(j + 1).firstOrNull { it.isNotBlank() }
                            if (lookAhead != null && findTransactionStart(lookAhead.trim()) != null) {
                                break
                            }
                        }

                        // Check if this is the value date line (second date)
                        val valueDateMatch = extractDateFromLine(nextLine)
                        if (valueDateMatch != null && valueDate == null) {
                            valueDate = valueDateMatch
                            // Rest of line after date is additional description
                            val afterDate = nextLine.substringAfter(
                                Regex("\\d{2}\\.\\d{2}\\.\\d{4}").find(nextLine)?.value ?: ""
                            ).trim()
                            if (afterDate.isNotBlank()) {
                                descriptionParts.add(afterDate)
                            }
                            j++
                            continue
                        }

                        // Check for mandate
                        if (nextLine.startsWith("Mandat:", ignoreCase = true)) {
                            mandate = nextLine.substringAfter(":").trim()
                            j++
                            continue
                        }

                        // Check for reference
                        if (nextLine.startsWith("Referenz:", ignoreCase = true)) {
                            reference = nextLine.substringAfter(":").trim()
                            j++
                            continue
                        }

                        // Check if this line starts a new transaction
                        if (findTransactionStart(nextLine) != null) {
                            break
                        }

                        // Check for section headers to skip
                        if (nextLine.contains("Buchung") && nextLine.contains("Verwendungszweck")) {
                            j++
                            continue
                        }

                        // Add to description if not empty
                        if (nextLine.isNotBlank() && !isPageFooter(nextLine)) {
                            descriptionParts.add(nextLine)
                        }

                        j++

                        // Safety: don't look too far ahead
                        if (j > i + 15) break
                    }

                    // Build full description
                    val fullDescription = buildDescription(type, descriptionParts)

                    // Extract counterparty from description
                    counterparty = extractCounterparty(type, descriptionParts.firstOrNull() ?: "")

                    if (amount != null && bookingDate != null) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate ?: bookingDate,
                                amount = amount,
                                currency = "EUR",
                                description = fullDescription,
                                counterpartyName = counterparty,
                                transactionType = type,
                                remittanceInfo = mandate?.let { "Mandat: $it" }
                                    ?: reference?.let { "Referenz: $it" },
                                rawText = lines.subList(i, minOf(j, lines.size)).joinToString("\n")
                            )
                        )
                    }

                    i = j
                } else {
                    i++
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
                    errorMessage = "Could not extract transactions from ING DiBa PDF"
                )
            }

        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing ING DiBa PDF: ${e.message}"
            )
        }
    }

    private data class TransactionStart(
        val date: LocalDate?,
        val type: String,
        val description: String,
        val amount: Double?
    )

    private fun findTransactionStart(line: String): TransactionStart? {
        // Pattern: DD.MM.YYYY TransactionType Description Amount
        val datePattern = Regex("^(\\d{2}\\.\\d{2}\\.\\d{4})")
        val dateMatch = datePattern.find(line) ?: return null

        val afterDate = line.substring(dateMatch.range.last + 1).trim()
        if (afterDate.isEmpty()) return null

        // Find transaction type
        val type = transactionTypes.find { afterDate.startsWith(it, ignoreCase = true) }
            ?: return null

        val afterType = afterDate.substring(type.length).trim()

        // Try to extract amount from end of line
        val amountPattern = Regex("(-?[\\d.]+,\\d{2})\\s*$")
        val amountMatch = amountPattern.find(afterType)

        val amount = amountMatch?.groupValues?.get(1)?.let { parseGermanAmount(it) }
        val description = if (amountMatch != null) {
            afterType.substring(0, amountMatch.range.first).trim()
        } else {
            afterType
        }

        return TransactionStart(
            date = parseGermanDate(dateMatch.groupValues[1]),
            type = type,
            description = description,
            amount = amount
        )
    }

    private fun extractDateFromLine(line: String): LocalDate? {
        val datePattern = Regex("^(\\d{2}\\.\\d{2}\\.\\d{4})")
        val match = datePattern.find(line) ?: return null
        return parseGermanDate(match.groupValues[1])
    }

    private fun parseGermanDate(dateStr: String): LocalDate? {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGermanAmount(amountStr: String): Double? {
        return try {
            amountStr.replace(".", "").replace(",", ".").toDouble()
        } catch (e: Exception) {
            null
        }
    }

    private fun extractIban(text: String): String? {
        val ibanPattern = Regex("IBAN\\s*:?\\s*([A-Z]{2}\\d{2}[\\s\\d]+)")
        val match = ibanPattern.find(text.uppercase())
        return match?.groupValues?.get(1)?.replace("\\s".toRegex(), "")
    }

    private fun extractStatementPeriod(text: String): String? {
        // Look for "Kontoauszug März 2025" or similar
        val periodPattern = Regex("Kontoauszug\\s+(\\w+\\s+\\d{4})", RegexOption.IGNORE_CASE)
        val match = periodPattern.find(text)
        return match?.groupValues?.get(1)
    }

    private fun buildDescription(type: String, parts: List<String>): String {
        val relevantParts = parts.filter { part ->
            !part.startsWith("Mandat:", ignoreCase = true) &&
            !part.startsWith("Referenz:", ignoreCase = true) &&
            !isPageFooter(part)
        }
        return if (relevantParts.isNotEmpty()) {
            "$type: ${relevantParts.joinToString(" ")}"
        } else {
            type
        }
    }

    private fun extractCounterparty(type: String, firstDescPart: String): String? {
        // For Lastschrift/Gutschrift, the first part usually contains the company name
        return firstDescPart.takeIf { it.isNotBlank() }?.let {
            // Clean up common suffixes
            it.replace(Regex("\\s+(GmbH|AG|e\\.V\\.|KG|OHG).*", RegexOption.IGNORE_CASE), " $1")
                .trim()
                .takeIf { name -> name.length > 2 }
        }
    }

    private fun isPageFooter(line: String): Boolean {
        return line.contains("Seite") && line.contains("von") ||
               line.contains("ING-DiBa AG") ||
               line.contains("www.ing.de") ||
               line.contains("Theodor-Heuss-Allee")
    }
}
