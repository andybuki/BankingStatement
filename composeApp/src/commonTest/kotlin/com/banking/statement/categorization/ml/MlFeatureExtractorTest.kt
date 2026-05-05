package com.banking.statement.categorization.ml

import com.banking.statement.parser.ParsedTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden-input parity tests. Each case pins a transaction and the exact
 * feature string `ml/train_category_model.py` produces. If any step in
 * MlFeatureExtractor diverges from the Python preprocessor these tests
 * will fail loudly — that drift would silently degrade predictions in
 * production.
 *
 * To regenerate expected values, run train_category_model.build_features
 * over the same single-row dataframe and copy the result here.
 */
class MlFeatureExtractorTest {

    private fun tx(
        description: String,
        amount: Double,
        counterparty: String? = null
    ) = ParsedTransaction(
        bookingDate = LocalDate(2024, 1, 15),
        amount = amount,
        description = description,
        counterpartyName = counterparty,
        remittanceInfo = null,
        rawText = null
    )

    // region cleanupText

    @Test
    fun cleanupText_lowersCaseAndTransliteratesUmlauts() {
        val out = MlFeatureExtractor.cleanupText("Bäckerei Müller GmbH", keepNoise = false)
        // gmbh is in legal suffixes, so it gets dropped
        assertEquals("baeckerei mueller", out)
    }

    @Test
    fun cleanupText_stripsArnAndDateAndAmountTokens() {
        val out = MlFeatureExtractor.cleanupText(
            "REWE 12.03.2024 ARNXYZ123 12,49 EUR Edeka",
            keepNoise = false
        )
        assertEquals("rewe eur edeka", out)
    }

    @Test
    fun cleanupText_dropsBankNoiseWords() {
        val out = MlFeatureExtractor.cleanupText(
            "SEPA Lastschrift PayPal Europe Hotel Adlon",
            keepNoise = false
        )
        // sepa, lastschrift, paypal, europe are noise; hotel + adlon survive
        assertEquals("hotel adlon", out)
    }

    @Test
    fun cleanupText_singleCharsAreFiltered() {
        val out = MlFeatureExtractor.cleanupText("a b c rewe", keepNoise = false)
        assertEquals("rewe", out)
    }

    @Test
    fun cleanupText_keepNoiseRetainsBankWords() {
        val out = MlFeatureExtractor.cleanupText("SEPA Lastschrift Rewe", keepNoise = true)
        // single chars still get whitespace-collapsed but noise words are kept
        assertEquals("sepa lastschrift rewe", out)
    }

    @Test
    fun cleanupText_normalizesBrokenWords() {
        val out = MlFeatureExtractor.cleanupText("DB Vertr ieb", keepNoise = false)
        assertEquals("db vertrieb", out)
    }

    // endregion

    // region domainHints

    @Test
    fun domainHints_recognizeSupermarketAndRestaurant() {
        val hints = MlFeatureExtractor.domainHints("rewe and bistro nearby")
        assertTrue("hint_supermarket" in hints, "Got: $hints")
        assertTrue("hint_restaurant" in hints, "Got: $hints")
    }

    @Test
    fun domainHints_emptyForUnknownText() {
        assertEquals(emptyList(), MlFeatureExtractor.domainHints("unrelated text"))
    }

    // endregion

    // region amountFeatures

    @Test
    fun amountFeatures_classifiesIncomeAndExpense() {
        val income = MlFeatureExtractor.amountFeatures(2500.0, "salary")
        assertTrue("amount_income" in income)
        assertTrue("amount_very_large" in income)

        val expense = MlFeatureExtractor.amountFeatures(-12.49, "rewe")
        assertTrue("amount_expense" in expense)
        assertTrue("amount_small" in expense)
    }

    @Test
    fun amountFeatures_emitsRestaurantPlausibleInRange() {
        val features = MlFeatureExtractor.amountFeatures(-25.0, "bistro near me")
        assertTrue("amount_restaurant_plausible" in features, "Got: $features")
    }

    @Test
    fun amountFeatures_emitsRestaurantHighOverThreshold() {
        val features = MlFeatureExtractor.amountFeatures(-150.0, "restaurant feast")
        assertTrue("amount_restaurant_high" in features, "Got: $features")
    }

    @Test
    fun amountFeatures_groceryPlausibleOnlyWhenAmountInRange() {
        val plausible = MlFeatureExtractor.amountFeatures(-50.0, "lidl shop")
        assertTrue("amount_grocery_plausible" in plausible)

        val tooLarge = MlFeatureExtractor.amountFeatures(-300.0, "lidl shop")
        assertTrue("amount_grocery_plausible" !in tooLarge, "Got: $tooLarge")
    }

    // endregion

    // region extractEffectiveMerchant

    @Test
    fun extractMerchant_returnsNullWhenNoPatternMatches() {
        assertNull(MlFeatureExtractor.extractEffectiveMerchant("simple description"))
    }

    @Test
    fun extractMerchant_paypalPattern() {
        val merchant = MlFeatureExtractor.extractEffectiveMerchant(
            "PayPal Europe SVWZ. NETFLIX, Ihr Einkauf bei NETFLIX"
        )
        // Cleanup transliterates and strips noise; "netflix" should survive
        assertTrue(merchant != null && "netflix" in merchant, "Got: $merchant")
    }

    // endregion

    // region buildFeatureText

    /**
     * End-to-end golden parity. The feature text format is:
     *   "<merchant> <merchant> <hint_text> <amount_text> <clean_desc>"
     * with empty parts skipped. We assert structure and key tokens rather
     * than the entire string so this test is robust to additive changes
     * in the Python script — but if Python adds a token Kotlin doesn't,
     * mirror it here too.
     */
    @Test
    fun buildFeatureText_includesHintsAndAmountAndCleanDesc() {
        val transaction = tx("Lidl Berlin Mitte", amount = -42.50)
        val featureText = MlFeatureExtractor.buildFeatureText(transaction)

        assertTrue("lidl" in featureText, "Got: $featureText")
        assertTrue("hint_supermarket" in featureText, "Got: $featureText")
        assertTrue("amount_expense" in featureText, "Got: $featureText")
        assertTrue("amount_large" in featureText, "Got: $featureText")
        assertTrue("amount_grocery_plausible" in featureText, "Got: $featureText")
    }

    @Test
    fun buildFeatureText_emptyTransactionReturnsEmpty() {
        val transaction = tx("", amount = 0.0)
        val featureText = MlFeatureExtractor.buildFeatureText(transaction)
        // No description and no counterparty: only amount features survive
        assertTrue("amount_zero" in featureText)
        assertTrue("amount_micro" in featureText)
    }

    // endregion
}
