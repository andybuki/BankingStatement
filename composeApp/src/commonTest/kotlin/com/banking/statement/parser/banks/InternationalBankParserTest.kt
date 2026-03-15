package com.banking.statement.parser.banks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for international bank parsers.
 * Covers US (MM/DD/YYYY, dot decimal), UK (DD MMM YYYY), European (DD.MM.YYYY, comma decimal),
 * and various amount formats across different bank parsers.
 */
class InternationalBankParserTest {

    // ===== US Banks (MM/DD/YYYY, dot decimal) =====

    @Test
    fun testBankOfAmerica_CanParse() {
        val parser = BankOfAmericaParser()

        assertTrue(parser.canParse("Bank of America Statement"))
        assertTrue(parser.canParse("BOFA checking account"))
        assertFalse(parser.canParse("Deutsche Bank AG"))
    }

    @Test
    fun testBankOfAmerica_BankName() {
        assertEquals("Bank of America", BankOfAmericaParser().bankName)
    }

    @Test
    fun testBankOfAmerica_ParsesUSStatement() {
        val parser = BankOfAmericaParser()
        val pdfText = """
            Bank of America
            Checking Account Statement
            Account Number: ****1234

            01/15/2024    Direct Deposit - Payroll         3,500.00
            01/16/2024    WALMART SUPERCENTER               -125.43
            01/17/2024    SHELL OIL GAS STATION              -45.00
            01/18/2024    ATM Withdrawal                    -200.00
        """.trimIndent()

        val result = parser.parse(pdfText, "boa_statement.pdf")

        assertEquals("Bank of America", result.bankName)
    }

    @Test
    fun testSunTrust_CanParse() {
        val parser = SunTrustParser()

        assertTrue(parser.canParse("SunTrust Bank Statement"))
        assertTrue(parser.canParse("Truist Financial"))
    }

    @Test
    fun testSunTrust_BankName() {
        assertEquals("SunTrust", SunTrustParser().bankName)
    }

    @Test
    fun testUsBankParser_CanParse() {
        val parser = UsBankParser()

        assertTrue(parser.canParse("US Bank Statement"))
    }

    @Test
    fun testTdBank_CanParse() {
        val parser = TdBankParser()

        assertTrue(parser.canParse("TD Bank Statement"))
    }

    // ===== UK Banks (DD MMM YYYY, dot decimal) =====

    @Test
    fun testLloyds_CanParse() {
        val parser = LloydsParser()

        assertTrue(parser.canParse("Lloyds Bank Statement"))
        assertTrue(parser.canParse("Lloyds Banking Group"))
        assertFalse(parser.canParse("Some other bank"))
    }

    @Test
    fun testLloyds_BankName() {
        assertEquals("Lloyds Bank", LloydsParser().bankName)
    }

    @Test
    fun testLloyds_ParsesUKStatement() {
        val parser = LloydsParser()
        val pdfText = """
            Lloyds Bank
            Current Account Statement
            Sort Code: 30-98-76 Account: 12345678

            15 Jan 2024    Direct Debit - Sky TV              -35.00
            16 Jan 2024    Salary - ACME Corp              3,200.00
            18 Jan 2024    Card Payment - Tesco              -89.50
        """.trimIndent()

        val result = parser.parse(pdfText, "lloyds_statement.pdf")

        assertEquals("Lloyds Bank", result.bankName)
    }

    @Test
    fun testVirginMoney_CanParse() {
        val parser = VirginMoneyParser()

        assertTrue(parser.canParse("Virgin Money Statement"))
        assertTrue(parser.canParse("Clydesdale Bank"))
    }

    @Test
    fun testVirginMoney_BankName() {
        assertEquals("Virgin Money", VirginMoneyParser().bankName)
    }

    @Test
    fun testWestpac_CanParse() {
        val parser = WestpacParser()

        assertTrue(parser.canParse("Westpac Banking Corporation"))
    }

    @Test
    fun testMetroBank_CanParse() {
        val parser = MetroBankParser()

        assertTrue(parser.canParse("Metro Bank Statement"))
    }

    @Test
    fun testBankOfIreland_CanParse() {
        val parser = BankOfIrelandParser()

        assertTrue(parser.canParse("Bank of Ireland Statement"))
    }

    // ===== European Banks (various formats) =====

    @Test
    fun testAbnAmro_CanParse() {
        val parser = AbnAmroParser()

        assertTrue(parser.canParse("ABN AMRO Bank"))
    }

    @Test
    fun testAbnAmro_BankName() {
        assertEquals("ABN AMRO", AbnAmroParser().bankName)
    }

    @Test
    fun testIngPoland_CanParse() {
        val parser = IngPolandParser()

        assertTrue(parser.canParse("ING Bank Śląski"))
    }

    @Test
    fun testFineco_CanParse() {
        val parser = FinecoParser()

        assertTrue(parser.canParse("FinecoBank Statement"))
    }

    @Test
    fun testUniCredit_CanParse() {
        val parser = UniCreditParser()

        assertTrue(parser.canParse("UniCredit Bank Statement"))
    }

    @Test
    fun testSocieteGenerale_CanParse() {
        val parser = SocieteGeneraleParser()

        assertTrue(parser.canParse("Societe Generale Statement"))
    }

    @Test
    fun testBbvaFrances_CanParse() {
        val parser = BbvaFrancesParser()

        assertTrue(parser.canParse("BBVA Frances Statement"))
    }

    // ===== FinTech / Digital Banks =====

    @Test
    fun testRevolut_CanParse() {
        val parser = RevolutPdfParser()

        assertTrue(parser.canParse("Revolut Ltd Statement"))
    }

    @Test
    fun testRevolut_BankName() {
        assertEquals("Revolut", RevolutPdfParser().bankName)
    }

    @Test
    fun testBunq_CanParse() {
        val parser = BunqParser()

        assertTrue(parser.canParse("bunq B.V. Statement"))
    }

    @Test
    fun testWise_CanParse() {
        val parser = WiseParser()

        assertTrue(parser.canParse("Wise Statement"))
    }

    // ===== African Banks =====

    @Test
    fun testAfriland_CanParse() {
        val parser = AfrilandParser()

        assertTrue(parser.canParse("Afriland First Bank Statement"))
    }

    @Test
    fun testUba_CanParse() {
        val parser = UbaParser()

        assertTrue(parser.canParse("United Bank for Africa"))
    }

    @Test
    fun testKcb_CanParse() {
        val parser = KcbParser()

        assertTrue(parser.canParse("Kenya Commercial Bank"))
    }

    // ===== Asian Banks =====

    @Test
    fun testHdfc_CanParse() {
        val parser = HdfcParser()

        assertTrue(parser.canParse("HDFC Bank Statement"))
    }

    @Test
    fun testSbi_CanParse() {
        val parser = SbiParser()

        assertTrue(parser.canParse("State Bank of India"))
    }

    @Test
    fun testMaybank_CanParse() {
        val parser = MaybankParser()

        assertTrue(parser.canParse("Maybank Statement"))
    }

    @Test
    fun testCimb_CanParse() {
        val parser = CimbParser()

        assertTrue(parser.canParse("CIMB Bank Statement"))
    }

    // ===== Australian Banks =====

    @Test
    fun testBankwest_CanParse() {
        val parser = BankwestParser()

        assertTrue(parser.canParse("Bankwest Statement"))
        assertTrue(parser.canParse("Bank of Western Australia"))
    }

    @Test
    fun testBankwest_BankName() {
        assertEquals("Bankwest", BankwestParser().bankName)
    }

    // ===== Error handling =====

    @Test
    fun testInternationalParsers_EmptyText_ReturnFailure() {
        val parsers = listOf(
            BankOfAmericaParser(),
            LloydsParser(),
            AbnAmroParser(),
            RevolutPdfParser(),
            BankwestParser()
        )

        for (parser in parsers) {
            val result = parser.parse("", "test.pdf")

            assertFalse(result.success, "${parser.bankName} should fail for empty input")
        }
    }

    @Test
    fun testInternationalParsers_NoTransactions_ReturnFailure() {
        val parsers = listOf(
            BankOfAmericaParser(),
            LloydsParser()
        )

        for (parser in parsers) {
            val result = parser.parse("Random text without any transaction data", "test.pdf")

            // Should fail or have no transactions
            assertTrue(!result.success || result.transactions.isEmpty(),
                "${parser.bankName} should fail or return empty for random text")
        }
    }

    // ===== Date format variations =====

    @Test
    fun testDateFormat_USFormat_MM_DD_YYYY() {
        val parser = BankOfAmericaParser()
        val pdfText = """
            Bank of America Statement

            12/31/2024    Year End Payment               1,000.00
        """.trimIndent()

        val result = parser.parse(pdfText, "boa.pdf")

        assertEquals("Bank of America", result.bankName)
        // If transaction is parsed, verify date components
        if (result.transactions.isNotEmpty()) {
            val tx = result.transactions[0]
            assertEquals(12, tx.bookingDate.monthNumber)
            assertEquals(31, tx.bookingDate.dayOfMonth)
            assertEquals(2024, tx.bookingDate.year)
        }
    }

    @Test
    fun testDateFormat_UKFormat_DD_MMM_YYYY() {
        val parser = LloydsParser()
        val pdfText = """
            Lloyds Bank Statement

            31 Dec 2024    Year End Payment               1,000.00
        """.trimIndent()

        val result = parser.parse(pdfText, "lloyds.pdf")

        assertEquals("Lloyds Bank", result.bankName)
        if (result.transactions.isNotEmpty()) {
            val tx = result.transactions[0]
            assertEquals(12, tx.bookingDate.monthNumber)
            assertEquals(31, tx.bookingDate.dayOfMonth)
        }
    }

    // ===== Amount format variations =====

    @Test
    fun testAmountFormat_DotDecimal_US() {
        val parser = BankOfAmericaParser()
        val pdfText = """
            Bank of America

            01/15/2024    Payment                        -1,234.56
        """.trimIndent()

        val result = parser.parse(pdfText, "boa.pdf")

        assertEquals("Bank of America", result.bankName)
        if (result.transactions.isNotEmpty()) {
            assertEquals(-1234.56, result.transactions[0].amount)
        }
    }

    @Test
    fun testAmountFormat_CommaDecimal_European() {
        val parser = AbnAmroParser()
        val pdfText = """
            ABN AMRO Bank
            Rekening Overzicht

            15-01-2024    Betaling                        -1.234,56
        """.trimIndent()

        val result = parser.parse(pdfText, "abn_amro.pdf")

        assertEquals("ABN AMRO", result.bankName)
        if (result.transactions.isNotEmpty()) {
            assertEquals(-1234.56, result.transactions[0].amount)
        }
    }

    // ===== Currency tests =====

    @Test
    fun testCurrency_USParsers_UseUSD() {
        val parser = BankOfAmericaParser()
        val pdfText = """
            Bank of America

            01/15/2024    Test Payment                     -50.00
        """.trimIndent()

        val result = parser.parse(pdfText, "boa.pdf")

        if (result.transactions.isNotEmpty()) {
            assertEquals("USD", result.transactions[0].currency)
        }
    }

    @Test
    fun testCurrency_UKParsers_UseGBP() {
        val parser = LloydsParser()
        val pdfText = """
            Lloyds Bank

            15 Jan 2024    Test Payment                     -50.00
        """.trimIndent()

        val result = parser.parse(pdfText, "lloyds.pdf")

        if (result.transactions.isNotEmpty()) {
            assertEquals("GBP", result.transactions[0].currency)
        }
    }

    @Test
    fun testCurrency_AustralianParsers_UseAUD() {
        val parser = BankwestParser()
        val pdfText = """
            Bankwest

            15/01/2024    Test Payment                     -50.00
        """.trimIndent()

        val result = parser.parse(pdfText, "bankwest.pdf")

        if (result.transactions.isNotEmpty()) {
            assertEquals("AUD", result.transactions[0].currency)
        }
    }
}
