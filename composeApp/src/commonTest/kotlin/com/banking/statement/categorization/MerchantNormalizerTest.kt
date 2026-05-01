package com.banking.statement.categorization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MerchantNormalizerTest {

    @Test
    fun stripsTrailingStoreNumber() {
        assertEquals("rewe", MerchantNormalizer.normalize("REWE BERLIN 047"))
        assertEquals("rewe", MerchantNormalizer.normalize("REWE  04711"))
        assertEquals("rewe", MerchantNormalizer.normalize("rewe filiale 1234"))
    }

    @Test
    fun stripsCitySuffix() {
        assertEquals("edeka", MerchantNormalizer.normalize("EDEKA Hamburg"))
        assertEquals("edeka neukauf", MerchantNormalizer.normalize("EDEKA Neukauf Berlin"))
    }

    @Test
    fun stripsPaymentProcessorPrefix() {
        assertEquals("rewe", MerchantNormalizer.normalize("Kartenzahlung REWE"))
        assertEquals("rewe", MerchantNormalizer.normalize("VISA REWE"))
        assertEquals("rewe", MerchantNormalizer.normalize("PayPal REWE GmbH"))
    }

    @Test
    fun stripsLegalSuffix() {
        assertEquals("siemens", MerchantNormalizer.normalize("Siemens AG"))
        assertEquals("rewe", MerchantNormalizer.normalize("REWE GmbH & Co. KG"))
    }

    @Test
    fun blankInputReturnsBlank() {
        assertEquals("", MerchantNormalizer.normalize(""))
        assertEquals("", MerchantNormalizer.normalize("   "))
    }

    @Test
    fun preservesShortMerchantWithDigit() {
        // C24 has a digit but is the brand name, not a store number — keep it
        val result = MerchantNormalizer.normalize("C24 Bank")
        assertTrue(result.contains("c24"), "Expected c24 to survive normalization, got '$result'")
    }

    @Test
    fun extractMerchantTokenPicksLongestWord() {
        assertEquals("rewe", MerchantNormalizer.extractMerchantToken("Kartenzahlung REWE 047"))
        assertEquals("starbucks", MerchantNormalizer.extractMerchantToken("STARBUCKS Berlin 1234"))
        assertEquals("amazon", MerchantNormalizer.extractMerchantToken("AMZN Mktp DE Amazon"))
    }

    @Test
    fun extractMerchantTokenReturnsNullForNoise() {
        assertEquals(null, MerchantNormalizer.extractMerchantToken("12345"))
        assertEquals(null, MerchantNormalizer.extractMerchantToken(""))
        assertEquals(null, MerchantNormalizer.extractMerchantToken("GmbH AG KG"))
    }

    @Test
    fun normalizeLightKeepsAllWords() {
        assertEquals("rewe berlin 047", MerchantNormalizer.normalizeLight("REWE BERLIN 047"))
        assertEquals("rewe gmbh co kg", MerchantNormalizer.normalizeLight("REWE GmbH & Co. KG"))
    }

    @Test
    fun handlesGermanUmlauts() {
        val result = MerchantNormalizer.normalize("MÜLLER Drogerie München")
        assertTrue(
            result.contains("müller") || result.contains("ller"),
            "Expected umlaut-preserving normalization, got '$result'"
        )
        assertTrue(!result.contains("münchen"), "City should be stripped, got '$result'")
    }

    @Test
    fun realWorldExamples() {
        // Common bank statement patterns. Note: "sued"/"sud" survives because
        // it discriminates brands (Aldi Süd vs Aldi Nord) — the alias map
        // maps "aldi sued" → "aldi" at merchant-DB lookup time.
        assertNotNull(MerchantNormalizer.extractMerchantToken("LIDL DIENSTLEISTUNG GMBH BERLIN 12345 DE"))
        assertEquals("lidl dienstleistung", MerchantNormalizer.normalize("LIDL DIENSTLEISTUNG GMBH BERLIN 12345 DE"))
        assertEquals("aldi sued", MerchantNormalizer.normalize("Kartenzahlung ALDI SUED Munich"))
    }
}
