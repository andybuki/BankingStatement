package com.banking.statement.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for amount parsing across German and international formats.
 * Critical for a finance app: a parsing bug could miscategorize thousands of transactions.
 */
class AmountParsingTest {

    private val parser = CsvParser()

    // ===== German format: 1.234,56 (dots for thousands, comma for decimal) =====

    @Test
    fun testGermanAmount_SimpleDecimal() {
        val csv = "date;betrag;description\n2024-01-15;-50,00;Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    @Test
    fun testGermanAmount_ThousandsSeparator() {
        val csv = "date;betrag;description\n2024-01-15;-1.234,56;Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-1234.56, result.transactions[0].amount)
    }

    @Test
    fun testGermanAmount_MultipleThousandsSeparators() {
        val csv = "date;betrag;description\n2024-01-15;1.234.567,89;Salary"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1234567.89, result.transactions[0].amount)
    }

    @Test
    fun testGermanAmount_SmallDecimal() {
        val csv = "date;betrag;description\n2024-01-15;-0,50;Fee"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-0.5, result.transactions[0].amount)
    }

    @Test
    fun testGermanAmount_CommaOnly_NoThousands() {
        val csv = "date;betrag;description\n2024-01-15;-99,99;Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-99.99, result.transactions[0].amount)
    }

    // ===== International format: 1,234.56 (commas for thousands, dot for decimal) =====

    @Test
    fun testInternationalAmount_SimpleDecimal() {
        val csv = "date,amount,description\n2024-01-15,-50.00,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    @Test
    fun testInternationalAmount_ThousandsSeparator() {
        val csv = "date;amount;description\n2024-01-15;-1,234.56;Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-1234.56, result.transactions[0].amount)
    }

    @Test
    fun testInternationalAmount_MultipleThousandsSeparators() {
        val csv = "date;amount;description\n2024-01-15;1,234,567.89;Salary"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1234567.89, result.transactions[0].amount)
    }

    // ===== Currency symbol stripping =====

    @Test
    fun testAmount_EuroSymbolPrefix() {
        val csv = "date,amount,description\n2024-01-15,€-50.00,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    @Test
    fun testAmount_EURTextSuffix() {
        val csv = "date,amount,description\n2024-01-15,-50.00 EUR,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    @Test
    fun testAmount_DollarSymbolPrefix() {
        val csv = "date,amount,description\n2024-01-15,$-100.00,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-100.0, result.transactions[0].amount)
    }

    @Test
    fun testAmount_USDTextSuffix() {
        val csv = "date,amount,description\n2024-01-15,-100.00 USD,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-100.0, result.transactions[0].amount)
    }

    // ===== Edge cases =====

    @Test
    fun testAmount_WholeNumber_NoDot_NoComma() {
        val csv = "date,amount,description\n2024-01-15,-100,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-100.0, result.transactions[0].amount)
    }

    @Test
    fun testAmount_Zero() {
        val csv = "date,amount,description\n2024-01-15,0.00,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(0.0, result.transactions[0].amount)
    }

    @Test
    fun testAmount_LargePositive() {
        val csv = "date,amount,description\n2024-01-15,99999.99,Big deposit"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(99999.99, result.transactions[0].amount)
    }

    @Test
    fun testAmount_VerySmall() {
        val csv = "date,amount,description\n2024-01-15,-0.01,Rounding"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-0.01, result.transactions[0].amount)
    }

    @Test
    fun testAmount_InvalidAmount_SkipsRow() {
        val csv = "date,amount,description\n2024-01-15,abc,Test\n2024-01-16,-50.00,Valid"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    @Test
    fun testAmount_EmptyAmount_SkipsRow() {
        val csv = "date,amount,description\n2024-01-15,,Test\n2024-01-16,-25.00,Valid"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1, result.transactions.size)
    }

    @Test
    fun testAmount_SpacesInAmount() {
        val csv = "date,amount,description\n2024-01-15, -50.00 ,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(-50.0, result.transactions[0].amount)
    }

    // ===== Multiple transactions with mixed formats =====

    @Test
    fun testMixedFormats_GermanAndInternational() {
        // Using semicolon delimiter to avoid ambiguity with comma in amounts
        val csv = """
            date;amount;description
            2024-01-15;-1.234,56;German format
            2024-01-16;-50,00;Simple German
            2024-01-17;100,00;German income
        """.trimIndent()

        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(3, result.transactions.size)
        assertEquals(-1234.56, result.transactions[0].amount)
        assertEquals(-50.0, result.transactions[1].amount)
        assertEquals(100.0, result.transactions[2].amount)
    }

    // ===== Balance column parsing =====

    @Test
    fun testBalance_GermanFormat() {
        val csv = "date;betrag;beschreibung;saldo\n15.01.2024;-50,00;Test;1.234,56"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1234.56, result.transactions[0].balance)
    }

    @Test
    fun testBalance_InternationalFormat() {
        val csv = "date,amount,description,balance\n2024-01-15,-50.00,Test,1500.00"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals(1500.0, result.transactions[0].balance)
    }

    // ===== Value date tests =====

    @Test
    fun testValueDate_Mapped() {
        val csv = "date,value_date,amount,description\n2024-01-15,2024-01-17,-50.00,Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        val tx = result.transactions[0]
        assertEquals(2024, tx.bookingDate.year)
        assertEquals(1, tx.bookingDate.monthNumber)
        assertEquals(15, tx.bookingDate.dayOfMonth)
        assertNotNull(tx.valueDate)
        assertEquals(17, tx.valueDate!!.dayOfMonth)
    }

    @Test
    fun testValueDate_Valuta_Header() {
        val csv = "datum;valuta;betrag;beschreibung\n15.01.2024;17.01.2024;-50,00;Test"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertNotNull(result.transactions[0].valueDate)
    }

    // ===== Counterparty IBAN column =====

    @Test
    fun testCounterpartyIban_Mapped() {
        val csv = "date,amount,description,counterparty_iban\n2024-01-15,-50.00,Test,DE89370400440532013000"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("DE89370400440532013000", result.transactions[0].counterpartyIban)
    }

    // ===== Transaction type column =====

    @Test
    fun testTransactionType_Mapped() {
        val csv = "date,amount,description,type\n2024-01-15,-50.00,Test,Lastschrift"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("Lastschrift", result.transactions[0].transactionType)
    }

    // ===== Remittance info column =====

    @Test
    fun testRemittanceInfo_Mapped() {
        val csv = "date,amount,description,remittance_info\n2024-01-15,-50.00,Test,INV-2024-001"
        val result = parser.parse(csv, "test.csv")

        assertTrue(result.success)
        assertEquals("INV-2024-001", result.transactions[0].remittanceInfo)
    }
}
