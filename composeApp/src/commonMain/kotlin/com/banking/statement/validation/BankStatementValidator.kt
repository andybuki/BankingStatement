package com.banking.statement.validation

class BankStatementValidator {

    companion object {
        private const val THRESHOLD_SCORE = 50

        // IBAN patterns for different countries
        private val IBAN_PATTERN = Regex("[A-Z]{2}\\d{2}\\s?(?:\\d{4}\\s?){4,7}\\d{0,2}")

        // German date format: DD.MM.YYYY
        private val DATE_PATTERN_DE = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")

        // International date formats
        private val DATE_PATTERN_ISO = Regex("\\d{4}-\\d{2}-\\d{2}")
        private val DATE_PATTERN_US = Regex("\\d{2}/\\d{2}/\\d{4}")

        // Amount patterns (German: 1.234,56 or International: 1,234.56)
        private val AMOUNT_PATTERN_DE = Regex("-?\\d{1,3}(?:\\.\\d{3})*,\\d{2}")
        private val AMOUNT_PATTERN_INT = Regex("-?\\d{1,3}(?:,\\d{3})*\\.\\d{2}")

        // Currency indicators
        private val CURRENCY_KEYWORDS = listOf(
            "EUR", "Euro", "€",
            "USD", "Dollar", "$",
            "GBP", "£", "CHF"
        )

        // Bank statement keywords in multiple languages
        private val BALANCE_KEYWORDS = listOf(
            // German
            "Saldo", "Kontostand", "Guthaben", "Haben", "Neuer Saldo", "Alter Saldo",
            "Anfangssaldo", "Endsaldo", "Abschlusssaldo", "Vorläufiger Saldo",
            // English
            "Balance", "Account Balance", "Available Balance", "Opening Balance", "Closing Balance",
            // French
            "Solde",
            // Spanish
            "Saldo disponible"
        )

        private val TRANSACTION_KEYWORDS = listOf(
            // German - expanded
            "Buchung", "Valuta", "Lastschrift", "Gutschrift", "Überweisung",
            "Verwendungszweck", "Betrag", "Auszug", "Kontoauszug",
            "Kartenzahlung", "Bargeldein", "Bargeldaus", "Dauerauftrag",
            "SEPA", "Einzahlung", "Auszahlung", "Entgelt", "Abschluss",
            "Zinsen", "Gehalt", "Lohn", "Mandat", "Referenz", "Einreicher",
            "Kundenreferenz", "SDD Lastschr", "Abrechnung", "Rechnungsabschluss",
            // English
            "Transaction", "Debit", "Credit", "Transfer", "Payment",
            "Statement", "Description", "Amount", "Date",
            // French
            "Virement", "Prélèvement",
            // Spanish
            "Transferencia", "Movimiento"
        )

        private val BANK_INDICATORS = listOf(
            "IBAN", "BIC", "SWIFT", "BLZ",
            "Konto", "Account", "Compte", "Cuenta",
            "Kontonummer", "Bankleitzahl", "Girokonto",
            "Bank AG", "Sparkasse", "Volksbank", "Raiffeisenbank"
        )
    }

    fun validate(text: String?): ValidationResult {
        if (text.isNullOrBlank()) {
            return ValidationResult(
                isValid = false,
                score = 0,
                message = "PDF contains no readable text. It may be a scanned document.",
                details = listOf("No text extracted from PDF")
            )
        }

        val details = mutableListOf<String>()
        var score = 0

        // Check for IBAN (+25 points)
        if (IBAN_PATTERN.containsMatchIn(text)) {
            score += 25
            val matches = IBAN_PATTERN.findAll(text).take(2).map { it.value }.toList()
            details.add("✓ IBAN found: ${matches.joinToString(", ")}")
        }

        // Check for balance keywords (+20 points)
        val foundBalanceKeywords = BALANCE_KEYWORDS.filter { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
        if (foundBalanceKeywords.isNotEmpty()) {
            score += 20
            details.add("✓ Balance keywords: ${foundBalanceKeywords.take(3).joinToString(", ")}")
        }

        // Check for currency (+15 points)
        val foundCurrencies = CURRENCY_KEYWORDS.filter { currency ->
            text.contains(currency, ignoreCase = false)
        }
        if (foundCurrencies.isNotEmpty()) {
            score += 15
            details.add("✓ Currency found: ${foundCurrencies.take(2).joinToString(", ")}")
        }

        // Check for dates (+15 points)
        val hasGermanDate = DATE_PATTERN_DE.containsMatchIn(text)
        val hasIsoDate = DATE_PATTERN_ISO.containsMatchIn(text)
        val hasUsDate = DATE_PATTERN_US.containsMatchIn(text)
        if (hasGermanDate || hasIsoDate || hasUsDate) {
            score += 15
            val dateType = when {
                hasGermanDate -> "DD.MM.YYYY"
                hasIsoDate -> "YYYY-MM-DD"
                else -> "MM/DD/YYYY"
            }
            details.add("✓ Dates found ($dateType format)")
        }

        // Check for transaction keywords (+15 points)
        val foundTransactionKeywords = TRANSACTION_KEYWORDS.filter { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
        if (foundTransactionKeywords.isNotEmpty()) {
            score += 15
            details.add("✓ Transaction keywords: ${foundTransactionKeywords.take(3).joinToString(", ")}")
        }

        // Check for amounts (+10 points)
        val hasGermanAmount = AMOUNT_PATTERN_DE.containsMatchIn(text)
        val hasIntAmount = AMOUNT_PATTERN_INT.containsMatchIn(text)
        if (hasGermanAmount || hasIntAmount) {
            score += 10
            details.add("✓ Monetary amounts found")
        }

        // Check for bank indicators (+10 points bonus)
        val foundBankIndicators = BANK_INDICATORS.filter { indicator ->
            text.contains(indicator, ignoreCase = true)
        }
        if (foundBankIndicators.size >= 2) {
            score += 10
            details.add("✓ Bank indicators: ${foundBankIndicators.take(3).joinToString(", ")}")
        }

        val isValid = score >= THRESHOLD_SCORE
        val message = if (isValid) {
            "Valid bank statement detected (Score: $score/100)"
        } else {
            "This does not appear to be a bank statement (Score: $score/$THRESHOLD_SCORE required)"
        }

        return ValidationResult(
            isValid = isValid,
            score = score,
            message = message,
            details = details
        )
    }
}
