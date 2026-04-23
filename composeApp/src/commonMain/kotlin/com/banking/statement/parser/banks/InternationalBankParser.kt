package com.banking.statement.parser.banks

import com.banking.statement.parser.ParseResult
import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate

/**
 * Base class for international bank parsers with support for various date/amount formats
 */
abstract class InternationalBankParser : BankPdfParser {

    // Transaction type keywords by language
    protected val transactionTypes = mapOf(
        "en" to listOf("Transfer", "Payment", "Deposit", "Withdrawal", "Direct Debit", "Standing Order", "Card Payment", "ATM", "Fee", "Interest", "Salary", "Refund"),
        "es" to listOf("Transferencia", "Pago", "Depósito", "Retiro", "Débito", "Domiciliación", "Tarjeta", "Cajero", "Comisión", "Interés", "Nómina"),
        "fr" to listOf("Virement", "Paiement", "Dépôt", "Retrait", "Prélèvement", "Carte", "DAB", "Frais", "Intérêt", "Salaire"),
        "it" to listOf("Bonifico", "Pagamento", "Versamento", "Prelievo", "Addebito", "Carta", "Bancomat", "Commissione", "Interesse", "Stipendio"),
        "pl" to listOf("Przelew", "Płatność", "Wpłata", "Wypłata", "Polecenie", "Karta", "Bankomat", "Opłata", "Odsetki", "Wynagrodzenie"),
        "pt" to listOf("Transferência", "Pagamento", "Depósito", "Saque", "Débito", "Cartão", "Caixa", "Taxa", "Juros", "Salário")
    )

    protected abstract val currency: String
    protected abstract val dateFormat: DateFormat
    protected abstract val amountFormat: AmountFormat

    enum class DateFormat {
        DD_MM_YYYY,      // European: 31/12/2024 or 31.12.2024 or 31-12-2024
        MM_DD_YYYY,      // US: 12/31/2024
        YYYY_MM_DD,      // ISO: 2024-12-31
        DD_MMM_YYYY,     // UK: 31 Dec 2024
        MMM_DD_YYYY      // US alt: Dec 31, 2024
    }

    enum class AmountFormat {
        COMMA_DECIMAL,   // European: 1.234,56 or 1 234,56
        DOT_DECIMAL,     // US/UK: 1,234.56
        SPACE_DECIMAL    // Some European: 1 234.56
    }

    /**
     * Generic international statement parser
     */
    protected fun parseInternationalStatement(
        pdfText: String,
        fileName: String
    ): ParseResult {
        try {
            val lines = pdfText.lines()
            val accountNumber = extractAccountNumber(pdfText)
            val statementPeriod = extractStatementPeriodIntl(pdfText)

            var transactions = parseTableFormatIntl(lines)

            if (transactions.isEmpty()) {
                transactions = parseDateAmountLinesIntl(lines)
            }

            if (transactions.isEmpty()) {
                transactions = parseBlockFormatIntl(lines)
            }

            return if (transactions.isNotEmpty()) {
                ParseResult(
                    success = true,
                    bankName = bankName,
                    accountIban = accountNumber,
                    statementPeriod = statementPeriod,
                    transactions = transactions
                )
            } else {
                ParseResult(
                    success = false,
                    bankName = bankName,
                    errorMessage = "Could not extract transactions from $bankName PDF. Try CSV/Excel export."
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

    protected fun parseTableFormatIntl(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val datePattern = getDatePattern()
        val amountPattern = getAmountPattern()

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            val dates = datePattern.findAll(line).toList()
            val amounts = amountPattern.findAll(line).toList()

            if (dates.isNotEmpty() && amounts.isNotEmpty()) {
                val bookingDate = parseDate(dates[0].groupValues[1])
                val amount = parseAmount(amounts[0].groupValues[1])

                if (bookingDate != null && amount != null) {
                    var description = line
                    dates.forEach { description = description.replace(it.value, "") }
                    amounts.forEach { description = description.replace(it.value, "") }
                    description = cleanDescription(description)

                    // Collect additional description lines
                    val additionalDesc = mutableListOf<String>()
                    var j = i + 1
                    while (j < lines.size && j < i + 4) {
                        val nextLine = lines[j].trim()
                        if (nextLine.isEmpty()) break
                        if (datePattern.containsMatchIn(nextLine) && amountPattern.containsMatchIn(nextLine)) break
                        if (!isHeaderOrFooterIntl(nextLine)) {
                            additionalDesc.add(nextLine)
                        }
                        j++
                    }

                    val fullDescription = if (additionalDesc.isNotEmpty()) {
                        "$description ${additionalDesc.joinToString(" ")}"
                    } else description

                    if (fullDescription.isNotBlank() && fullDescription.length > 2) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = bookingDate,
                                amount = amount,
                                currency = currency,
                                description = fullDescription.trim(),
                                counterpartyName = extractCounterpartyIntl(fullDescription),
                                transactionType = detectTransactionTypeIntl(fullDescription),
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

    protected fun parseDateAmountLinesIntl(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val datePattern = getDatePattern()
        val amountPattern = getAmountPattern()

        for (line in lines) {
            val trimmed = line.trim()
            val dateMatch = datePattern.find(trimmed)
            val amountMatch = amountPattern.find(trimmed)

            if (dateMatch != null && amountMatch != null) {
                val date = parseDate(dateMatch.groupValues[1])
                val amount = parseAmount(amountMatch.groupValues[1])

                if (date != null && amount != null) {
                    var description = trimmed
                        .replace(dateMatch.value, "")
                        .replace(amountMatch.value, "")
                    description = cleanDescription(description)

                    if (description.isNotBlank() && description.length > 2) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = date,
                                valueDate = date,
                                amount = amount,
                                currency = currency,
                                description = description,
                                counterpartyName = extractCounterpartyIntl(description),
                                transactionType = detectTransactionTypeIntl(description),
                                rawText = trimmed
                            )
                        )
                    }
                }
            }
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    protected fun parseBlockFormatIntl(lines: List<String>): List<ParsedTransaction> {
        val transactions = mutableListOf<ParsedTransaction>()
        val datePattern = getDatePattern()
        val amountPattern = getAmountPattern()

        val blocks = mutableListOf<String>()
        var currentBlock = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                if (currentBlock.isNotEmpty()) {
                    blocks.add(currentBlock.toString())
                    currentBlock = StringBuilder()
                }
            } else if (!isHeaderOrFooterIntl(trimmed)) {
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
                val bookingDate = parseDate(dates[0].groupValues[1])
                val amount = parseAmount(amounts[0].groupValues[1])

                if (bookingDate != null && amount != null) {
                    var description = block
                    dates.forEach { description = description.replace(it.value, " ") }
                    amounts.forEach { description = description.replace(it.value, " ") }
                    description = cleanDescription(description)

                    if (description.length > 3) {
                        transactions.add(
                            ParsedTransaction(
                                bookingDate = bookingDate,
                                valueDate = bookingDate,
                                amount = amount,
                                currency = currency,
                                description = description,
                                counterpartyName = extractCounterpartyIntl(description),
                                transactionType = detectTransactionTypeIntl(description),
                                rawText = block
                            )
                        )
                    }
                }
            }
        }

        return transactions.distinctBy { "${it.bookingDate}_${it.amount}_${it.description.take(20)}" }
    }

    protected fun getDatePattern(): Regex {
        return when (dateFormat) {
            DateFormat.DD_MM_YYYY -> Regex("(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})")
            DateFormat.MM_DD_YYYY -> Regex("(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})")
            DateFormat.YYYY_MM_DD -> Regex("(\\d{4}[/.-]\\d{1,2}[/.-]\\d{1,2})")
            DateFormat.DD_MMM_YYYY -> Regex("(\\d{1,2}\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{2,4})", RegexOption.IGNORE_CASE)
            DateFormat.MMM_DD_YYYY -> Regex("((?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},?\\s+\\d{2,4})", RegexOption.IGNORE_CASE)
        }
    }

    protected fun getAmountPattern(): Regex {
        return when (amountFormat) {
            AmountFormat.COMMA_DECIMAL -> Regex("(-?\\d{1,3}(?:[.\\s]\\d{3})*,\\d{2})")
            AmountFormat.DOT_DECIMAL -> Regex("(-?\\d{1,3}(?:,\\d{3})*\\.\\d{2})")
            AmountFormat.SPACE_DECIMAL -> Regex("(-?\\d{1,3}(?:\\s\\d{3})*[.,]\\d{2})")
        }
    }

    protected fun parseDate(dateStr: String): LocalDate? {
        return try {
            val cleaned = dateStr.trim()
            when (dateFormat) {
                DateFormat.DD_MM_YYYY -> {
                    val parts = cleaned.split(Regex("[/.-]"))
                    if (parts.size == 3) {
                        val year = normalizeYearIntl(parts[2])
                        LocalDate(year, parts[1].toInt(), parts[0].toInt())
                    } else null
                }
                DateFormat.MM_DD_YYYY -> {
                    val parts = cleaned.split(Regex("[/.-]"))
                    if (parts.size == 3) {
                        val year = normalizeYearIntl(parts[2])
                        LocalDate(year, parts[0].toInt(), parts[1].toInt())
                    } else null
                }
                DateFormat.YYYY_MM_DD -> {
                    val parts = cleaned.split(Regex("[/.-]"))
                    if (parts.size == 3) {
                        LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                    } else null
                }
                DateFormat.DD_MMM_YYYY -> {
                    parseMonthNameDate(cleaned, false)
                }
                DateFormat.MMM_DD_YYYY -> {
                    parseMonthNameDate(cleaned, true)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseMonthNameDate(dateStr: String, monthFirst: Boolean): LocalDate? {
        val months = mapOf(
            "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
            "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
        )
        val parts = dateStr.replace(",", "").split(Regex("\\s+"))
        return try {
            if (monthFirst && parts.size >= 3) {
                val month = months[parts[0].take(3).lowercase()] ?: return null
                val day = parts[1].toInt()
                val year = normalizeYearIntl(parts[2])
                LocalDate(year, month, day)
            } else if (!monthFirst && parts.size >= 3) {
                val day = parts[0].toInt()
                val month = months[parts[1].take(3).lowercase()] ?: return null
                val year = normalizeYearIntl(parts[2])
                LocalDate(year, month, day)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    protected fun parseAmount(amountStr: String): Double? {
        return try {
            var cleaned = amountStr
                .replace(Regex("[^0-9,.-]"), "")
                .trim()

            when (amountFormat) {
                AmountFormat.COMMA_DECIMAL -> {
                    cleaned = cleaned.replace(".", "").replace(",", ".")
                }
                AmountFormat.DOT_DECIMAL -> {
                    cleaned = cleaned.replace(",", "")
                }
                AmountFormat.SPACE_DECIMAL -> {
                    cleaned = cleaned.replace(" ", "").replace(",", ".")
                }
            }
            cleaned.toDouble()
        } catch (e: Exception) {
            null
        }
    }

    protected fun normalizeYearIntl(yearStr: String): Int {
        val year = yearStr.toIntOrNull() ?: return 2024
        return if (year < 100) {
            if (year > 50) 1900 + year else 2000 + year
        } else year
    }

    protected fun extractAccountNumber(text: String): String? {
        // IBAN
        val ibanPattern = Regex("([A-Z]{2}\\d{2}[\\s]?(?:[A-Z0-9]{4}[\\s]?){2,7}[A-Z0-9]{1,4})")
        val ibanMatch = ibanPattern.find(text.uppercase())
        if (ibanMatch != null) {
            return ibanMatch.groupValues[1].replace("\\s".toRegex(), "")
        }

        // Generic account number
        val accountPattern = Regex("(?:Account|Cuenta|Compte|Conto|Konto)[:\\s#]*([A-Z0-9-]{8,20})", RegexOption.IGNORE_CASE)
        val accountMatch = accountPattern.find(text)
        return accountMatch?.groupValues?.get(1)
    }

    protected fun extractStatementPeriodIntl(text: String): String? {
        val patterns = listOf(
            Regex("(?:Statement Period|Period|Período|Période)[:\\s]*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})\\s*(?:to|-|–)\\s*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})", RegexOption.IGNORE_CASE),
            Regex("(?:From|Desde|Du)[:\\s]*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})\\s*(?:To|Hasta|Au)[:\\s]*(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4})", RegexOption.IGNORE_CASE),
            Regex("(\\w+\\s+\\d{4})", RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues.drop(1).filter { it.isNotBlank() }.joinToString(" - ")
            }
        }
        return null
    }

    protected fun detectTransactionTypeIntl(description: String): String {
        val lower = description.lowercase()
        for ((_, keywords) in transactionTypes) {
            for (keyword in keywords) {
                if (lower.contains(keyword.lowercase())) {
                    return keyword
                }
            }
        }
        return "Transaction"
    }

    protected fun extractCounterpartyIntl(description: String): String? {
        val words = description.split(Regex("\\s+"))
            .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' || c == '-' } }
            .take(5)

        return if (words.isNotEmpty()) {
            words.joinToString(" ").take(50)
        } else null
    }

    protected fun cleanDescription(description: String): String {
        return description
            .replace(Regex("[€$£¥₹₱₦]+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    protected fun isHeaderOrFooterIntl(line: String): Boolean {
        val lower = line.lowercase()
        return lower.contains("page") && (lower.contains("of") || lower.contains("de")) ||
                lower.contains("statement") && lower.contains("account") ||
                lower.contains("balance") && (lower.contains("opening") || lower.contains("closing")) ||
                lower.contains("date") && lower.contains("description") && lower.contains("amount") ||
                lower.contains("total") && lower.contains("balance") ||
                line.length < 5
    }
}
