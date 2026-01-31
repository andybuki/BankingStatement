package com.bankwise.app.parser

import kotlinx.datetime.LocalDate

/**
 * CSV Parser that auto-detects column mappings based on header names
 */
class CsvParser {

    // Common header name variations for each field
    private val dateHeaders = listOf(
        "date", "booking_date", "bookingdate", "transaction_date", "transactiondate",
        "datum", "buchungsdatum", "started date", "completed date", "value_date"
    )
    private val amountHeaders = listOf(
        "amount", "betrag", "value", "sum", "amount (signed)", "debit", "credit"
    )
    private val descriptionHeaders = listOf(
        "description", "beschreibung", "verwendungszweck", "reference", "memo",
        "narrative", "details", "text", "usage text"
    )
    private val balanceHeaders = listOf(
        "balance", "saldo", "kontostand", "running_balance", "available"
    )
    private val counterpartyHeaders = listOf(
        "counterparty", "counterparty_name", "payee", "payer", "name", "beneficiary",
        "empfänger", "auftraggeber"
    )
    private val counterpartyIbanHeaders = listOf(
        "counterparty_iban", "iban", "account_number", "kontonummer"
    )
    private val currencyHeaders = listOf(
        "currency", "währung", "ccy"
    )
    private val typeHeaders = listOf(
        "type", "transaction_type", "art", "buchungsart"
    )
    private val remittanceHeaders = listOf(
        "remittance_info", "reference", "referenz", "payment_reference"
    )

    fun parse(csvContent: String, fileName: String): ParseResult {
        try {
            val lines = csvContent.lines().filter { it.isNotBlank() }
            if (lines.isEmpty()) {
                return ParseResult(
                    success = false,
                    bankName = detectBankFromFileName(fileName),
                    errorMessage = "CSV file is empty"
                )
            }

            // Detect delimiter
            val delimiter = detectDelimiter(lines.first())

            // Parse header row
            val headers = parseRow(lines.first(), delimiter).map { it.lowercase().trim() }

            // Map columns to fields
            val columnMapping = mapColumns(headers)

            if (columnMapping.dateIndex == -1) {
                return ParseResult(
                    success = false,
                    bankName = detectBankFromFileName(fileName),
                    errorMessage = "Could not find date column in CSV"
                )
            }

            if (columnMapping.amountIndex == -1) {
                return ParseResult(
                    success = false,
                    bankName = detectBankFromFileName(fileName),
                    errorMessage = "Could not find amount column in CSV"
                )
            }

            // Parse data rows
            val transactions = mutableListOf<ParsedTransaction>()
            for (i in 1 until lines.size) {
                val row = parseRow(lines[i], delimiter)
                if (row.isEmpty() || row.all { it.isBlank() }) continue

                try {
                    val transaction = parseTransaction(row, columnMapping)
                    if (transaction != null) {
                        transactions.add(transaction)
                    }
                } catch (e: Exception) {
                    // Skip malformed rows
                    continue
                }
            }

            val bankName = detectBankFromContent(headers, transactions)
                ?: detectBankFromFileName(fileName)

            return ParseResult(
                success = true,
                bankName = bankName,
                transactions = transactions,
                statementPeriod = detectStatementPeriod(transactions)
            )

        } catch (e: Exception) {
            return ParseResult(
                success = false,
                bankName = detectBankFromFileName(fileName),
                errorMessage = "Failed to parse CSV: ${e.message}"
            )
        }
    }

    private fun detectDelimiter(headerLine: String): Char {
        val delimiters = listOf(',', ';', '\t', '|')
        return delimiters.maxByOrNull { delimiter ->
            headerLine.count { it == delimiter }
        } ?: ','
    }

    private fun parseRow(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    result.add(current.toString().trim().removeSurrounding("\""))
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim().removeSurrounding("\""))
        return result
    }

    private data class ColumnMapping(
        val dateIndex: Int = -1,
        val valueDateIndex: Int = -1,
        val amountIndex: Int = -1,
        val descriptionIndex: Int = -1,
        val balanceIndex: Int = -1,
        val counterpartyIndex: Int = -1,
        val counterpartyIbanIndex: Int = -1,
        val currencyIndex: Int = -1,
        val typeIndex: Int = -1,
        val remittanceIndex: Int = -1
    )

    private fun mapColumns(headers: List<String>): ColumnMapping {
        fun findIndex(variations: List<String>): Int {
            for (variation in variations) {
                val index = headers.indexOfFirst { it.contains(variation) }
                if (index >= 0) return index
            }
            return -1
        }

        return ColumnMapping(
            dateIndex = findIndex(dateHeaders),
            valueDateIndex = headers.indexOfFirst { it.contains("value_date") || it.contains("valuta") },
            amountIndex = findIndex(amountHeaders),
            descriptionIndex = findIndex(descriptionHeaders),
            balanceIndex = findIndex(balanceHeaders),
            counterpartyIndex = findIndex(counterpartyHeaders),
            counterpartyIbanIndex = findIndex(counterpartyIbanHeaders),
            currencyIndex = findIndex(currencyHeaders),
            typeIndex = findIndex(typeHeaders),
            remittanceIndex = findIndex(remittanceHeaders)
        )
    }

    private fun parseTransaction(row: List<String>, mapping: ColumnMapping): ParsedTransaction? {
        val dateStr = row.getOrNull(mapping.dateIndex) ?: return null
        val amountStr = row.getOrNull(mapping.amountIndex) ?: return null

        val bookingDate = parseDate(dateStr) ?: return null
        val amount = parseAmount(amountStr) ?: return null

        return ParsedTransaction(
            bookingDate = bookingDate,
            valueDate = mapping.valueDateIndex.takeIf { it >= 0 }
                ?.let { row.getOrNull(it) }
                ?.let { parseDate(it) },
            amount = amount,
            currency = mapping.currencyIndex.takeIf { it >= 0 }
                ?.let { row.getOrNull(it)?.uppercase() }
                ?: "EUR",
            balance = mapping.balanceIndex.takeIf { it >= 0 }
                ?.let { row.getOrNull(it) }
                ?.let { parseAmount(it) },
            description = row.getOrNull(mapping.descriptionIndex) ?: "",
            counterpartyName = mapping.counterpartyIndex.takeIf { it >= 0 }
                ?.let { row.getOrNull(it) },
            counterpartyIban = mapping.counterpartyIbanIndex.takeIf { it >= 0 }
                ?.let { row.getOrNull(it) },
            remittanceInfo = mapping.remittanceIndex.takeIf { it >= 0 }
                ?.let { row.getOrNull(it) },
            transactionType = mapping.typeIndex.takeIf { it >= 0 }
                ?.let { row.getOrNull(it) },
            rawText = row.joinToString(";")
        )
    }

    private fun parseDate(dateStr: String): LocalDate? {
        val cleanDate = dateStr.trim()
        if (cleanDate.isEmpty()) return null

        // Common date formats
        val patterns = listOf(
            // DD.MM.YYYY or DD.MM.YYYY HH:MM
            Regex("""(\d{2})\.(\d{2})\.(\d{4})"""),
            // YYYY-MM-DD
            Regex("""(\d{4})-(\d{2})-(\d{2})"""),
            // DD/MM/YYYY
            Regex("""(\d{2})/(\d{2})/(\d{4})"""),
            // MM/DD/YYYY
            Regex("""(\d{2})/(\d{2})/(\d{4})""")
        )

        // Try DD.MM.YYYY format (German)
        patterns[0].find(cleanDate)?.let { match ->
            val (day, month, year) = match.destructured
            return try {
                LocalDate(year.toInt(), month.toInt(), day.toInt())
            } catch (e: Exception) { null }
        }

        // Try YYYY-MM-DD format (ISO)
        patterns[1].find(cleanDate)?.let { match ->
            val (year, month, day) = match.destructured
            return try {
                LocalDate(year.toInt(), month.toInt(), day.toInt())
            } catch (e: Exception) { null }
        }

        // Try DD/MM/YYYY format
        patterns[2].find(cleanDate)?.let { match ->
            val (day, month, year) = match.destructured
            return try {
                LocalDate(year.toInt(), month.toInt(), day.toInt())
            } catch (e: Exception) { null }
        }

        return null
    }

    private fun parseAmount(amountStr: String): Double? {
        val cleanAmount = amountStr.trim()
            .replace(" ", "")
            .replace("€", "")
            .replace("$", "")
            .replace("EUR", "")
            .replace("USD", "")
            .trim()

        if (cleanAmount.isEmpty()) return null

        // Handle German number format (1.234,56) vs International (1,234.56)
        return try {
            when {
                // German format: dots for thousands, comma for decimal
                cleanAmount.contains(",") && cleanAmount.lastIndexOf(',') > cleanAmount.lastIndexOf('.') -> {
                    cleanAmount.replace(".", "").replace(",", ".").toDouble()
                }
                // International format: commas for thousands, dot for decimal
                cleanAmount.contains(",") && cleanAmount.lastIndexOf('.') > cleanAmount.lastIndexOf(',') -> {
                    cleanAmount.replace(",", "").toDouble()
                }
                // Only comma, treat as decimal separator
                cleanAmount.contains(",") && !cleanAmount.contains(".") -> {
                    cleanAmount.replace(",", ".").toDouble()
                }
                else -> cleanAmount.toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun detectBankFromFileName(fileName: String): String {
        val name = fileName.lowercase()
        return when {
            name.contains("revolut") -> "Revolut"
            name.contains("ing") -> "ING"
            name.contains("dkb") -> "DKB"
            name.contains("sparkasse") -> "Sparkasse"
            name.contains("deutsche") -> "Deutsche Bank"
            name.contains("commerzbank") -> "Commerzbank"
            name.contains("n26") -> "N26"
            else -> "Unknown Bank"
        }
    }

    private fun detectBankFromContent(headers: List<String>, transactions: List<ParsedTransaction>): String? {
        val headerText = headers.joinToString(" ").lowercase()

        return when {
            headerText.contains("revolut") || headerText.contains("product") && headerText.contains("started date") -> "Revolut"
            headerText.contains("ing") -> "ING"
            else -> null
        }
    }

    private fun detectStatementPeriod(transactions: List<ParsedTransaction>): String? {
        if (transactions.isEmpty()) return null
        val dates = transactions.map { it.bookingDate }.sorted()
        val first = dates.first()
        val last = dates.last()
        return "${first.month.name.take(3)} ${first.year} - ${last.month.name.take(3)} ${last.year}"
    }
}
