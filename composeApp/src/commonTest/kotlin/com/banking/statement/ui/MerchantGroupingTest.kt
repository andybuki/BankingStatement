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
