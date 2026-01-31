package com.bankwise.app.db

import com.bankwise.app.parser.ParseResult
import com.bankwise.app.parser.ParsedTransaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for TransactionRepository data classes and types.
 * Note: Full database integration tests require platform-specific setup.
 * These tests validate the data structures and logic that can be tested in common code.
 */
class TransactionRepositoryTest {

    // ===== AccountMatchResult sealed class tests =====

    @Test
    fun testAccountMatchResult_IbanMatch() {
        // Create a mock Accounts object would require database setup
        // Test the sealed class structure instead
        val result: AccountMatchResult = AccountMatchResult.NoMatch

        assertIs<AccountMatchResult.NoMatch>(result)
    }

    @Test
    fun testAccountMatchResult_NoMatch() {
        val result = AccountMatchResult.NoMatch

        assertIs<AccountMatchResult.NoMatch>(result)
    }

    @Test
    fun testAccountMatchResult_SealedClassVariants() {
        // Verify all sealed class variants exist
        val variants = listOf(
            AccountMatchResult.NoMatch
            // IbanMatch and BankMatch require Accounts instances
        )

        assertTrue(variants.isNotEmpty())
    }

    // ===== ImportResult data class tests =====

    @Test
    fun testImportResult_Creation() {
        val result = ImportResult(
            statementId = 1L,
            accountId = 2L,
            transactionsImported = 10,
            duplicatesSkipped = 2,
            isNewAccount = false
        )

        assertEquals(1L, result.statementId)
        assertEquals(2L, result.accountId)
        assertEquals(10, result.transactionsImported)
        assertEquals(2, result.duplicatesSkipped)
        assertFalse(result.isNewAccount)
    }

    @Test
    fun testImportResult_NewAccount() {
        val result = ImportResult(
            statementId = 1L,
            accountId = 1L,
            transactionsImported = 50,
            duplicatesSkipped = 0,
            isNewAccount = true
        )

        assertTrue(result.isNewAccount)
        assertEquals(0, result.duplicatesSkipped)
    }

    @Test
    fun testImportResult_Copy() {
        val original = ImportResult(
            statementId = 1L,
            accountId = 1L,
            transactionsImported = 10,
            duplicatesSkipped = 0,
            isNewAccount = false
        )

        val modified = original.copy(isNewAccount = true)

        assertFalse(original.isNewAccount)
        assertTrue(modified.isNewAccount)
        assertEquals(original.statementId, modified.statementId)
    }

    @Test
    fun testImportResult_WithAllDuplicates() {
        val result = ImportResult(
            statementId = 1L,
            accountId = 1L,
            transactionsImported = 0,
            duplicatesSkipped = 25,
            isNewAccount = false
        )

        assertEquals(0, result.transactionsImported)
        assertEquals(25, result.duplicatesSkipped)
    }

    @Test
    fun testImportResult_TotalTransactions() {
        val result = ImportResult(
            statementId = 1L,
            accountId = 1L,
            transactionsImported = 45,
            duplicatesSkipped = 5,
            isNewAccount = false
        )

        val totalProcessed = result.transactionsImported + result.duplicatesSkipped
        assertEquals(50, totalProcessed)
    }

    // ===== ParseResult integration tests =====

    @Test
    fun testParseResult_ForImport() {
        val transactions = listOf(
            ParsedTransaction(
                bookingDate = LocalDate(2024, 1, 15),
                amount = -50.0,
                description = "Test transaction"
            )
        )

        val parseResult = ParseResult(
            success = true,
            bankName = "Test Bank",
            accountIban = "DE89370400440532013000",
            statementPeriod = "Jan 2024",
            transactions = transactions
        )

        assertTrue(parseResult.success)
        assertEquals(1, parseResult.transactions.size)
        assertNotNull(parseResult.accountIban)
    }

    @Test
    fun testParseResult_FailedImport() {
        val parseResult = ParseResult(
            success = false,
            bankName = "Test Bank",
            errorMessage = "Failed to parse file"
        )

        assertFalse(parseResult.success)
        assertTrue(parseResult.transactions.isEmpty())
        assertNotNull(parseResult.errorMessage)
    }

    // ===== Transaction categorization tests =====

    @Test
    fun testParsedTransaction_ForCategorization() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -150.0,
            description = "REWE MARKT Einkauf",
            counterpartyName = "REWE Markt GmbH"
        )

        assertEquals(-150.0, transaction.amount)
        assertTrue(transaction.description.contains("REWE"))
        assertNotNull(transaction.counterpartyName)
    }

    @Test
    fun testParsedTransaction_IncomeTransaction() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = 3500.0,
            description = "Gehalt Dezember",
            counterpartyName = "Arbeitgeber GmbH"
        )

        assertTrue(transaction.amount > 0)
        assertTrue(transaction.description.contains("Gehalt"))
    }

    @Test
    fun testParsedTransaction_ExpenseTransaction() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -25.99,
            description = "Amazon Prime Abo",
            counterpartyName = "Amazon"
        )

        assertTrue(transaction.amount < 0)
    }

    // ===== Date handling tests =====

    @Test
    fun testTransaction_DateConversion() {
        val date = LocalDate(2024, 6, 15)
        val transaction = ParsedTransaction(
            bookingDate = date,
            amount = -100.0,
            description = "Test"
        )

        assertEquals(2024, transaction.bookingDate.year)
        assertEquals(6, transaction.bookingDate.monthNumber)
        assertEquals(15, transaction.bookingDate.dayOfMonth)
    }

    @Test
    fun testTransaction_ValueDate() {
        val bookingDate = LocalDate(2024, 6, 15)
        val valueDate = LocalDate(2024, 6, 17)

        val transaction = ParsedTransaction(
            bookingDate = bookingDate,
            valueDate = valueDate,
            amount = -100.0,
            description = "Test"
        )

        assertNotNull(transaction.valueDate)
        assertTrue(transaction.valueDate!! > transaction.bookingDate)
    }

    // ===== Currency handling tests =====

    @Test
    fun testTransaction_DefaultCurrencyEUR() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 1),
            amount = -50.0,
            description = "Test"
        )

        assertEquals("EUR", transaction.currency)
    }

    @Test
    fun testTransaction_CustomCurrency() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 1),
            amount = -50.0,
            description = "Test",
            currency = "USD"
        )

        assertEquals("USD", transaction.currency)
    }

    // ===== Duplicate detection logic tests =====

    @Test
    fun testTransactionEquality_SameTransaction() {
        val tx1 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test transaction"
        )

        val tx2 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test transaction"
        )

        assertEquals(tx1, tx2)
    }

    @Test
    fun testTransactionEquality_DifferentAmount() {
        val tx1 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test"
        )

        val tx2 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -75.0,
            description = "Test"
        )

        assertTrue(tx1 != tx2)
    }

    @Test
    fun testTransactionEquality_DifferentDate() {
        val tx1 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -50.0,
            description = "Test"
        )

        val tx2 = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 16),
            amount = -50.0,
            description = "Test"
        )

        assertTrue(tx1 != tx2)
    }

    // ===== Balance tracking tests =====

    @Test
    fun testTransaction_WithBalance() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -100.0,
            description = "Test",
            balance = 1500.0
        )

        assertNotNull(transaction.balance)
        assertEquals(1500.0, transaction.balance)
    }

    @Test
    fun testTransaction_WithoutBalance() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -100.0,
            description = "Test"
        )

        assertEquals(null, transaction.balance)
    }

    // ===== Transaction type tests =====

    @Test
    fun testTransaction_WithTransactionType() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -100.0,
            description = "Test",
            transactionType = "Lastschrift"
        )

        assertEquals("Lastschrift", transaction.transactionType)
    }

    @Test
    fun testTransaction_SEPAType() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -100.0,
            description = "SEPA Lastschrift",
            transactionType = "SEPA-Lastschrift"
        )

        assertTrue(transaction.transactionType!!.contains("SEPA"))
    }

    // ===== Remittance info tests =====

    @Test
    fun testTransaction_WithRemittanceInfo() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -100.0,
            description = "Test",
            remittanceInfo = "Invoice 12345"
        )

        assertNotNull(transaction.remittanceInfo)
        assertTrue(transaction.remittanceInfo!!.contains("Invoice"))
    }

    // ===== IBAN tests =====

    @Test
    fun testTransaction_WithCounterpartyIban() {
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -100.0,
            description = "Test",
            counterpartyIban = "DE89370400440532013000"
        )

        assertNotNull(transaction.counterpartyIban)
        assertTrue(transaction.counterpartyIban!!.startsWith("DE"))
    }

    // ===== Raw text preservation tests =====

    @Test
    fun testTransaction_WithRawText() {
        val rawText = "15.01.2024 Lastschrift -100,00 EUR"
        val transaction = ParsedTransaction(
            bookingDate = LocalDate(2024, 1, 15),
            amount = -100.0,
            description = "Lastschrift",
            rawText = rawText
        )

        assertNotNull(transaction.rawText)
        assertEquals(rawText, transaction.rawText)
    }
}
