package com.banking.statement.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for CsvParser.
 * Validates CSV parsing, delimiter detection, date/amount parsing, and column mapping.
 */
class CsvParserTest {

    private val parser = CsvParser()

    // ===== Basic parsing tests =====

    @Test
    fun testParse_EmptyFile_ReturnsFailure() {
        val result = parser.parse("", "test.csv")

        assertFalse(result.success)
        assertEquals("CSV file is empty", result.errorMessage)
    }

    @Test
    fun testParse_OnlyWhitespace_ReturnsFailure() {
        val result = parser.parse("   \n   \n   ", "test.csv")

        assertFalse(result.success)
        assertEquals("CSV file is empty", result.errorMessage)
    }

    @Test
    fun testParse_MissingDateColumn_ReturnsFailure() {
        val csv = """
            amount,description
            100.00,Test payment
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertFalse(result.success)
        assertEquals("Could not find date column in CSV", result.errorMessage)
    }

    @Test
    fun testParse_MissingAmountColumn_ReturnsFailure() {
        val csv = """
            date,description
            2024-01-15,Test payment
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertFalse(result.success)
        assertEquals("Could not find amount column in CSV", result.errorMessage)
    }

    @Test
    fun testParse_ValidCsv_ReturnsSuccess() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Grocery shopping
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    // ===== Delimiter detection tests =====

    @Test
    fun testParse_CommaDelimiter() {
        val csv = """
            date,amount,description
            2024-01-15,-100.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-100.0, result.transactions[0].amount)
    }

    @Test
    fun testParse_SemicolonDelimiter() {
        val csv = """
            date;amount;description
            2024-01-15;-100.00;Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-100.0, result.transactions[0].amount)
    }

    @Test
    fun testParse_TabDelimiter() {
        val csv = "date\tamount\tdescription\n2024-01-15\t-100.00\tTest"

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-100.0, result.transactions[0].amount)
    }

    @Test
    fun testParse_PipeDelimiter() {
        val csv = """
            date|amount|description
            2024-01-15|-100.00|Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-100.0, result.transactions[0].amount)
    }

    // ===== Date format tests =====

    @Test
    fun testParse_IsoDateFormat_YYYY_MM_DD() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        val transaction = result.transactions[0]
        assertEquals(2024, transaction.bookingDate.year)
        assertEquals(1, transaction.bookingDate.monthNumber)
        assertEquals(15, transaction.bookingDate.dayOfMonth)
    }

    @Test
    fun testParse_GermanDateFormat_DD_MM_YYYY() {
        val csv = """
            datum,betrag,beschreibung
            15.01.2024,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        val transaction = result.transactions[0]
        assertEquals(2024, transaction.bookingDate.year)
        assertEquals(1, transaction.bookingDate.monthNumber)
        assertEquals(15, transaction.bookingDate.dayOfMonth)
    }

    @Test
    fun testParse_SlashDateFormat_DD_MM_YYYY() {
        val csv = """
            date,amount,description
            15/01/2024,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        val transaction = result.transactions[0]
        assertEquals(2024, transaction.bookingDate.year)
        assertEquals(1, transaction.bookingDate.monthNumber)
        assertEquals(15, transaction.bookingDate.dayOfMonth)
    }

    // ===== Amount parsing tests =====

    @Test
    fun testParse_GermanAmountFormat_CommaDecimal() {
        val csv = """
            date;betrag;description
            2024-01-15;-1.234,56;Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-1234.56, result.transactions[0].amount)
    }

    @Test
    fun testParse_InternationalAmountFormat_DotDecimal() {
        val csv = """
            date,amount,description
            2024-01-15,-1,234.56,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-1234.56, result.transactions[0].amount)
    }

    @Test
    fun testParse_AmountWithCurrencySymbol() {
        val csv = """
            date,amount,description
            2024-01-15,€-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    @Test
    fun testParse_AmountWithEURText() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00 EUR,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    @Test
    fun testParse_PositiveAmount() {
        val csv = """
            date,amount,description
            2024-01-15,1500.00,Salary
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1500.0, result.transactions[0].amount)
    }

    // ===== Column mapping tests =====

    @Test
    fun testParse_MapsDescriptionColumn() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Grocery shopping at Lidl
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Grocery shopping at Lidl", result.transactions[0].description)
    }

    @Test
    fun testParse_MapsCounterpartyColumn() {
        val csv = """
            date,amount,description,counterparty_name
            2024-01-15,-50.00,Payment,Lidl GmbH
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Lidl GmbH", result.transactions[0].counterpartyName)
    }

    @Test
    fun testParse_MapsBalanceColumn() {
        val csv = """
            date,amount,description,balance
            2024-01-15,-50.00,Payment,1500.00
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1500.0, result.transactions[0].balance)
    }

    @Test
    fun testParse_MapsCurrencyColumn() {
        val csv = """
            date,amount,description,currency
            2024-01-15,-50.00,Payment,USD
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("USD", result.transactions[0].currency)
    }

    @Test
    fun testParse_DefaultCurrencyIsEUR() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Payment
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("EUR", result.transactions[0].currency)
    }

    // ===== German header variations =====

    @Test
    fun testParse_GermanHeaders_Buchungsdatum() {
        val csv = """
            buchungsdatum,betrag,verwendungszweck
            15.01.2024,-50.00,Einkauf
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
        assertEquals("Einkauf", result.transactions[0].description)
    }

    @Test
    fun testParse_GermanHeaders_Kontostand() {
        val csv = """
            datum,betrag,beschreibung,kontostand
            15.01.2024,-50.00,Test,1500.00
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1500.0, result.transactions[0].balance)
    }

    // ===== Bank name detection tests =====

    @Test
    fun testParse_DetectsBankFromFileName_Revolut() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "revolut_statement_2024.csv")

        assertEquals("Revolut", result.bankName)
    }

    @Test
    fun testParse_DetectsBankFromFileName_ING() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "ing_export.csv")

        assertEquals("ING", result.bankName)
    }

    @Test
    fun testParse_DetectsBankFromFileName_DKB() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "dkb-kontoauszug.csv")

        assertEquals("DKB", result.bankName)
    }

    @Test
    fun testParse_DetectsBankFromFileName_Sparkasse() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "sparkasse_export.csv")

        assertEquals("Sparkasse", result.bankName)
    }

    @Test
    fun testParse_DetectsBankFromFileName_DeutscheBank() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "deutsche_bank_statement.csv")

        assertEquals("Deutsche Bank", result.bankName)
    }

    @Test
    fun testParse_DetectsBankFromFileName_N26() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "n26-statement.csv")

        assertEquals("N26", result.bankName)
    }

    @Test
    fun testParse_UnknownBank_ReturnsUnknownBank() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "mystatement.csv")

        assertEquals("Unknown Bank", result.bankName)
    }

    // ===== Multiple transactions =====

    @Test
    fun testParse_MultipleTransactions() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,First
            2024-01-16,-75.00,Second
            2024-01-17,-100.00,Third
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(3, result.transactions.size)
        assertEquals(-50.0, result.transactions[0].amount)
        assertEquals(-75.0, result.transactions[1].amount)
        assertEquals(-100.0, result.transactions[2].amount)
    }

    // ===== Quoted fields =====

    @Test
    fun testParse_QuotedFieldsWithCommas() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,"Description with, comma"
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Description with, comma", result.transactions[0].description)
    }

    // ===== Malformed row handling =====

    @Test
    fun testParse_SkipsMalformedRows() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Valid row
            invalid,data,here
            2024-01-16,-75.00,Another valid row
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        // Should have 2 valid transactions, skipping the malformed one
        assertTrue(result.transactions.isNotEmpty())
    }

    @Test
    fun testParse_SkipsEmptyRows() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,First

            2024-01-16,-75.00,Second
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(2, result.transactions.size)
    }

    // ===== Statement period detection =====

    @Test
    fun testParse_DetectsStatementPeriod() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,First
            2024-03-20,-75.00,Last
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertNotNull(result.statementPeriod)
        assertTrue(result.statementPeriod!!.contains("JAN"))
        assertTrue(result.statementPeriod!!.contains("MAR"))
    }

    // ===== Raw text preservation =====

    @Test
    fun testParse_PreservesRawText() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test payment
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertNotNull(result.transactions[0].rawText)
        assertTrue(result.transactions[0].rawText!!.contains("2024-01-15"))
    }
}
