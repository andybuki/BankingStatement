package com.bankwise.app.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for BankStatementValidator.
 * Validates the scoring-based validation system for bank statements.
 */
class BankStatementValidatorTest {

    private val validator = BankStatementValidator()

    // ===== Null/Empty input tests =====

    @Test
    fun testValidate_NullText_ReturnsInvalid() {
        val result = validator.validate(null)

        assertFalse(result.isValid)
        assertEquals(0, result.score)
        assertTrue(result.message.contains("no readable text"))
    }

    @Test
    fun testValidate_EmptyText_ReturnsInvalid() {
        val result = validator.validate("")

        assertFalse(result.isValid)
        assertEquals(0, result.score)
    }

    @Test
    fun testValidate_BlankText_ReturnsInvalid() {
        val result = validator.validate("   \n   \t   ")

        assertFalse(result.isValid)
        assertEquals(0, result.score)
    }

    // ===== IBAN detection tests (+25 points) =====

    @Test
    fun testValidate_WithIBAN_AddsPoints() {
        val text = "Account: DE89 3704 0044 0532 0130 00"

        val result = validator.validate(text)

        assertTrue(result.score >= 25)
        assertTrue(result.details.any { it.contains("IBAN") })
    }

    @Test
    fun testValidate_GermanIBAN_Detected() {
        val text = "IBAN: DE89370400440532013000"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("IBAN") })
    }

    @Test
    fun testValidate_FrenchIBAN_Detected() {
        val text = "IBAN: FR76 3000 6000 0112 3456 7890 189"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("IBAN") })
    }

    // ===== Balance keywords tests (+20 points) =====

    @Test
    fun testValidate_WithBalanceKeyword_German_Saldo() {
        val text = "Ihr aktueller Saldo beträgt 1.234,56 EUR"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Balance") })
    }

    @Test
    fun testValidate_WithBalanceKeyword_German_Kontostand() {
        val text = "Kontostand zum 31.12.2024: 5.000,00 EUR"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Balance") })
    }

    @Test
    fun testValidate_WithBalanceKeyword_English() {
        val text = "Your Account Balance is $1,234.56"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Balance") })
    }

    @Test
    fun testValidate_WithBalanceKeyword_ClosingBalance() {
        val text = "Closing Balance: 2,500.00 EUR"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Balance") })
    }

    // ===== Currency detection tests (+15 points) =====

    @Test
    fun testValidate_WithCurrency_EUR() {
        val text = "Amount: 100.00 EUR"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Currency") })
    }

    @Test
    fun testValidate_WithCurrency_EuroSymbol() {
        val text = "Betrag: 100,00 €"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Currency") })
    }

    @Test
    fun testValidate_WithCurrency_USD() {
        val text = "Amount: USD 500.00"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Currency") })
    }

    @Test
    fun testValidate_WithCurrency_DollarSymbol() {
        val text = "Balance: $1,500.00"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Currency") })
    }

    @Test
    fun testValidate_WithCurrency_GBP() {
        val text = "Total: £250.00"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Currency") })
    }

    // ===== Date detection tests (+15 points) =====

    @Test
    fun testValidate_WithDate_GermanFormat() {
        val text = "Buchungsdatum: 15.01.2024"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Dates") })
        assertTrue(result.details.any { it.contains("DD.MM.YYYY") })
    }

    @Test
    fun testValidate_WithDate_ISOFormat() {
        val text = "Date: 2024-01-15"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Dates") })
        assertTrue(result.details.any { it.contains("YYYY-MM-DD") })
    }

    @Test
    fun testValidate_WithDate_USFormat() {
        val text = "Date: 01/15/2024"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Dates") })
    }

    // ===== Transaction keywords tests (+15 points) =====

    @Test
    fun testValidate_WithTransactionKeyword_German_Buchung() {
        val text = "Buchung vom 15.01.2024"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Transaction") })
    }

    @Test
    fun testValidate_WithTransactionKeyword_German_Lastschrift() {
        val text = "SEPA Lastschrift"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Transaction") })
    }

    @Test
    fun testValidate_WithTransactionKeyword_German_Uberweisung() {
        val text = "SEPA Überweisung"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Transaction") })
    }

    @Test
    fun testValidate_WithTransactionKeyword_English_Debit() {
        val text = "Debit Card Transaction"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Transaction") })
    }

    @Test
    fun testValidate_WithTransactionKeyword_English_Transfer() {
        val text = "Bank Transfer received"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Transaction") })
    }

    // ===== Amount detection tests (+10 points) =====

    @Test
    fun testValidate_WithAmount_GermanFormat() {
        val text = "Betrag: -1.234,56"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Monetary amounts") })
    }

    @Test
    fun testValidate_WithAmount_InternationalFormat() {
        val text = "Amount: -1,234.56"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Monetary amounts") })
    }

    @Test
    fun testValidate_WithAmount_NegativeValue() {
        val text = "Payment: -500,00"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Monetary amounts") })
    }

    // ===== Bank indicator tests (+10 bonus points) =====

    @Test
    fun testValidate_WithBankIndicators_IBAN_BIC() {
        val text = "IBAN: DE12345 BIC: DEUTDEDB"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Bank indicators") })
    }

    @Test
    fun testValidate_WithBankIndicators_Sparkasse() {
        val text = "Sparkasse Frankfurt IBAN: DE12345"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Bank indicators") })
    }

    @Test
    fun testValidate_WithBankIndicators_Konto() {
        val text = "Ihr Konto bei der Bank AG"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.contains("Bank indicators") })
    }

    // ===== Threshold validation tests =====

    @Test
    fun testValidate_BelowThreshold_ReturnsInvalid() {
        // Only one indicator (IBAN = 25 points, but threshold is 50)
        val text = "DE89 3704 0044 0532 0130 00"

        val result = validator.validate(text)

        assertFalse(result.isValid)
        assertTrue(result.message.contains("does not appear to be a bank statement"))
    }

    @Test
    fun testValidate_AtThreshold_ReturnsValid() {
        // IBAN (25) + Balance keyword (20) + Currency (15) = 60 >= 50
        val text = """
            IBAN: DE89 3704 0044 0532 0130 00
            Saldo: 1.000,00 EUR
        """.trimIndent()

        val result = validator.validate(text)

        assertTrue(result.isValid)
        assertTrue(result.score >= 50)
    }

    @Test
    fun testValidate_HighScore_ReturnsValid() {
        // Full bank statement with many indicators
        val text = """
            Kontoauszug
            IBAN: DE89 3704 0044 0532 0130 00
            BIC: DEUTDEDB
            Kontostand zum 31.12.2024

            Buchungsdatum: 15.01.2024
            SEPA Überweisung
            Betrag: -1.234,56 EUR
            Neuer Saldo: 5.000,00 EUR
        """.trimIndent()

        val result = validator.validate(text)

        assertTrue(result.isValid)
        assertTrue(result.score >= 70)
        assertTrue(result.message.contains("Valid bank statement"))
    }

    // ===== Real-world bank statement patterns =====

    @Test
    fun testValidate_GermanBankStatement_Pattern() {
        val text = """
            Sparkasse
            Ihr Girokonto
            IBAN: DE89 3704 0044 0532 0130 00
            Kontoauszug Nr. 1/2024

            Datum       Buchungstext           Betrag
            15.01.2024  Lastschrift            -50,00 EUR
            16.01.2024  Gutschrift Gehalt    2.500,00 EUR

            Neuer Kontostand: 3.450,00 EUR
        """.trimIndent()

        val result = validator.validate(text)

        assertTrue(result.isValid)
        assertTrue(result.score >= 80)
    }

    @Test
    fun testValidate_EnglishBankStatement_Pattern() {
        val text = """
            Bank Statement
            Account Number: 12345678
            IBAN: GB29 NWBK 6016 1331 9268 19

            Date        Description             Amount      Balance
            2024-01-15  Direct Debit           -50.00 GBP  1,450.00
            2024-01-16  Salary Credit        2,500.00 GBP  3,950.00

            Closing Balance: 3,950.00 GBP
        """.trimIndent()

        val result = validator.validate(text)

        assertTrue(result.isValid)
    }

    // ===== Edge cases =====

    @Test
    fun testValidate_CaseInsensitiveKeywords() {
        val text = "SALDO balance KONTOSTAND"

        val result = validator.validate(text)

        // Should detect balance keywords regardless of case
        assertTrue(result.details.any { it.contains("Balance") })
    }

    @Test
    fun testValidate_RandomText_ReturnsInvalid() {
        val text = """
            Lorem ipsum dolor sit amet, consectetur adipiscing elit.
            Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.
            Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.
        """.trimIndent()

        val result = validator.validate(text)

        assertFalse(result.isValid)
        assertTrue(result.score < 50)
    }

    // ===== ValidationResult structure tests =====

    @Test
    fun testValidationResult_HasCorrectStructure() {
        val text = "IBAN: DE89370400440532013000 Saldo: 1000 EUR"

        val result = validator.validate(text)

        assertTrue(result.details.isNotEmpty())
        assertTrue(result.message.isNotEmpty())
        assertTrue(result.score >= 0)
    }

    @Test
    fun testValidationResult_DetailsShowMatchedIndicators() {
        val text = "IBAN: DE89370400440532013000"

        val result = validator.validate(text)

        assertTrue(result.details.any { it.startsWith("✓") })
    }
}
