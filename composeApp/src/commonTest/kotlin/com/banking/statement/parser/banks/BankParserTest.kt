package com.banking.statement.parser.banks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for BankPdfParser interface, DetectionConfidence, BankDetectionResult,
 * and BankParserRegistry.
 */
class BankParserTest {

    // ===== DetectionConfidence tests =====

    @Test
    fun testDetectionConfidence_Scores() {
        assertEquals(0, DetectionConfidence.NONE.score)
        assertEquals(25, DetectionConfidence.LOW.score)
        assertEquals(50, DetectionConfidence.MEDIUM.score)
        assertEquals(75, DetectionConfidence.HIGH.score)
        assertEquals(100, DetectionConfidence.CERTAIN.score)
    }

    @Test
    fun testDetectionConfidence_Ordering() {
        assertTrue(DetectionConfidence.NONE.score < DetectionConfidence.LOW.score)
        assertTrue(DetectionConfidence.LOW.score < DetectionConfidence.MEDIUM.score)
        assertTrue(DetectionConfidence.MEDIUM.score < DetectionConfidence.HIGH.score)
        assertTrue(DetectionConfidence.HIGH.score < DetectionConfidence.CERTAIN.score)
    }

    @Test
    fun testDetectionConfidence_AllValues() {
        assertEquals(5, DetectionConfidence.entries.size)
    }

    // ===== BankDetectionResult tests =====

    @Test
    fun testBankDetectionResult_Creation() {
        val parser = DeutscheBankParser()
        val result = BankDetectionResult(
            parser = parser,
            confidence = DetectionConfidence.HIGH,
            matchedIdentifiers = listOf("deutsche bank", "deutdedb")
        )

        assertEquals(parser, result.parser)
        assertEquals(DetectionConfidence.HIGH, result.confidence)
        assertEquals(2, result.matchedIdentifiers.size)
        assertTrue(result.matchedIdentifiers.contains("deutsche bank"))
    }

    @Test
    fun testBankDetectionResult_EmptyIdentifiers() {
        val parser = SparkasseParser()
        val result = BankDetectionResult(
            parser = parser,
            confidence = DetectionConfidence.LOW
        )

        assertTrue(result.matchedIdentifiers.isEmpty())
    }

    // ===== BankParserRegistry tests =====

    @Test
    fun testBankParserRegistry_SupportedBanks_NotEmpty() {
        val banks = BankParserRegistry.supportedBanks()

        assertTrue(banks.isNotEmpty())
        assertTrue(banks.size > 20)  // Should have many bank parsers
    }

    @Test
    fun testBankParserRegistry_SupportedBanks_ContainsMajorBanks() {
        val banks = BankParserRegistry.supportedBanks()

        assertTrue(banks.any { it.contains("Deutsche Bank", ignoreCase = true) })
        assertTrue(banks.any { it.contains("Sparkasse", ignoreCase = true) })
        assertTrue(banks.any { it.contains("N26", ignoreCase = true) })
        assertTrue(banks.any { it.contains("DKB", ignoreCase = true) })
        assertTrue(banks.any { it.contains("ING", ignoreCase = true) })
        assertTrue(banks.any { it.contains("Commerzbank", ignoreCase = true) })
    }

    @Test
    fun testBankParserRegistry_SupportedBanks_IsSorted() {
        val banks = BankParserRegistry.supportedBanks()
        val sortedBanks = banks.sorted()

        assertEquals(sortedBanks, banks)
    }

    @Test
    fun testBankParserRegistry_GetParserByName_Existing() {
        val parser = BankParserRegistry.getParserByName("Deutsche Bank")

        assertNotNull(parser)
        assertEquals("Deutsche Bank", parser.bankName)
    }

    @Test
    fun testBankParserRegistry_GetParserByName_CaseInsensitive() {
        val parser1 = BankParserRegistry.getParserByName("deutsche bank")
        val parser2 = BankParserRegistry.getParserByName("DEUTSCHE BANK")

        assertNotNull(parser1)
        assertNotNull(parser2)
    }

    @Test
    fun testBankParserRegistry_GetParserByName_NotFound() {
        val parser = BankParserRegistry.getParserByName("NonExistent Bank XYZ")

        assertNull(parser)
    }

    @Test
    fun testBankParserRegistry_DetectBanks_DeutscheBank() {
        val pdfText = """
            Deutsche Bank AG
            Kontoauszug
            IBAN: DE89 3704 0044 0532 0130 00
            BIC: DEUTDEDB
        """.trimIndent()

        val results = BankParserRegistry.detectBanks(pdfText)

        assertTrue(results.isNotEmpty())
        assertEquals("Deutsche Bank", results.first().parser.bankName)
        assertTrue(results.first().confidence.score >= DetectionConfidence.HIGH.score)
    }

    @Test
    fun testBankParserRegistry_DetectBanks_Sparkasse() {
        val pdfText = """
            Sparkasse München
            Kontoauszug Nr. 1/2024
            IBAN: DE12 3456 7890 1234 5678 90
        """.trimIndent()

        val results = BankParserRegistry.detectBanks(pdfText)

        assertTrue(results.isNotEmpty())
        val sparkasse = results.find { it.parser.bankName.contains("Sparkasse", ignoreCase = true) }
        assertNotNull(sparkasse)
    }

    @Test
    fun testBankParserRegistry_DetectBanks_N26() {
        val pdfText = """
            N26 Bank GmbH
            Kontoauszug Nr. 09/2025
            BIC: NTSBDEB1XXX
        """.trimIndent()

        val results = BankParserRegistry.detectBanks(pdfText)

        assertTrue(results.isNotEmpty())
        val n26 = results.find { it.parser.bankName == "N26" }
        assertNotNull(n26)
        assertTrue(n26.confidence.score >= DetectionConfidence.HIGH.score)
    }

    @Test
    fun testBankParserRegistry_DetectBanks_EmptyText() {
        val results = BankParserRegistry.detectBanks("")

        assertTrue(results.isEmpty())
    }

    @Test
    fun testBankParserRegistry_DetectBanks_NoMatch() {
        val pdfText = "Random text without bank identifiers"

        val results = BankParserRegistry.detectBanks(pdfText)

        // Should return empty or very low confidence results
        assertTrue(results.isEmpty() || results.all { it.confidence.score < DetectionConfidence.MEDIUM.score })
    }

    @Test
    fun testBankParserRegistry_DetectBanks_SortedByConfidence() {
        val pdfText = """
            Kontoauszug
            IBAN: DE12345678901234567890
            Some transaction data
        """.trimIndent()

        val results = BankParserRegistry.detectBanks(pdfText)

        // Verify sorting (highest confidence first)
        for (i in 0 until results.size - 1) {
            assertTrue(results[i].confidence.score >= results[i + 1].confidence.score)
        }
    }

    @Test
    fun testBankParserRegistry_FindParser_HighConfidence() {
        val pdfText = """
            Deutsche Bank AG
            BIC: DEUTDEFF
            Kontoauszug
        """.trimIndent()

        val parser = BankParserRegistry.findParser(pdfText)

        assertNotNull(parser)
        assertEquals("Deutsche Bank", parser.bankName)
    }

    @Test
    fun testBankParserRegistry_FindParser_LowConfidence_ReturnsNull() {
        val pdfText = "Generic banking text without clear bank indicators"

        val parser = BankParserRegistry.findParser(pdfText)

        // Should return null when confidence is low (needs user selection)
        assertNull(parser)
    }

    @Test
    fun testBankParserRegistry_NeedsUserSelection_AmbiguousText() {
        val pdfText = """
            Kontoauszug
            IBAN: DE12345678901234567890
            Some generic German banking text
        """.trimIndent()

        val needsSelection = BankParserRegistry.needsUserSelection(pdfText)

        // Ambiguous text should require user selection
        assertTrue(needsSelection)
    }

    @Test
    fun testBankParserRegistry_NeedsUserSelection_ClearIdentifier() {
        val pdfText = """
            Deutsche Bank AG
            BIC: DEUTDEFF
            Kontoauszug vom 01.01.2024
        """.trimIndent()

        val needsSelection = BankParserRegistry.needsUserSelection(pdfText)

        // Clear identifier should not need user selection
        assertFalse(needsSelection)
    }

    @Test
    fun testBankParserRegistry_NeedsUserSelection_EmptyText() {
        val needsSelection = BankParserRegistry.needsUserSelection("")

        assertTrue(needsSelection)
    }
}
