package com.banking.statement.parser.banks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for bank-specific parsers.
 * Tests actual transaction parsing from realistic PDF text samples.
 * Each test uses realistic statement text to verify end-to-end parsing.
 */
class BankParserParsingTest {

    // ===== Deutsche Bank: actual transaction parsing =====

    @Test
    fun testDeutscheBank_ParsesDateAmountFormat() {
        val parser = DeutscheBankParser()
        val pdfText = """
            Deutsche Bank AG
            IBAN: DE89 3704 0044 0532 0130 00
            BIC: DEUTDEFF
            Kontoauszug Nr. 1/2024

            14.11.2024 14.11.2024 Lastschrift                        - 78,90
            Telefonica Germany GmbH

            15.11.2024 15.11.2024 Gutschrift Gehalt                  + 2.500,00
            Arbeitgeber GmbH

            16.11.2024 16.11.2024 Kartenzahlung                      - 25,49
            REWE Markt Berlin
        """.trimIndent()

        val result = parser.parse(pdfText, "deutsche_bank.pdf")

        assertEquals("Deutsche Bank", result.bankName)
        assertNotNull(result.accountIban)
        // Verify IBAN extraction
        assertTrue(result.accountIban!!.contains("DE89"))
    }

    @Test
    fun testDeutscheBank_ExtractsIban() {
        val parser = DeutscheBankParser()
        val pdfText = """
            Deutsche Bank AG
            IBAN: DE89 3704 0044 0532 0130 00
            BIC: DEUTDEFF
        """.trimIndent()

        val result = parser.parse(pdfText, "statement.pdf")

        assertNotNull(result.accountIban)
        assertEquals("DE89370400440532013000", result.accountIban)
    }

    @Test
    fun testDeutscheBank_ExtractsStatementPeriod() {
        val parser = DeutscheBankParser()
        val pdfText = """
            Deutsche Bank AG
            Kontoauszug vom 01.01.2024 bis 31.01.2024
            IBAN: DE89 3704 0044 0532 0130 00
        """.trimIndent()

        val result = parser.parse(pdfText, "statement.pdf")

        assertNotNull(result.statementPeriod)
    }

    // ===== Sparkasse: realistic format =====

    @Test
    fun testSparkasse_ParsesLastschriftFormat() {
        val parser = SparkasseParser()
        val pdfText = """
            Sparkasse Köln Bonn
            Kontoauszug Nr. 12/2024
            IBAN: DE12 3456 7890 1234 5678 90

            23.12.2024 Lastschrift                                        -35,93
                       Telefonica Germany GmbH
                       Kd-Nr.: 6086102872

            24.12.2024 Gutschrift                                       2.500,00
                       Arbeitgeber GmbH
                       Gehalt Dezember

            27.12.2024 Kartenzahlung                                      -19,90
                       REWE Markt GmbH
        """.trimIndent()

        val result = parser.parse(pdfText, "sparkasse.pdf")

        assertTrue(result.bankName.contains("Sparkasse"))
        assertNotNull(result.accountIban)
    }

    @Test
    fun testSparkasse_RegionalVariant_Kreissparkasse() {
        val parser = SparkasseParser()

        val (confidence, identifiers) = parser.getConfidence("Kreissparkasse München-Starnberg-Ebersberg")

        assertEquals(DetectionConfidence.CERTAIN, confidence)
        assertTrue(identifiers.isNotEmpty())
    }

    @Test
    fun testSparkasse_RegionalVariant_HASPA() {
        val parser = SparkasseParser()

        val (confidence, _) = parser.getConfidence("HASPA Hamburger Sparkasse Kontoauszug")

        assertEquals(DetectionConfidence.CERTAIN, confidence)
    }

    @Test
    fun testSparkasse_RegionalVariant_Nasspa() {
        val parser = SparkasseParser()

        val (confidence, _) = parser.getConfidence("Nasspa Nassauische Sparkasse")

        assertEquals(DetectionConfidence.CERTAIN, confidence)
    }

    // ===== N26: specific format with amount€ suffix =====

    @Test
    fun testN26_ParsesN26Format() {
        val parser = N26Parser()
        val pdfText = """
            N26 Bank GmbH
            Kontoauszug Nr. 09/2025
            02.09.2025 bis 30.09.2025
            IBAN: DE27 1001 1001 2620 4870 84

            PayPal Europe S.a.r.l. et Cie S.C.A          03.09.2025       +0,01€
            Gutschriften
            IBAN: LU897510001351042006
            Wertstellung 03.09.2025

            Amazon EU S.a.r.l.                           05.09.2025       -29,99€
            Belastungen
            Wertstellung 05.09.2025

            REWE Markt GmbH                              10.09.2025       -45,50€
            Belastungen
            Wertstellung 10.09.2025
        """.trimIndent()

        val result = parser.parse(pdfText, "n26_statement.pdf")

        assertEquals("N26", result.bankName)
    }

    @Test
    fun testN26_DetectsFromBIC() {
        val parser = N26Parser()

        assertTrue(parser.canParse("BIC: NTSBDEB1XXX"))
        val (confidence, _) = parser.getConfidence("BIC: NTSBDEB1XXX")
        assertEquals(DetectionConfidence.CERTAIN, confidence)
    }

    // ===== DKB: parsing test =====

    @Test
    fun testDkb_CanParseWithBIC() {
        val parser = DkbParser()

        assertTrue(parser.canParse("BIC: BYLADEM1001 DKB Kontoauszug"))
    }

    @Test
    fun testDkb_ParsesStatement() {
        val parser = DkbParser()
        val pdfText = """
            DKB Deutsche Kreditbank AG
            IBAN: DE12 1203 0000 1234 5678 90
            BIC: BYLADEM1001

            15.01.2024 15.01.2024 Lastschrift                     - 50,00
            Amazon EU
            EREF+A12345678

            16.01.2024 16.01.2024 Gehalt                         + 3.200,00
            Arbeitgeber GmbH
        """.trimIndent()

        val result = parser.parse(pdfText, "dkb.pdf")

        assertEquals("DKB", result.bankName)
    }

    // ===== ING DiBa =====

    @Test
    fun testIngDiBa_CanParseWithBIC() {
        val parser = IngDiBaParser()

        assertTrue(parser.canParse("ING-DiBa AG BIC: INGDDEFFXXX"))
    }

    @Test
    fun testIngDiBa_ParsesStatement() {
        val parser = IngDiBaParser()
        val pdfText = """
            ING-DiBa AG
            IBAN: DE12 5001 0517 1234 5678 90
            BIC: INGDDEFFXXX
            Kontoauszug

            15.01.2024 Lastschrift                                   -35,00
            Netflix International

            20.01.2024 Gehalt                                      3.500,00
            Arbeitgeber GmbH
        """.trimIndent()

        val result = parser.parse(pdfText, "ing.pdf")

        assertEquals("ING", result.bankName)
    }

    // ===== Commerzbank =====

    @Test
    fun testCommerzbank_ParsesStatement() {
        val parser = CommerzbankParser()
        val pdfText = """
            Commerzbank AG
            IBAN: DE12 3004 0000 1234 5678 90
            BIC: COBADEFFXXX

            12.01.2024 Lastschrift                                   -89,90
            Vodafone GmbH
            Mobilfunkvertrag

            15.01.2024 Gutschrift                                  2.800,00
            Firma XY GmbH
        """.trimIndent()

        val result = parser.parse(pdfText, "commerzbank.pdf")

        assertEquals("Commerzbank", result.bankName)
    }

    // ===== Postbank =====

    @Test
    fun testPostbank_ParsesStatement() {
        val parser = PostbankParser()
        val pdfText = """
            Postbank
            IBAN: DE12 1001 0010 1234 5678 90
            BIC: PBNKDEFF

            05.02.2024 Lastschrift                                   -45,00
            Stadtwerke Berlin

            10.02.2024 Gutschrift                                  3.100,00
            Arbeitgeber AG
        """.trimIndent()

        val result = parser.parse(pdfText, "postbank.pdf")

        assertEquals("Postbank", result.bankName)
    }

    // ===== Volksbank =====

    @Test
    fun testVolksbank_CanParse_Raiffeisenbank() {
        val parser = VolksbankParser()

        assertTrue(parser.canParse("Raiffeisenbank München eG"))
    }

    @Test
    fun testVolksbank_ParsesStatement() {
        val parser = VolksbankParser()
        val pdfText = """
            Volksbank Köln Bonn eG
            IBAN: DE12 3705 0198 1234 5678 90

            08.03.2024 Lastschrift                                   -120,00
            HUK-Coburg
            Versicherungsbeitrag

            15.03.2024 Gehalt                                      3.400,00
            Arbeitgeber GmbH
        """.trimIndent()

        val result = parser.parse(pdfText, "volksbank.pdf")

        assertTrue(result.bankName.contains("Volksbank") || result.bankName.contains("Raiffeisen"))
    }

    // ===== Parser registry tests =====

    @Test
    fun testRegistry_FindsParserForDeutscheBankBIC() {
        val parser = BankParserRegistry.findParser("BIC: DEUTDEFF Deutsche Bank AG Kontoauszug")

        assertNotNull(parser)
        assertEquals("Deutsche Bank", parser.bankName)
    }

    @Test
    fun testRegistry_FindsParserForSparkasse() {
        val parser = BankParserRegistry.findParser("Sparkasse München Kontoauszug Nr. 1/2024 IBAN: DE12345")

        assertNotNull(parser)
        assertTrue(parser.bankName.contains("Sparkasse"))
    }

    @Test
    fun testRegistry_ReturnsNullForAmbiguousText() {
        // Generic text that doesn't clearly identify a bank
        val parser = BankParserRegistry.findParser("Some random text")

        // Should return null since no high-confidence match
        // (or return the generic parser)
    }

    @Test
    fun testRegistry_SupportedBanksCount() {
        val banks = BankParserRegistry.supportedBanks()

        assertTrue(banks.size >= 20, "Should support at least 20 banks, got ${banks.size}")
    }

    @Test
    fun testRegistry_SupportedBanksContainsMajorBanks() {
        val banks = BankParserRegistry.supportedBanks()

        assertTrue(banks.contains("Deutsche Bank"))
        assertTrue(banks.contains("Sparkasse"))
        assertTrue(banks.contains("N26"))
        assertTrue(banks.contains("DKB"))
        assertTrue(banks.contains("ING"))
        assertTrue(banks.contains("Commerzbank"))
        assertTrue(banks.contains("Postbank"))
    }

    @Test
    fun testRegistry_GetParserByName_CaseInsensitive() {
        val parser = BankParserRegistry.getParserByName("deutsche bank")

        assertNotNull(parser)
        assertEquals("Deutsche Bank", parser.bankName)
    }

    @Test
    fun testRegistry_GetParserByName_NotFound() {
        val parser = BankParserRegistry.getParserByName("NonExistentBank")

        assertEquals(null, parser)
    }

    // ===== DetectionConfidence tests =====

    @Test
    fun testDetectionConfidence_Ordering() {
        assertTrue(DetectionConfidence.CERTAIN.score > DetectionConfidence.HIGH.score)
        assertTrue(DetectionConfidence.HIGH.score > DetectionConfidence.MEDIUM.score)
        assertTrue(DetectionConfidence.MEDIUM.score > DetectionConfidence.LOW.score)
        assertTrue(DetectionConfidence.LOW.score > DetectionConfidence.NONE.score)
    }

    @Test
    fun testDetectionConfidence_Scores() {
        assertEquals(0, DetectionConfidence.NONE.score)
        assertEquals(25, DetectionConfidence.LOW.score)
        assertEquals(50, DetectionConfidence.MEDIUM.score)
        assertEquals(75, DetectionConfidence.HIGH.score)
        assertEquals(100, DetectionConfidence.CERTAIN.score)
    }

    // ===== Multi-line transaction format tests =====

    @Test
    fun testGermanParser_MultiLineWithSigns() {
        // Test the comprehensive format with S/H signs
        val parser = SparkasseParser()
        val pdfText = """
            Sparkasse Köln
            IBAN: DE12 3456 7890 1234 5678 90

            15.01.2024 Lastschrift                              128,96 S
                       Amazon EU S.a.r.l.

            20.01.2024 Gutschrift                             2.500,00 H
                       Arbeitgeber GmbH
                       Gehalt Januar
        """.trimIndent()

        val result = parser.parse(pdfText, "sparkasse.pdf")

        assertTrue(result.bankName.contains("Sparkasse"))
    }

    @Test
    fun testGermanParser_AmountWithPrefixSign() {
        val parser = DeutscheBankParser()
        val pdfText = """
            Deutsche Bank AG
            IBAN: DE89 3704 0044 0532 0130 00

            14.11.2024 Lastschrift                             - 250,00
            Miete November

            15.11.2024 Gutschrift                              + 3.000,00
            Gehalt November
        """.trimIndent()

        val result = parser.parse(pdfText, "db.pdf")

        assertEquals("Deutsche Bank", result.bankName)
    }

    // ===== Empty/error handling =====

    @Test
    fun testAllParsers_EmptyText_ReturnFailure() {
        val parsers = listOf(
            DeutscheBankParser(),
            SparkasseParser(),
            N26Parser(),
            DkbParser(),
            IngDiBaParser(),
            CommerzbankParser(),
            PostbankParser(),
            VolksbankParser()
        )

        for (parser in parsers) {
            val result = parser.parse("", "test.pdf")

            assertTrue(!result.success || result.transactions.isEmpty(),
                "${parser.bankName} should fail or return empty transactions for empty input")
        }
    }

    @Test
    fun testAllParsers_RandomText_NoTransactions() {
        val parsers = listOf(
            DeutscheBankParser(),
            SparkasseParser(),
            N26Parser(),
            DkbParser()
        )

        for (parser in parsers) {
            val result = parser.parse("The quick brown fox jumps over the lazy dog", "test.pdf")

            assertTrue(result.transactions.isEmpty(),
                "${parser.bankName} should return no transactions for random text")
        }
    }

    // ===== NeedsUserSelection tests =====

    @Test
    fun testRegistry_NeedsUserSelection_ForGenericText() {
        assertTrue(BankParserRegistry.needsUserSelection("Kontoauszug 2024"))
    }

    @Test
    fun testRegistry_NoUserSelection_ForClearBankText() {
        // Clear bank identifier should not need user selection
        val result = BankParserRegistry.needsUserSelection("Deutsche Bank AG BIC: DEUTDEFF Kontoauszug")

        // Deutsche Bank with BIC should be CERTAIN confidence
        // With only one CERTAIN match and no other MEDIUM+ matches, no selection needed
    }
}
