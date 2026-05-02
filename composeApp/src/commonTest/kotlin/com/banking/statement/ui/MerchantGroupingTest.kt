package com.banking.statement.ui

import com.banking.statement.categorization.TransactionCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantGroupingTest {

    private fun tx(
        id: Long,
        date: String,
        amount: Double,
        counterparty: String?,
        description: String = ""
    ) = TransactionDisplay(
        id = id,
        date = date,
        description = description,
        amount = amount,
        currency = "EUR",
        category = TransactionCategory.SUPERMARKET,
        counterparty = counterparty
    )

    @Test
    fun groupsRecurringMerchantAcrossStoreNumbers() {
        val transactions = listOf(
            tx(1, "05.01.2024", -12.34, "REWE BERLIN 047"),
            tx(2, "15.02.2024", -23.45, "REWE Berlin Filiale 0123"),
            tx(3, "20.03.2024", -34.56, "Kartenzahlung REWE 04711")
        )
        val grouped = MerchantGrouping.groupByMerchant(transactions)
        assertEquals(1, grouped.size, "Expected all REWE variants to collapse to one merchant, got ${grouped.keys}")
        val (_, merchantTxs) = grouped.entries.first()
        assertEquals(3, merchantTxs.size)
    }

    @Test
    fun keepsDistinctMerchantsApart() {
        val transactions = listOf(
            tx(1, "05.01.2024", -10.0, "REWE"),
            tx(2, "06.01.2024", -20.0, "Edeka Markt"),
            tx(3, "07.01.2024", -30.0, "Lidl Sagt Danke")
        )
        val grouped = MerchantGrouping.groupByMerchant(transactions)
        assertEquals(3, grouped.size)
    }

    @Test
    fun displayNamePicksMostFrequentCounterparty() {
        val transactions = listOf(
            tx(1, "01.01.2024", -10.0, "REWE Markt Berlin"),
            tx(2, "02.01.2024", -20.0, "REWE Markt Berlin"),
            tx(3, "03.01.2024", -30.0, "REWE 047")
        )
        val grouped = MerchantGrouping.groupByMerchant(transactions)
        assertEquals(1, grouped.size)
        assertEquals("REWE Markt Berlin", grouped.keys.first())
    }

    @Test
    fun displayNameTieBrokenByLength() {
        val transactions = listOf(
            tx(1, "01.01.2024", -10.0, "REWE 047"),
            tx(2, "02.01.2024", -20.0, "REWE Markt Berlin")
        )
        val grouped = MerchantGrouping.groupByMerchant(transactions)
        assertEquals("REWE Markt Berlin", grouped.keys.first())
    }

    @Test
    fun ignoresTransactionsWithBlankIdentity() {
        val transactions = listOf(
            tx(1, "01.01.2024", -10.0, null, description = ""),
            tx(2, "02.01.2024", -20.0, "REWE")
        )
        val grouped = MerchantGrouping.groupByMerchant(transactions)
        assertEquals(1, grouped.size)
    }

    @Test
    fun fallsBackToDescriptionWhenCounterpartyBlank() {
        val transactions = listOf(
            tx(1, "01.01.2024", -10.0, null, description = "ALNATURA Bio Markt 123"),
            tx(2, "02.01.2024", -20.0, null, description = "ALNATURA Filiale Berlin")
        )
        val grouped = MerchantGrouping.groupByMerchant(transactions)
        assertEquals(1, grouped.size, "Expected ALNATURA descriptions to group, got ${grouped.keys}")
    }

    @Test
    fun trendCalculationRespectsCanonicalGrouping() {
        // Same merchant across two months with branch-number drift.
        // Without canonical grouping these would appear as separate one-off
        // entries; with grouping they collapse into a single merchant trend.
        val transactions = listOf(
            tx(1, "05.01.2024", -50.0, "REWE BERLIN 047"),
            tx(2, "10.01.2024", -25.0, "REWE Filiale 0123"),
            tx(3, "05.02.2024", -60.0, "REWE BERLIN 047"),
            tx(4, "10.02.2024", -40.0, "REWE Markt Munich")
        )
        val trends = calculateMerchantTrendsFromTransactions(transactions)
        assertEquals(1, trends.size)
        val rewe = trends.first()
        assertEquals(2, rewe.currentMonthTransactions)
        assertEquals(2, rewe.previousMonthTransactions)
        assertTrue(
            rewe.currentMonthAmount < 0 && rewe.previousMonthAmount < 0,
            "Both months should have spending, got cur=${rewe.currentMonthAmount} prev=${rewe.previousMonthAmount}"
        )
    }

    @Test
    fun fragmentationRegression_VisaCardCommerzbankPattern() {
        // Reproduces the user's reported screenshot: Commerzbank VISA card
        // transactions where the counterparty looks like "VISA LIDL SAGT DANKE"
        // appear in both January (11 tx) and February (13 tx). The "All"
        // view must show a single Lidl entry with 24 transactions, not 11.
        val january = (1..11).map { i ->
            tx(
                id = i.toLong(),
                date = "${(i + 4).toString().padStart(2, '0')}.01.2024",
                amount = -10.0 - i,
                counterparty = "VISA LIDL SAGT DANKE",
                description = "VISA LIDL SAGT DANKE //BERLIN/DE"
            )
        }
        val february = (1..13).map { i ->
            tx(
                id = (100 + i).toLong(),
                date = "${(i + 2).toString().padStart(2, '0')}.02.2024",
                amount = -12.0 - i,
                counterparty = "VISA LIDL SAGT DANKE",
                description = "VISA LIDL SAGT DANKE //BERLIN/DE"
            )
        }

        val history = calculateMerchantHistoryFromTransactions(january + february)
        assertEquals(1, history.size, "Expected single Lidl merchant, got ${history.map { it.merchantName }}")
        assertEquals(24, history.first().totalTransactions, "Expected 11 + 13 = 24 transactions")
    }

    @Test
    fun fragmentationRegression_MixedLidlVariants() {
        // Verifies that LIDL DIENSTLEISTUNG (the case the longest-word
        // heuristic broke) merges with VISA LIDL SAGT DANKE when both
        // appear across the user's imports.
        val transactions = listOf(
            tx(1, "05.01.2024", -10.0, "VISA LIDL SAGT DANKE"),
            tx(2, "10.01.2024", -20.0, "LIDL DIENSTLEISTUNG GMBH BERLIN"),
            tx(3, "05.02.2024", -30.0, "Kartenzahlung LIDL FILIALE 4711"),
            tx(4, "10.02.2024", -40.0, "Lidl Berlin 04711 DE")
        )
        val history = calculateMerchantHistoryFromTransactions(transactions)
        assertEquals(1, history.size, "All Lidl variants should merge, got ${history.map { it.merchantName }}")
        assertEquals(4, history.first().totalTransactions)
    }

    @Test
    fun fragmentationRegression_AlnaturaProductgenossenschaft() {
        // Regression for user-reported bug: a brand whose descriptors are
        // longer than the brand itself ("ALNATURA Produktgenossenschaft")
        // used to fragment from other transactions of the same brand
        // ("ALNATURA Filiale 3"), because the picker chose the longest word.
        val transactions = listOf(
            tx(1, "05.01.2024", -10.0, "ALNATURA Produktgenossenschaft Berlin"),
            tx(2, "05.02.2024", -20.0, "ALNATURA Filiale 3"),
            tx(3, "05.03.2024", -30.0, "Alnatura Bio Markt 12345")
        )
        val grouped = MerchantGrouping.groupByMerchant(transactions)
        assertEquals(1, grouped.size, "All ALNATURA variants should collapse to one merchant, got ${grouped.keys}")
        val history = calculateMerchantHistoryFromTransactions(transactions)
        assertEquals(1, history.size)
        assertEquals(3, history.first().totalTransactions)
    }

    @Test
    fun fragmentationRegression_MultiMonthRecurringMerchants() {
        // User's reported scenario: import January, then February — both
        // months contain Lidl, Alnatura and REWE, but counterparty strings
        // drift slightly across months (different store numbers, different
        // descriptors). All variants must collapse so each brand shows up
        // as a single merchant with the combined transaction count.
        val january = listOf(
            tx(1, "05.01.2024", -25.0, "LIDL DIENSTLEISTUNG GMBH 4711"),
            tx(2, "12.01.2024", -30.0, "ALNATURA PRODUKTGENOSSENSCHAFT BERLIN"),
            tx(3, "20.01.2024", -45.0, "REWE Markt 0231")
        )
        val february = listOf(
            tx(4, "03.02.2024", -28.0, "LIDL Sagt Danke 4711"),
            tx(5, "11.02.2024", -32.0, "Alnatura Bio Markt Berlin"),
            tx(6, "21.02.2024", -47.0, "REWE BERLIN 0231")
        )
        val all = january + february

        val history = calculateMerchantHistoryFromTransactions(all)
        assertEquals(3, history.size, "Expected exactly 3 merchants, got ${history.map { it.merchantName }}")
        for (merchant in history) {
            assertEquals(
                2, merchant.totalTransactions,
                "Each recurring merchant should have 2 transactions across the two months: $merchant"
            )
        }
    }

    @Test
    fun historyCalculationRespectsCanonicalGrouping() {
        val transactions = listOf(
            tx(1, "05.01.2024", -50.0, "Lidl Sagt Danke"),
            tx(2, "05.02.2024", -60.0, "Lidl Filiale 0034"),
            tx(3, "05.03.2024", -70.0, "Kartenzahlung LIDL 9999")
        )
        val history = calculateMerchantHistoryFromTransactions(transactions)
        assertEquals(1, history.size)
        assertEquals(3, history.first().totalTransactions)
        assertEquals(-180.0, history.first().totalSpending)
    }
}
