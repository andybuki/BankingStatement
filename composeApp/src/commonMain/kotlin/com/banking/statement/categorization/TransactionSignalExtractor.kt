package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction

/**
 * High-level transaction type inferred from bank-statement wording.
 */
enum class TransactionSignalType {
    VISA_CARD,
    PAYPAL,
    CASH_WITHDRAWAL,
    SALARY,
    RENT_OR_UTILITIES,
    TRANSFER,
    DIRECT_DEBIT,
    UNKNOWN
}

/**
 * Normalized signals used by the categorizer before keyword/merchant lookup.
 */
data class TransactionSignals(
    val type: TransactionSignalType,
    val effectiveMerchant: String? = null,
    val normalizedSearchText: String,
    val isSalaryLike: Boolean,
    val isRentLike: Boolean,
    val isCashWithdrawal: Boolean,
    val isTransferLike: Boolean
)

/**
 * Extracts the real merchant / transaction type from noisy bank descriptions.
 *
 * Key examples:
 * - "PayPal ... Wolt, Ihr Einkauf bei Wolt" -> effectiveMerchant = "Wolt"
 * - "VISA LIDL SAGT DANKE NR XXXX ..." -> effectiveMerchant = "LIDL SAGT DANKE"
 * - "Gehalt/Rente KARL STORZ ..." -> type = SALARY
 */
object TransactionSignalExtractor {

    private val whitespaceRegex = Regex("\\s+")
    private val visaMerchantRegex = Regex(
        pattern = "\\bvisa\\s+(.+?)(?:\\s+nr\\s+xxxx|\\s+kaufumsatz|$)",
        option = RegexOption.IGNORE_CASE
    )
    private val paypalPurchaseRegexes = listOf(
        Regex("ihr\\s+einkauf\\s+bei\\s+(.+?)(?:\\s+mandat:|\\s+referenz:|/abbuchung|$)", RegexOption.IGNORE_CASE),
        Regex("/\\.?\\s*([^/]+?),\\s*ihr\\s+einkauf", RegexOption.IGNORE_CASE)
    )
    private val paypalLegalSuffixRegex = Regex(
        pattern = "\\b(gmbh|ag|se|s\\.?a\\.?r\\.?l\\.?|s\\.?c\\.?a\\.?|unipessoal\\s+lda|inc\\.?|ltd\\.?|bv)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val cardNoiseRegex = Regex(
        pattern = "\\b(nr|xxxx|kaufumsatz|arn|sagt\\s+danke)\\b|\\b\\d+[,.]\\d+\\b|\\b\\d{2}\\.\\d{2}\\b|\\b\\d{4,}\\b",
        option = RegexOption.IGNORE_CASE
    )

    fun extract(transaction: ParsedTransaction): TransactionSignals {
        val rawText = listOfNotNull(
            transaction.description,
            transaction.counterpartyName,
            transaction.remittanceInfo,
            transaction.rawText
        ).joinToString(" ")
        val normalizedRaw = normalizeSpaces(rawText)
        val lower = normalizedRaw.lowercase()

        val salaryLike = isSalaryLike(lower)
        val rentLike = isRentLike(lower)
        val cashWithdrawal = isCashWithdrawal(lower)
        val transferLike = isTransferLike(lower)

        val type = when {
            cashWithdrawal -> TransactionSignalType.CASH_WITHDRAWAL
            salaryLike -> TransactionSignalType.SALARY
            lower.contains("paypal") -> TransactionSignalType.PAYPAL
            lower.contains("visa") || lower.contains("kaufumsatz") || lower.contains("kartenzahlung") -> TransactionSignalType.VISA_CARD
            rentLike -> TransactionSignalType.RENT_OR_UTILITIES
            transferLike -> TransactionSignalType.TRANSFER
            lower.contains("lastschrift") -> TransactionSignalType.DIRECT_DEBIT
            else -> TransactionSignalType.UNKNOWN
        }

        val effectiveMerchant = when (type) {
            TransactionSignalType.PAYPAL -> extractPaypalMerchant(normalizedRaw)
            TransactionSignalType.VISA_CARD -> extractVisaMerchant(normalizedRaw)
            else -> transaction.counterpartyName?.takeIf { it.isNotBlank() }
        }?.let { cleanupMerchant(it) }
            ?.takeIf { it.length >= 2 }

        val searchText = effectiveMerchant ?: normalizedRaw

        return TransactionSignals(
            type = type,
            effectiveMerchant = effectiveMerchant,
            normalizedSearchText = searchText,
            isSalaryLike = salaryLike,
            isRentLike = rentLike,
            isCashWithdrawal = cashWithdrawal,
            isTransferLike = transferLike
        )
    }

    private fun extractPaypalMerchant(text: String): String? {
        for (regex in paypalPurchaseRegexes) {
            val match = regex.find(text) ?: continue
            val candidate = match.groupValues.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank()) return candidate
        }
        return null
    }

    private fun extractVisaMerchant(text: String): String? {
        val match = visaMerchantRegex.find(text) ?: return null
        return match.groupValues.getOrNull(1)?.trim()
    }

    private fun cleanupMerchant(value: String): String {
        return value
            .replace(cardNoiseRegex, " ")
            .replace(paypalLegalSuffixRegex, " ")
            .replace(Regex("[,;:_]+"), " ")
            .let(::normalizeSpaces)
    }

    private fun normalizeSpaces(value: String): String {
        return value.replace(whitespaceRegex, " ").trim()
    }

    private fun isSalaryLike(lower: String): Boolean {
        return listOf(
            "gehalt/rente",
            "lohn/gehalt",
            "gehalt",
            "bezuege",
            "bezüge",
            "bundeskasse"
        ).any { lower.contains(it) }
    }

    private fun isRentLike(lower: String): Boolean {
        return listOf(
            "miete",
            "hausgeld",
            "wohngeld",
            "vorschuss hausgeld",
            "nebenkosten",
            "strom",
            "waermestrom",
            "wärmestrom",
            "gehag"
        ).any { lower.contains(it) }
    }

    private fun isCashWithdrawal(lower: String): Boolean {
        return lower.contains("bargeldauszahlung") || lower.contains("cash withdrawal")
    }

    private fun isTransferLike(lower: String): Boolean {
        return lower.contains("ueberweisung") ||
            lower.contains("überweisung") ||
            lower.contains("extra konto") ||
            lower.contains("dauerauftrag")
    }
}
