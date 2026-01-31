package com.banking.statement.parser

import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream

/**
 * Excel Parser for .xls and .xlsx files (Android only, uses Apache POI)
 */
class ExcelParser {

    // Common header name variations
    private val dateHeaders = listOf(
        "date", "booking_date", "bookingdate", "transaction_date", "transactiondate",
        "datum", "buchungsdatum", "started date", "completed date", "value_date"
    )
    private val amountHeaders = listOf(
        "amount", "betrag", "value", "sum", "amount (signed)", "debit", "credit"
    )
    private val descriptionHeaders = listOf(
        "description", "beschreibung", "verwendungszweck", "reference", "memo",
        "narrative", "details", "text"
    )
    private val balanceHeaders = listOf(
        "balance", "saldo", "kontostand", "running_balance"
    )
    private val counterpartyHeaders = listOf(
        "counterparty", "counterparty_name", "payee", "payer", "name", "beneficiary"
    )
    private val counterpartyIbanHeaders = listOf(
        "counterparty_iban", "iban", "account_number"
    )
    private val currencyHeaders = listOf(
        "currency", "währung", "ccy"
    )
    private val typeHeaders = listOf(
        "type", "transaction_type", "art"
    )

    fun parse(excelBytes: ByteArray, fileName: String): ParseResult {
        try {
            val inputStream = ByteArrayInputStream(excelBytes)
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)

            if (sheet.physicalNumberOfRows == 0) {
                workbook.close()
                return ParseResult(
                    success = false,
                    bankName = detectBankFromFileName(fileName),
                    errorMessage = "Excel file is empty"
                )
            }

            // Get header row
            val headerRow = sheet.getRow(0)
            val headers = mutableListOf<String>()
            for (cell in headerRow) {
                headers.add(getCellValue(cell).lowercase().trim())
            }

            // Map columns
            val columnMapping = mapColumns(headers)

            if (columnMapping.dateIndex == -1) {
                workbook.close()
                return ParseResult(
                    success = false,
                    bankName = detectBankFromFileName(fileName),
                    errorMessage = "Could not find date column in Excel"
                )
            }

            if (columnMapping.amountIndex == -1) {
                workbook.close()
                return ParseResult(
                    success = false,
                    bankName = detectBankFromFileName(fileName),
                    errorMessage = "Could not find amount column in Excel"
                )
            }

            // Parse data rows
            val transactions = mutableListOf<ParsedTransaction>()
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                try {
                    val transaction = parseTransaction(row, columnMapping)
                    if (transaction != null) {
                        transactions.add(transaction)
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            workbook.close()

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
                errorMessage = "Failed to parse Excel: ${e.message}"
            )
        }
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
        val typeIndex: Int = -1
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
            typeIndex = findIndex(typeHeaders)
        )
    }

    private fun parseTransaction(row: Row, mapping: ColumnMapping): ParsedTransaction? {
        val dateCell = row.getCell(mapping.dateIndex) ?: return null
        val amountCell = row.getCell(mapping.amountIndex) ?: return null

        val bookingDate = parseDateCell(dateCell) ?: return null
        val amount = parseAmountCell(amountCell) ?: return null

        return ParsedTransaction(
            bookingDate = bookingDate,
            valueDate = mapping.valueDateIndex.takeIf { it >= 0 }
                ?.let { row.getCell(it) }
                ?.let { parseDateCell(it) },
            amount = amount,
            currency = mapping.currencyIndex.takeIf { it >= 0 }
                ?.let { row.getCell(it) }
                ?.let { getCellValue(it).uppercase() }
                ?.takeIf { it.isNotEmpty() }
                ?: "EUR",
            balance = mapping.balanceIndex.takeIf { it >= 0 }
                ?.let { row.getCell(it) }
                ?.let { parseAmountCell(it) },
            description = mapping.descriptionIndex.takeIf { it >= 0 }
                ?.let { row.getCell(it) }
                ?.let { getCellValue(it) }
                ?: "",
            counterpartyName = mapping.counterpartyIndex.takeIf { it >= 0 }
                ?.let { row.getCell(it) }
                ?.let { getCellValue(it) },
            counterpartyIban = mapping.counterpartyIbanIndex.takeIf { it >= 0 }
                ?.let { row.getCell(it) }
                ?.let { getCellValue(it) },
            transactionType = mapping.typeIndex.takeIf { it >= 0 }
                ?.let { row.getCell(it) }
                ?.let { getCellValue(it) },
            rawText = rowToString(row)
        )
    }

    private fun getCellValue(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.localDateTimeCellValue.toLocalDate()
                    "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthValue.toString().padStart(2, '0')}.${date.year}"
                } else {
                    cell.numericCellValue.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    try {
                        cell.numericCellValue.toString()
                    } catch (e2: Exception) {
                        ""
                    }
                }
            }
            else -> ""
        }
    }

    private fun parseDateCell(cell: Cell): LocalDate? {
        return when (cell.cellType) {
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    val date = cell.localDateTimeCellValue.toLocalDate()
                    LocalDate(date.year, date.monthValue, date.dayOfMonth)
                } else {
                    null
                }
            }
            CellType.STRING -> parseDate(cell.stringCellValue)
            else -> null
        }
    }

    private fun parseDate(dateStr: String): LocalDate? {
        val cleanDate = dateStr.trim()
        if (cleanDate.isEmpty()) return null

        // Try DD.MM.YYYY format (German)
        Regex("""(\d{2})\.(\d{2})\.(\d{4})""").find(cleanDate)?.let { match ->
            val (day, month, year) = match.destructured
            return try {
                LocalDate(year.toInt(), month.toInt(), day.toInt())
            } catch (e: Exception) { null }
        }

        // Try YYYY-MM-DD format (ISO)
        Regex("""(\d{4})-(\d{2})-(\d{2})""").find(cleanDate)?.let { match ->
            val (year, month, day) = match.destructured
            return try {
                LocalDate(year.toInt(), month.toInt(), day.toInt())
            } catch (e: Exception) { null }
        }

        return null
    }

    private fun parseAmountCell(cell: Cell): Double? {
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue
            CellType.STRING -> parseAmount(cell.stringCellValue)
            CellType.FORMULA -> {
                try {
                    cell.numericCellValue
                } catch (e: Exception) {
                    parseAmount(cell.stringCellValue)
                }
            }
            else -> null
        }
    }

    private fun parseAmount(amountStr: String): Double? {
        val cleanAmount = amountStr.trim()
            .replace(" ", "")
            .replace("€", "")
            .replace("EUR", "")
            .trim()

        if (cleanAmount.isEmpty()) return null

        return try {
            when {
                cleanAmount.contains(",") && cleanAmount.lastIndexOf(',') > cleanAmount.lastIndexOf('.') -> {
                    cleanAmount.replace(".", "").replace(",", ".").toDouble()
                }
                cleanAmount.contains(",") && !cleanAmount.contains(".") -> {
                    cleanAmount.replace(",", ".").toDouble()
                }
                else -> cleanAmount.replace(",", "").toDouble()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun rowToString(row: Row): String {
        val values = mutableListOf<String>()
        for (cell in row) {
            values.add(getCellValue(cell))
        }
        return values.joinToString(";")
    }

    private fun detectBankFromFileName(fileName: String): String {
        val name = fileName.lowercase()
        return when {
            name.contains("revolut") -> "Revolut"
            name.contains("ing") -> "ING"
            name.contains("dkb") -> "DKB"
            name.contains("sparkasse") -> "Sparkasse"
            else -> "Unknown Bank"
        }
    }

    private fun detectBankFromContent(headers: List<String>, transactions: List<ParsedTransaction>): String? {
        val headerText = headers.joinToString(" ").lowercase()
        return when {
            headerText.contains("revolut") || (headerText.contains("product") && headerText.contains("started date")) -> "Revolut"
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
