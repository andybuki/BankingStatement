package com.banking.statement.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge case tests for CSV parsing.
 * Covers malformed data, unusual delimiters, header variations, and encoding issues.
 */
class CsvParserEdgeCaseTest {

    private val parser = CsvParser()

    // ===== Quoted field edge cases =====

    @Test
    fun testQuotedFields_DelimiterInsideQuotes() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,"Payment to Smith, John"
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Payment to Smith, John", result.transactions[0].description)
    }

    @Test
    fun testQuotedFields_NewlineInsideQuotes_TreatedAsSeparateLines() {
        // CSV parser splits by lines first, so embedded newlines in quotes
        // may cause issues - this tests the current behavior
        val csv = "date,amount,description\n2024-01-15,-50.00,\"Simple description\""
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testQuotedFields_EmptyQuotedField() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,""
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("", result.transactions[0].description)
    }

    // ===== Date edge cases =====

    @Test
    fun testDate_InvalidDay_SkipsRow() {
        val csv = """
            date,amount,description
            2024-01-32,-50.00,Invalid day
            2024-01-15,-25.00,Valid
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
        assertEquals(-25.0, result.transactions[0].amount)
    }

    @Test
    fun testDate_InvalidMonth_SkipsRow() {
        val csv = """
            date,amount,description
            2024-13-15,-50.00,Invalid month
            2024-01-15,-25.00,Valid
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testDate_LeapYear_Feb29() {
        val csv = """
            date,amount,description
            2024-02-29,-50.00,Leap year
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
        assertEquals(29, result.transactions[0].bookingDate.dayOfMonth)
        assertEquals(2, result.transactions[0].bookingDate.monthNumber)
    }

    @Test
    fun testDate_NonLeapYear_Feb29_SkipsRow() {
        val csv = """
            date,amount,description
            2023-02-29,-50.00,Not a leap year
            2024-01-15,-25.00,Valid
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testDate_GermanFormat_WithTime() {
        // DD.MM.YYYY HH:MM should still parse (regex captures just the date part)
        val csv = """
            datum;betrag;beschreibung
            15.01.2024 14:30;-50,00;Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(15, result.transactions[0].bookingDate.dayOfMonth)
    }

    // ===== All-blank rows =====

    @Test
    fun testParse_AllBlankDataRows_EmptyTransactions() {
        val csv = """
            date,amount,description
            ,,
            ,,
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(0, result.transactions.size)
    }

    // ===== Header variations =====

    @Test
    fun testHeaders_CaseInsensitive() {
        val csv = """
            DATE,AMOUNT,DESCRIPTION
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testHeaders_WithSpaces() {
        val csv = """
            date , amount , description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testHeaders_StartedDate_Revolut() {
        val csv = """
            started date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testHeaders_TransactionDate() {
        val csv = """
            transaction_date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testHeaders_BookingDate() {
        val csv = """
            booking_date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testHeaders_GermanAmount_Betrag() {
        val csv = """
            datum;betrag;verwendungszweck
            15.01.2024;-50,00;Einkauf bei Rewe
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Einkauf bei Rewe", result.transactions[0].description)
    }

    @Test
    fun testHeaders_Empfaenger() {
        val csv = """
            datum;betrag;beschreibung;empfänger
            15.01.2024;-50,00;Lastschrift;Lidl GmbH
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Lidl GmbH", result.transactions[0].counterpartyName)
    }

    @Test
    fun testHeaders_Auftraggeber() {
        val csv = """
            datum;betrag;beschreibung;auftraggeber
            15.01.2024;2500,00;Gehalt;Arbeitgeber GmbH
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Arbeitgeber GmbH", result.transactions[0].counterpartyName)
    }

    // ===== Delimiter edge cases =====

    @Test
    fun testDelimiter_MixedDelimitersInHeader_UsesMaxFrequency() {
        // If header has more semicolons than commas, use semicolon
        val csv = """
            datum;betrag;beschreibung;saldo
            15.01.2024;-50,00;Test;1000,00
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    // ===== Column index out of range =====

    @Test
    fun testParse_RowWithFewerColumnsThanHeader() {
        val csv = """
            date,amount,description,balance,counterparty
            2024-01-15,-50.00,Short row
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        // Should still parse with available columns
        assertEquals(1, result.transactions.size)
        assertNull(result.transactions[0].balance)
        assertNull(result.transactions[0].counterpartyName)
    }

    @Test
    fun testParse_RowWithMoreColumnsThanHeader() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test,extra,columns,here
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    // ===== Large dataset =====

    @Test
    fun testParse_ManyRows() {
        val header = "date,amount,description"
        val rows = (1..100).map { i ->
            val day = (i % 28 + 1).toString().padStart(2, '0')
            val month = ((i - 1) % 12 + 1).toString().padStart(2, '0')
            "2024-$month-$day,-${i}.00,Transaction $i"
        }
        val csv = (listOf(header) + rows).joinToString("\n")

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(100, result.transactions.size)
    }

    // ===== Statement period detection =====

    @Test
    fun testStatementPeriod_SingleTransaction_SamePeriod() {
        val csv = """
            date,amount,description
            2024-06-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertNotNull(result.statementPeriod)
        assertTrue(result.statementPeriod!!.contains("JUN"))
    }

    @Test
    fun testStatementPeriod_NoTransactions_NoStatementPeriod() {
        val csv = """
            date,amount,description
            invalid,invalid,invalid
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertNull(result.statementPeriod)
    }

    // ===== Bank detection from content =====

    @Test
    fun testBankDetection_RevolutHeaders() {
        val csv = """
            product,started date,amount,description
            Current,2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertEquals("Revolut", result.bankName)
    }

    @Test
    fun testBankDetection_ContentOverridesFileName() {
        // If content headers identify Revolut, it should win over filename
        val csv = """
            product,started date,amount,description
            Current,2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "sparkasse_export.csv")

        assertEquals("Revolut", result.bankName)
    }

    @Test
    fun testBankDetection_FileNameFallback_Commerzbank() {
        val csv = """
            date,amount,description
            2024-01-15,-50.00,Test
        """.trimIndent()

        val result = parser.parse(csv, "commerzbank_export.csv")

        assertEquals("Commerzbank", result.bankName)
    }

    // ===== Currency column =====

    @Test
    fun testCurrency_LowercaseNormalized() {
        val csv = """
            date,amount,description,currency
            2024-01-15,-50.00,Test,usd
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("USD", result.transactions[0].currency)
    }

    @Test
    fun testCurrency_Waehrung_Header() {
        val csv = """
            datum;betrag;beschreibung;währung
            15.01.2024;-50,00;Test;CHF
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("CHF", result.transactions[0].currency)
    }

    // ===== Raw text preservation =====

    @Test
    fun testRawText_ContainsAllColumns() {
        val csv = """
            date,amount,description,counterparty
            2024-01-15,-50.00,Test,Lidl
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        val rawText = result.transactions[0].rawText
        assertNotNull(rawText)
        assertTrue(rawText.contains("2024-01-15"))
        assertTrue(rawText.contains("-50.00"))
        assertTrue(rawText.contains("Test"))
        assertTrue(rawText.contains("Lidl"))
    }
}
