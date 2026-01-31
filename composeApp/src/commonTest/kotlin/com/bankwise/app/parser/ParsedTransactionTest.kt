package com.bankwise.app.parser

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ParsedTransaction, ParseResult, and ImportFileType.
 */
class ParsedTransactionTest {

    // ===== ParsedTransaction tests =====

    @Test
    fun testParsedTransaction_RequiredFields() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test payment"
        )

        assertEquals(LocalDate(2024, 1, 15), transaction.bookingDate)
        assertEquals(-50.0, transaction.amount)
        assertEquals("Test payment", transaction.description)
    }

    @Test
    fun testParsedTransaction_DefaultCurrency() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test"
        )

        assertEquals("EUR", transaction.currency)
    }

    @Test
    fun testParsedTransaction_OptionalFieldsAreNull() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test"
        )

        assertNull(transaction.transactionId)
        assertNull(transaction.valueDate)
        assertNull(transaction.balance)
        assertNull(transaction.counterpartyName)
        assertNull(transaction.counterpartyIban)
        assertNull(transaction.remittanceInfo)
        assertNull(transaction.transactionType)
        assertNull(transaction.bankTransactionCode)
        assertNull(transaction.rawText)
    }

    @Test
    fun testParsedTransaction_AllFields() {
        val transaction = ParsedTransaction(
            transactionId = "TX12345",
            bookingDate = LocalDate(2024, 1, 15),
            valueDate = LocalDate(2024, 1, 16),
            amount = -150.0,
            currency = "USD",
            balance = 1000.0,
            description = "Payment to supplier",
            counterpartyName = "ACME Corp",
            counterpartyIban = "DE89370400440532013000",
            remittanceInfo = "Invoice 12345",
            transactionType = "SEPA Credit Transfer",
            bankTransactionCode = "PMNT",
            rawText = "Raw transaction data"
        )

        assertEquals("TX12345", transaction.transactionId)
        assertEquals(LocalDate(2024, 1, 16), transaction.valueDate)
        assertEquals("USD", transaction.currency)
        assertEquals(1000.0, transaction.balance)
        assertEquals("ACME Corp", transaction.counterpartyName)
        assertEquals("DE89370400440532013000", transaction.counterpartyIban)
        assertEquals("Invoice 12345", transaction.remittanceInfo)
        assertEquals("SEPA Credit Transfer", transaction.transactionType)
        assertEquals("PMNT", transaction.bankTransactionCode)
        assertEquals("Raw transaction data", transaction.rawText)
    }

    @Test
    fun testParsedTransaction_DataClassEquality() {
        val transaction1 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test"
        )
        val transaction2 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test"
        )

        assertEquals(transaction1, transaction2)
    }

    // ===== ParseResult tests =====

    @Test
    fun testParseResult_SuccessWithTransactions() {
        val transactions = listOf(
            ParsedTransaction(
                bookingDate = LocalDate(2024, 1, 15),
                amount = -50.0,
                description = "Test"
            )
        )

        val result = ParseResult(
            success = true,
            bankName = "Test Bank",
            transactions = transactions
        )

        assertTrue(result.success)
        assertEquals("Test Bank", result.bankName)
        assertEquals(1, result.transactions.size)
        assertNull(result.errorMessage)
    }

    @Test
    fun testParseResult_FailureWithError() {
        val result = ParseResult(
            success = false,
            bankName = "Unknown Bank",
            errorMessage = "Failed to parse file"
        )

        assertFalse(result.success)
        assertEquals("Failed to parse file", result.errorMessage)
        assertTrue(result.transactions.isEmpty())
    }

    @Test
    fun testParseResult_WithAccountAndPeriod() {
        val result = ParseResult(
            success = true,
            bankName = "Sparkasse",
            accountIban = "DE89370400440532013000",
            statementPeriod = "Jan 2024 - Mar 2024"
        )

        assertEquals("DE89370400440532013000", result.accountIban)
        assertEquals("Jan 2024 - Mar 2024", result.statementPeriod)
    }

    @Test
    fun testParseResult_DefaultEmptyTransactions() {
        val result = ParseResult(
            success = true,
            bankName = "Test Bank"
        )

        assertTrue(result.transactions.isEmpty())
    }

    // ===== ImportFileType tests =====

    @Test
    fun testImportFileType_PDFMimeTypes() {
        val pdfType = ImportFileType.PDF

        assertTrue(pdfType.mimeTypes.contains("application/pdf"))
        assertTrue(pdfType.extensions.contains("pdf"))
    }

    @Test
    fun testImportFileType_CSVMimeTypes() {
        val csvType = ImportFileType.CSV

        assertTrue(csvType.mimeTypes.contains("text/csv"))
        assertTrue(csvType.mimeTypes.contains("text/comma-separated-values"))
        assertTrue(csvType.mimeTypes.contains("application/csv"))
        assertTrue(csvType.extensions.contains("csv"))
    }

    @Test
    fun testImportFileType_ExcelMimeTypes() {
        val excelType = ImportFileType.EXCEL

        assertTrue(excelType.mimeTypes.contains("application/vnd.ms-excel"))
        assertTrue(excelType.mimeTypes.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertTrue(excelType.extensions.contains("xls"))
        assertTrue(excelType.extensions.contains("xlsx"))
    }

    @Test
    fun testImportFileType_FromFileName_PDF() {
        assertEquals(ImportFileType.PDF, ImportFileType.fromFileName("statement.pdf"))
        assertEquals(ImportFileType.PDF, ImportFileType.fromFileName("STATEMENT.PDF"))
        assertEquals(ImportFileType.PDF, ImportFileType.fromFileName("bank/statement.pdf"))
    }

    @Test
    fun testImportFileType_FromFileName_CSV() {
        assertEquals(ImportFileType.CSV, ImportFileType.fromFileName("transactions.csv"))
        assertEquals(ImportFileType.CSV, ImportFileType.fromFileName("EXPORT.CSV"))
        assertEquals(ImportFileType.CSV, ImportFileType.fromFileName("data/export.csv"))
    }

    @Test
    fun testImportFileType_FromFileName_Excel() {
        assertEquals(ImportFileType.EXCEL, ImportFileType.fromFileName("data.xls"))
        assertEquals(ImportFileType.EXCEL, ImportFileType.fromFileName("data.xlsx"))
        assertEquals(ImportFileType.EXCEL, ImportFileType.fromFileName("REPORT.XLSX"))
    }

    @Test
    fun testImportFileType_FromFileName_Unknown() {
        assertNull(ImportFileType.fromFileName("document.txt"))
        assertNull(ImportFileType.fromFileName("image.png"))
        assertNull(ImportFileType.fromFileName("file.doc"))
        assertNull(ImportFileType.fromFileName("noextension"))
    }

    @Test
    fun testImportFileType_FromMimeType_PDF() {
        assertEquals(ImportFileType.PDF, ImportFileType.fromMimeType("application/pdf"))
    }

    @Test
    fun testImportFileType_FromMimeType_CSV() {
        assertEquals(ImportFileType.CSV, ImportFileType.fromMimeType("text/csv"))
        assertEquals(ImportFileType.CSV, ImportFileType.fromMimeType("text/comma-separated-values"))
        assertEquals(ImportFileType.CSV, ImportFileType.fromMimeType("application/csv"))
    }

    @Test
    fun testImportFileType_FromMimeType_Excel() {
        assertEquals(ImportFileType.EXCEL, ImportFileType.fromMimeType("application/vnd.ms-excel"))
        assertEquals(
            ImportFileType.EXCEL,
            ImportFileType.fromMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        )
    }

    @Test
    fun testImportFileType_FromMimeType_Unknown() {
        assertNull(ImportFileType.fromMimeType("text/plain"))
        assertNull(ImportFileType.fromMimeType("image/png"))
        assertNull(ImportFileType.fromMimeType("unknown/type"))
    }

    @Test
    fun testImportFileType_FromMimeType_CaseInsensitive() {
        assertEquals(ImportFileType.PDF, ImportFileType.fromMimeType("APPLICATION/PDF"))
        assertEquals(ImportFileType.CSV, ImportFileType.fromMimeType("TEXT/CSV"))
    }

    @Test
    fun testImportFileType_AllMimeTypes() {
        val allMimeTypes = ImportFileType.allMimeTypes()

        assertTrue(allMimeTypes.contains("application/pdf"))
        assertTrue(allMimeTypes.contains("text/csv"))
        assertTrue(allMimeTypes.contains("application/vnd.ms-excel"))
        assertTrue(allMimeTypes.size >= 6)  // At least PDF(1) + CSV(3) + Excel(2)
    }

    @Test
    fun testImportFileType_EnumEntries() {
        assertEquals(3, ImportFileType.entries.size)
        assertTrue(ImportFileType.entries.contains(ImportFileType.PDF))
        assertTrue(ImportFileType.entries.contains(ImportFileType.CSV))
        assertTrue(ImportFileType.entries.contains(ImportFileType.EXCEL))
    }

    // ===== Edge cases =====

    @Test
    fun testImportFileType_FromFileName_EmptyString() {
        assertNull(ImportFileType.fromFileName(""))
    }

    @Test
    fun testImportFileType_FromFileName_OnlyExtension() {
        assertEquals(ImportFileType.PDF, ImportFileType.fromFileName(".pdf"))
    }

    @Test
    fun testImportFileType_FromFileName_MultipleDotsInName() {
        assertEquals(ImportFileType.CSV, ImportFileType.fromFileName("bank.statement.2024.csv"))
    }
}
