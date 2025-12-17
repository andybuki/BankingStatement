package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Base class for German bank parsers with common parsing logic
 */
abstract class GermanBankParser : BankPdfParser {

    // Common German transaction type keywords
    protected val germanTransactionTypes = listOf(
        "Lastschrift", "Gutschrift", "Überweisung", "Dauerauftrag",
        "Gehalt", "Lohn", "Kartenzahlung", "Bargeldauszahlung",
        "Abschluss", "Zinsen", "Entgelt", "Einzahlung", "Auszahlung",
        "SEPA-Lastschrift", "SEPA-Überweisung", "Kontoführung",
        "Geldautomat", "Echtzeitüberweisung", "Abrechnung",
        "Barauszahlung", "Bareinzahlung", "Scheckeinreichung",
        "Wertpapier", "Dividende", "Zinsabschluss"
    )

    /**
     * Generic German bank statement parser
     * Tries multiple strategies to extract transactions
     */
    protected fun parseGermanStatement(
        pdfText: String,
        fileName: String,
        bankIdentifier: String
    ): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountIban = extractIban(pdfText)
            val statementPeriod = extractStatementPeriod(pdfText)

            // Try multiple parsing strategies
            var transactions = parseTableFormat(lines)

            if (transactions.isEmpty()) {
                transactions = parseDateAmountLines(lines)
            }

            if (transactions.isEmpty()) {
                transactions = parseBlockFormat(lines)
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
                val sampleLines = lines.filter { it.isNotBlank() }.take(30).joinToString("\n")
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from $bankName PDF. Sample:\n$sampleLines"
                )
            }
        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = bankName,
                errorMessage = "Error parsing $bankName PDF: ${e.message}"
            )
        }
    }

    /**
     * Strategy 1: Parse table-like format with columns
     * Common format: Date | Date | Description | Amount | Balance
     */
    protected fun parseTableFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val datePattern = Regex("(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
        val amountPattern = Regex("(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*[€]?")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // Look for lines with at least one date and one amount
            val dates = datePattern.findAll(line).toList()
            val amounts = amountPattern.findAll(line).toList()

            if (dates.isNotEmpty() && amounts.isNotEmpty()) {
                val bookingDate = parseGermanDate(normalizeYear(dates[0].groupValues[1]))
                val valueDate = if (dates.size > 1) {
                    parseGermanDate(normalizeYear(dates[1].groupValues[1]))
                } else bookingDate

                // Take the first amount as transaction amount (second might be balance)
                val amount = parseGermanAmount(amounts[0].groupValues[1])

                if (bookingDate != null && amount != null) {
                    // Extract description - text between dates and amount
                    var description = line
                    dates.forEach { description = description.replace(it.value, "") }
                    amounts.forEach { description = description.replace(it.value, "") }
                    description = description.replace(Regex("[€\\s]+"), " ").trim()

                    // Collect additional description from following lines
                    val additionalDesc = mutableListOf<String>()
                    var j = i + 1
                    while (j < lines.size && j < i + 5) {
                        val nextLine = lines[j].trim()
                        if (nextLine.isEmpty()) break
                        if (datePattern.containsMatchIn(nextLine) && amountPattern.containsMatchIn(nextLine)) break
                        if (!isHeaderOrFooter(nextLine)) {
                            additionalDesc.add(nextLine)
                        }
                        j++
                    }

                    val fullDescription = if (additionalDesc.isNotEmpty()) {
                        "$description ${additionalDesc.joinToString(" ")}"
                    } else description

                    if (fullDescription.isNotBlank()) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate ?: bookingDate,
                                amount = amount,
                                currency = "EUR",
                                description = fullDescription.trim(),
                                counterpartyName = extractCounterparty(fullDescription),
                                transactionType = detectTransactionType(fullDescription),
                                rawText = line
                            )
                        )
                    }
                    i = j
                    continue
                }
            }
            i++
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    /**
     * Strategy 2: Parse lines with date at start and amount at end
     */
    protected fun parseDateAmountLines(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val dateStartPattern = Regex("^(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
        val amountEndPattern = Regex("(-?\\d{1,3}(?:[.]\\d{3})*,\\d{2})\\s*[€]?\\s*$")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            val dateMatch = dateStartPattern.find(line)
            val amountMatch = amountEndPattern.find(line)

            if (dateMatch != null && amountMatch != null) {
                val date = parseGermanDate(normalizeYear(dateMatch.groupValues[1]))
                val amount = parseGermanAmount(amountMatch.groupValues[1])

                if (date != null && amount != null) {
                    val description = line
                        .substring(dateMatch.range.last + 1, amountMatch.range.first)
                        .trim()

                    // Check for second date (value date)
                    val valueDateMatch = dateStartPattern.find(description)
                    val cleanDescription = if (valueDateMatch != null) {
                        description.substring(valueDateMatch.range.last + 1).trim()
                    } else description

                    val valueDate = valueDateMatch?.let {
                        parseGermanDate(normalizeYear(it.groupValues[1]))
                    } ?: date

                    // Collect additional description lines
                    val additionalDesc = mutableListOf<String>()
                    var j = i + 1
                    while (j < lines.size && j < i + 5) {
                        val nextLine = lines[j].trim()
                        if (nextLine.isEmpty()) break
                        if (dateStartPattern.containsMatchIn(nextLine)) break
                        if (!isHeaderOrFooter(nextLine)) {
                            additionalDesc.add(nextLine)
                        }
                        j++
                    }

                    val fullDescription = if (additionalDesc.isNotEmpty()) {
                        "$cleanDescription ${additionalDesc.joinToString(" ")}"
                    } else cleanDescription

                    if (fullDescription.isNotBlank()) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = valueDate,
                                amount = amount,
                                currency = "EUR",
                                description = fullDescription.trim(),
                                counterpartyName = extractCounterparty(fullDescription),
                                transactionType = detectTransactionType(fullDescription),
                                rawText = line
                            )
                        )
                    }
                    i = j
                    continue
                }
            }
            i++
        }

        return transactions
    }

    /**
     * Strategy 3: Parse block format where transactions span multiple lines
     */
    protected fun parseBlockFormat(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val datePattern = Regex("(\\d{2}\\.\\d{2}\\.(?:\\d{4}|\\d{2}))")
        val amountPattern = Regex("(-?\\d{1,3}(?:[.,]\\d{3})*[,]\\d{2})\\s*[€]?")

        // Group lines into blocks separated by empty lines
        val blocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentBlock.isNotEmpty()) {
                    blocks.add(currentBlock.toString())
                    currentBlock = StringBuilder()
                }
            } else if (!isHeaderOrFooter(trimmed)) {
                if (currentBlock.isNotEmpty()) currentBlock.append(" ")
                currentBlock.append(trimmed)
            }
        }
        if (currentBlock.isNotEmpty()) {
            blocks.add(currentBlock.toString())
        }

        for (block in blocks) {
            val dates = datePattern.findAll(block).toList()
            val amounts = amountPattern.findAll(block).toList()

            if (dates.isNotEmpty() && amounts.isNotEmpty()) {
                val bookingDate = parseGermanDate(normalizeYear(dates[0].groupValues[1]))
                val valueDate = if (dates.size > 1) {
                    parseGermanDate(normalizeYear(dates[1].groupValues[1]))
                } else bookingDate

                val amount = parseGermanAmount(amounts[0].groupValues[1])

                if (bookingDate != null && amount != null) {
                    var description = block
                    dates.forEach { description = description.replace(it.value, " ") }
                    amounts.forEach { description = description.replace(it.value, " ") }
                    description = description.replace(Regex("\\s+"), " ").trim()

                    if (description.length > 3) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = valueDate ?: bookingDate,
                                amount = amount,
                                currency = "EUR",
                                description = description,
                                counterpartyName = extractCounterparty(description),
                                transactionType = detectTransactionType(description),
                                rawText = block
                            )
                        )
                    }
                }
            }
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    // Helper functions
    protected fun parseGermanDate(dateStr: String): LocalDate? {
        return try {
            val parts = dateStr.split(".")
            if (parts.size == 3) {
                LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }

    protected fun parseGermanAmount(amountStr: String): Double? {
        return try {
            amountStr
                .replace("€", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
                .trim()
                .toDouble()
        } catch (e: Exception) {
            null
        }
    }

    protected fun normalizeYear(dateStr: String): String {
        val parts = dateStr.split(".")
        if (parts.size == 3 && parts[2].length == 2) {
            val year = parts[2].toIntOrNull() ?: return dateStr
            val fullYear = if (year > 50) 1900 + year else 2000 + year
            return "${parts[0]}.${parts[1]}.$fullYear"
        }
        return dateStr
    }

    protected fun extractIban(text: String): String? {
        val ibanPattern = Regex("([A-Z]{2}\\d{2}[\\s]?(?:\\d{4}[\\s]?){4}\\d{2})")
        val match = ibanPattern.find(text.uppercase())
        return match?.groupValues?.get(1)?.replace("\\s".toRegex(), "")
    }

    protected fun extractStatementPeriod(text: String): String? {
        // Various patterns for statement period
        val patterns = listOf(
            Regex("Kontoauszug\\s+(?:Nr\\.?\\s*\\d+\\s+)?(?:vom\\s+)?(\\d{2}\\.\\d{2}\\.\\d{4})(?:\\s*[-–bis]+\\s*(\\d{2}\\.\\d{2}\\.\\d{4}))?", RegexOption.IGNORE_CASE),
            Regex("Auszug\\s+(\\w+\\s+\\d{4})", RegexOption.IGNORE_CASE),
            Regex("Zeitraum[:\\s]+(\\d{2}\\.\\d{2}\\.\\d{4})\\s*[-–bis]+\\s*(\\d{2}\\.\\d{2}\\.\\d{4})", RegexOption.IGNORE_CASE),
            Regex("(\\w+)\\s+(\\d{4})\\s*Kontoauszug", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues.drop(1).filter { it.isNotBlank() }.joinToString(" - ")
            }
        }
        return null
    }

    protected fun detectTransactionType(description: String): String {
        val lower = description.lowercase()
        for (type in germanTransactionTypes) {
            if (lower.contains(type.lowercase())) {
                return type
            }
        }
        return "Buchung"
    }

    protected fun extractCounterparty(description: String): String? {
        // Try to extract counterparty from description
        val words = description.split(Regex("\\s+"))
            .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' } }
            .take(5)

        return if (words.isNotEmpty()) {
            words.joinToString(" ").take(50)
        } else null
    }

    protected fun isHeaderOrFooter(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("seite") && lower.contains("von") ||
               lower.contains("kontoauszug") && lower.contains("nr") ||
               lower.contains("iban") && lower.contains("bic") ||
               lower.contains("blz") ||
               lower.contains("datum") && lower.contains("betrag") && lower.contains("saldo") ||
               lower.contains("alter saldo") ||
               lower.contains("neuer saldo") ||
               lower.contains("übertrag") ||
               line.length < 5
    }
}

// ============================================================
// Deutsche Bank Parser
// ============================================================
class DeutscheBankParser : GermanBankParser() {
    override val bankName = "Deutsche Bank"

    private val identifiers = listOf(
        "deutsche bank",
        "deutdedb",
        "deutdeff",
        "deutsche-bank.de"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Deutsche Bank")
    }
}

// ============================================================
// Postbank Parser
// ============================================================
class PostbankParser : GermanBankParser() {
    override val bankName = "Postbank"

    private val identifiers = listOf(
        "postbank",
        "pbnkdeff",
        "postbank.de",
        "eine niederlassung der deutsche bank"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Postbank")
    }
}

// ============================================================
// Commerzbank Parser
// ============================================================
class CommerzbankParser : GermanBankParser() {
    override val bankName = "Commerzbank"

    private val identifiers = listOf(
        "commerzbank",
        "cobadeff",
        "dresdner bank",
        "commerzbank.de"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Commerzbank")
    }
}

// ============================================================
// Volksbank Parser
// ============================================================
class VolksbankParser : GermanBankParser() {
    override val bankName = "Volksbank"

    private val identifiers = listOf(
        "volksbank",
        "vr-bank",
        "vr bank",
        "raiffeisenbank",
        "genossenschaftsbank",
        "genoded"  // BIC prefix for Genobanks
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
    }

    override fun parse(pdfText: String, fileName: String): ParseResult {
        return parseGermanStatement(pdfText, fileName, "Volksbank")
    }
}

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
        "cortal consors"
    )

    override fun canParse(pdfText: String): Boolean {
        val lower = pdfText.lowercase()
        return identifiers.any { lower.contains(it) }
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
