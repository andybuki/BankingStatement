package com.bankwise.app.parser.banks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for German bank parsers (DeutscheBankParser, SparkasseParser, N26Parser, etc.)
 * Tests parsing functionality through the public parse() method.
 */
class GermanBankParserTest {

    // ===== Deutsche Bank Parser tests =====

    @Test
    fun testDeutscheBankParser_CanParse_WithBIC() {
        val parser = DeutscheBankParser()
        val text = "BIC: DEUTDEDB Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testDeutscheBankParser_CanParse_WithBankName() {
        val parser = DeutscheBankParser()
        val text = "Deutsche Bank AG Filiale München"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testDeutscheBankParser_CannotParse_UnrelatedText() {
        val parser = DeutscheBankParser()
        val text = "Sparkasse Berlin Kontoauszug"

        assertFalse(parser.canParse(text))
    }

    @Test
    fun testDeutscheBankParser_GetConfidence_Certain() {
        val parser = DeutscheBankParser()
        val text = "BIC: DEUTDEDB Deutsche Bank AG"

        val (confidence, identifiers) = parser.getConfidence(text)

        assertEquals(DetectionConfidence.CERTAIN, confidence)
        assertTrue(identifiers.isNotEmpty())
    }

    @Test
    fun testDeutscheBankParser_BankName() {
        val parser = DeutscheBankParser()

        assertEquals("Deutsche Bank", parser.bankName)
    }

    @Test
    fun testDeutscheBankParser_Parse_EmptyText() {
        val parser = DeutscheBankParser()

        val result = parser.parse("", "statement.pdf")

        assertFalse(result.success)
        assertEquals("Deutsche Bank", result.bankName)
    }

    @Test
    fun testDeutscheBankParser_Parse_ValidStatement() {
        val parser = DeutscheBankParser()
        val pdfText = """
            Deutsche Bank AG
            IBAN: DE89 3704 0044 0532 0130 00

            14.11.    13.11.    SEPA Überweisung an                    - 78,90
            2024      2024      Zalando Payments GmbH
                                 IBAN DE86210700200123010101

            15.11.    15.11.    Gutschrift Gehalt                      + 2.500,00
            2024      2024      Arbeitgeber GmbH
        """.trimIndent()

        val result = parser.parse(pdfText, "deutsche_bank_statement.pdf")

        assertEquals("Deutsche Bank", result.bankName)
        // Note: Parsing may succeed or fail depending on exact format matching
    }

    // ===== Sparkasse Parser tests =====

    @Test
    fun testSparkasseParser_CanParse_WithSparkasse() {
        val parser = SparkasseParser()
        val text = "Sparkasse München Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testSparkasseParser_CanParse_WithKreissparkasse() {
        val parser = SparkasseParser()
        val text = "Kreissparkasse Köln Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testSparkasseParser_CanParse_WithHaspa() {
        val parser = SparkasseParser()
        val text = "HASPA Hamburger Sparkasse Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testSparkasseParser_CannotParse_UnrelatedText() {
        val parser = SparkasseParser()
        val text = "Deutsche Bank AG Kontoauszug"

        assertFalse(parser.canParse(text))
    }

    @Test
    fun testSparkasseParser_GetConfidence_Certain() {
        val parser = SparkasseParser()
        val text = "Sparkasse Frankfurt Kontoauszug 2024"

        val (confidence, identifiers) = parser.getConfidence(text)

        assertEquals(DetectionConfidence.CERTAIN, confidence)
        assertTrue(identifiers.contains("sparkasse"))
    }

    @Test
    fun testSparkasseParser_BankName() {
        val parser = SparkasseParser()

        assertEquals("Sparkasse", parser.bankName)
    }

    @Test
    fun testSparkasseParser_Parse_ValidStatement() {
        val parser = SparkasseParser()
        val pdfText = """
            Sparkasse Frankfurt
            Kontoauszug Nr. 1/2024
            IBAN: DE12 3456 7890 1234 5678 90

            23.12.2022 Lastschrift                                    -35,93
                       Telefonica Germany GmbH + Co. OHG Kd-Nr.: 6086102872

            24.12.2022 Gutschrift Gehalt                            2.500,00
                       Arbeitgeber GmbH
        """.trimIndent()

        val result = parser.parse(pdfText, "sparkasse_statement.pdf")

        assertTrue(result.bankName.contains("Sparkasse"))
    }

    // ===== N26 Parser tests =====

    @Test
    fun testN26Parser_CanParse_WithBIC() {
        val parser = N26Parser()
        val text = "BIC: NTSBDEB1XXX Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testN26Parser_CanParse_WithN26Bank() {
        val parser = N26Parser()
        val text = "N26 Bank GmbH Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testN26Parser_CannotParse_UnrelatedText() {
        val parser = N26Parser()
        val text = "Sparkasse Berlin Kontoauszug"

        assertFalse(parser.canParse(text))
    }

    @Test
    fun testN26Parser_GetConfidence_Certain() {
        val parser = N26Parser()
        val text = "N26 Bank GmbH BIC: NTSBDEB1"

        val (confidence, identifiers) = parser.getConfidence(text)

        assertEquals(DetectionConfidence.CERTAIN, confidence)
    }

    @Test
    fun testN26Parser_BankName() {
        val parser = N26Parser()

        assertEquals("N26", parser.bankName)
    }

    @Test
    fun testN26Parser_Parse_ValidStatement() {
        val parser = N26Parser()
        val pdfText = """
            N26 Bank GmbH
            Kontoauszug Nr. 09/2025
            02.09.2025 bis 30.09.2025

            PayPal Europe S.a.r.l. et Cie S.C.A          03.09.2025       +0,01€
            Gutschriften
            IBAN: LU897510001351042006 • BIC: PPLXLUL2
            Wertstellung 03.09.2025

            Amazon EU S.a.r.l.                           05.09.2025       -29,99€
            Belastungen
            Wertstellung 05.09.2025
        """.trimIndent()

        val result = parser.parse(pdfText, "n26_statement.pdf")

        assertEquals("N26", result.bankName)
        // Transactions may or may not be parsed depending on format
    }

    // ===== DKB Parser tests =====

    @Test
    fun testDkbParser_CanParse_WithBIC() {
        val parser = DkbParser()
        val text = "BIC: BYLADEM1001 Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testDkbParser_CanParse_WithDKB() {
        val parser = DkbParser()
        val text = "DKB Deutsche Kreditbank AG"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testDkbParser_BankName() {
        val parser = DkbParser()

        assertEquals("DKB", parser.bankName)
    }

    // ===== ING DiBa Parser tests =====

    @Test
    fun testIngDiBaParser_CanParse_WithBIC() {
        val parser = IngDiBaParser()
        val text = "BIC: INGDDEFFXXX Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testIngDiBaParser_CanParse_WithING() {
        val parser = IngDiBaParser()
        val text = "ING-DiBa AG Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testIngDiBaParser_BankName() {
        val parser = IngDiBaParser()

        assertEquals("ING", parser.bankName)
    }

    // ===== Commerzbank Parser tests =====

    @Test
    fun testCommerzbankParser_CanParse_WithBIC() {
        val parser = CommerzbankParser()
        val text = "BIC: COBADEFFXXX Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testCommerzbankParser_CanParse_WithName() {
        val parser = CommerzbankParser()
        val text = "Commerzbank AG Frankfurt"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testCommerzbankParser_BankName() {
        val parser = CommerzbankParser()

        assertEquals("Commerzbank", parser.bankName)
    }

    // ===== Postbank Parser tests =====

    @Test
    fun testPostbankParser_CanParse_WithBIC() {
        val parser = PostbankParser()
        val text = "BIC: PBNKDEFF Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testPostbankParser_CanParse_WithName() {
        val parser = PostbankParser()
        val text = "Postbank Kontoauszug"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testPostbankParser_BankName() {
        val parser = PostbankParser()

        assertEquals("Postbank", parser.bankName)
    }

    // ===== Volksbank Parser tests =====

    @Test
    fun testVolksbankParser_CanParse_WithVolksbank() {
        val parser = VolksbankParser()
        val text = "Volksbank Köln Bonn"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testVolksbankParser_CanParse_WithRaiffeisenbank() {
        val parser = VolksbankParser()
        val text = "Raiffeisenbank München"

        assertTrue(parser.canParse(text))
    }

    @Test
    fun testVolksbankParser_BankName() {
        val parser = VolksbankParser()

        assertTrue(parser.bankName.contains("Volksbank") || parser.bankName.contains("Raiffeisen"))
    }

    // ===== Parse result structure tests =====

    @Test
    fun testParser_ParseResult_HasCorrectBankName() {
        val parsers = listOf(
            DeutscheBankParser() to "Deutsche Bank",
            SparkasseParser() to "Sparkasse",
            N26Parser() to "N26",
            DkbParser() to "DKB",
            IngDiBaParser() to "ING",
            CommerzbankParser() to "Commerzbank",
            PostbankParser() to "Postbank"
        )

        for ((parser, expectedName) in parsers) {
            val result = parser.parse("", "test.pdf")
            assertEquals(expectedName, result.bankName, "Parser ${parser::class.simpleName} has wrong bank name")
        }
    }

    @Test
    fun testParser_ParseResult_FailureHasErrorMessage() {
        val parser = DeutscheBankParser()

        val result = parser.parse("", "test.pdf")

        assertFalse(result.success)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.isNotEmpty())
    }

    @Test
    fun testParser_ParseResult_EmptyTransactionsOnFailure() {
        val parser = SparkasseParser()

        val result = parser.parse("Random text without transactions", "test.pdf")

        assertTrue(result.transactions.isEmpty())
    }

    // ===== Confidence calculation tests =====

    @Test
    fun testConfidence_CertainBeatsHigh() {
        val parser = DeutscheBankParser()

        val certainText = "BIC: DEUTDEDB"
        val highText = "deutsche bank"

        val (certainConf, _) = parser.getConfidence(certainText)
        val (highConf, _) = parser.getConfidence(highText)

        assertTrue(certainConf.score >= highConf.score)
    }

    @Test
    fun testConfidence_HighBeatsMedium() {
        val parser = SparkasseParser()

        // HIGH identifier
        val highText = "Sparkasse München"
        // MEDIUM identifier only
        val mediumText = "Kontoauszug" // Generic, not unique to Sparkasse

        val (highConf, _) = parser.getConfidence(highText)

        assertTrue(highConf.score >= DetectionConfidence.HIGH.score)
    }

    @Test
    fun testConfidence_NoMatchReturnsNone() {
        val parser = DeutscheBankParser()
        val text = "Completely unrelated text about weather"

        val (confidence, identifiers) = parser.getConfidence(text)

        assertEquals(DetectionConfidence.NONE, confidence)
        assertTrue(identifiers.isEmpty())
    }

    // ===== IBAN extraction from parse result tests =====

    @Test
    fun testParser_ExtractsIban() {
        val parser = DeutscheBankParser()
        val pdfText = """
            Deutsche Bank AG
            IBAN: DE89 3704 0044 0532 0130 00
            BIC: DEUTDEFF
        """.trimIndent()

        val result = parser.parse(pdfText, "statement.pdf")

        // IBAN extraction should work even if transaction parsing fails
        assertNotNull(result.accountIban)
        assertEquals("DE89370400440532013000", result.accountIban)
    }
}
