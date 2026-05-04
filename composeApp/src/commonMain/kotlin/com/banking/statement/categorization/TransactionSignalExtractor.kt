package com.banking.statement.categorization

import com.banking.statement.parser.ParsedTransaction

/**
 * High-level transaction type inferred from bank-statement wording.
 */
enum class TransactionSignalType {
    VISA_CARD,
    CARD_PAYMENT,
    PAYPAL,
    CASH_WITHDRAWAL,
    CASH_DEPOSIT,
    SALARY,
    RENT_OR_UTILITIES,
    INVESTMENT,
    TRANSFER,
    DIRECT_DEBIT,
    BANK_FEE,
    REFUND,
    UNKNOWN
}

/**
 * Normalized signals used by the categorizer before keyword/merchant lookup.
 */
data class TransactionSignals(
    val type: TransactionSignalType,
    val effectiveMerchant: String? = null,
    val normalizedSearchText: String,
    val categoryHint: TransactionCategory? = null,
    val isSalaryLike: Boolean,
    val isRentLike: Boolean,
    val isRefundLike: Boolean,
    val isCashWithdrawal: Boolean,
    val isCashDeposit: Boolean,
    val isInvestmentLike: Boolean,
    val isTransferLike: Boolean,
    val isBankFeeLike: Boolean,
    val isTravelLike: Boolean
)

/**
 * Extracts the real merchant / transaction type from noisy bank descriptions.
 *
 * Key examples:
 * - "PayPal ... Wolt, Ihr Einkauf bei Wolt" -> effectiveMerchant = "Wolt"
 * - "PayPal ... Apple Se rvices, Ihr Einkauf bei Apple Servi ces" -> "Apple Services"
 * - "VISA LIDL SAGT DANKE NR XXXX ..." -> effectiveMerchant = "LIDL SAGT DANKE"
 * - "NETTO MARKEN-DISCOU//DUEREN/DE ... Debitk." -> effectiveMerchant = "NETTO MARKEN-DISCOU"
 * - "Lieferando.de Mastercard • Bars & Restaurants" -> merchant + RESTAURANT hint
 * - "Gehalt/Rente KARL STORZ ..." -> type = SALARY
 */
object TransactionSignalExtractor {

    private val whitespaceRegex = Regex("\\s+")
    private val visaMerchantRegex = Regex(
        pattern = "\\bvisa\\s+(.+?)(?:\\s+nr\\s+xxxx|\\s+kaufumsatz|$)",
        option = RegexOption.IGNORE_CASE
    )
    private val cardDoubleSlashMerchantRegex = Regex(
        pattern = "(?:debitkartenzahlung|kartenzahlung|lastschrift\\s+aus\\s+kartenzahlung)?\\s*([A-ZÄÖÜ0-9 .&'\\-]+?)//[A-ZÄÖÜa-zäöüß .\\-]+/DE",
        option = RegexOption.IGNORE_CASE
    )
    private val girocardMerchantRegex = Regex(
        pattern = "kartenzahlung\\s+girocard.*?\\n?\\s*([A-ZÄÖÜ0-9 .&'\\-]{3,})",
        option = RegexOption.IGNORE_CASE
    )
    private val mastercardBulletRegex = Regex(
        pattern = "(.+?)\\s+mastercard\\s*•\\s*([^\\n]+)",
        option = RegexOption.IGNORE_CASE
    )
    private val paypalPurchaseRegexes = listOf(
        Regex("i\\s*hr\\s+einkauf\\s+b\\s*ei\\s+(.+?)(?:\\s+mandat:|\\s+referenz:|/abbuchung|\\s+awv-meldepflicht|\\s+mref\\+|\\s+eref\\+|$)", RegexOption.IGNORE_CASE),
        Regex("/\\.?\\s*([^/]+?),\\s*i\\s*hr\\s+einkauf", RegexOption.IGNORE_CASE),
        Regex("svwz\\+.*?\\.\\s*([^,]+?),\\s*i\\s*hr\\s+einkauf", RegexOption.IGNORE_CASE)
    )
    private val paypalLegalSuffixRegex = Regex(
        pattern = "\\b(gmbh|ag|ab|se|s\\.?a\\.?r\\.?l\\.?|s\\.?c\\.?a\\.?|unipessoal\\s+lda|inc\\.?|ltd\\.?|bv|a/s|as|co\\.?\\s*kg)\\b",
        option = RegexOption.IGNORE_CASE
    )
    private val cardNoiseRegex = Regex(
        pattern = "\\b(nr|xxxx|kaufumsatz|arn|sagt\\s+danke|debitk\\.?|kfn|vj|pn)\\b|\\b\\d+[,.]\\d+\\b|\\b\\d{2}\\.\\d{2}\\b|\\b\\d{4,}\\b",
        option = RegexOption.IGNORE_CASE
    )

    fun extract(transaction: ParsedTransaction): TransactionSignals {
        return extract(
            description = transaction.description,
            counterpartyName = transaction.counterpartyName,
            remittanceInfo = transaction.remittanceInfo,
            rawText = transaction.rawText
        )
    }

    fun extract(
        description: String,
        counterpartyName: String? = null,
        remittanceInfo: String? = null,
        rawText: String? = null
    ): TransactionSignals {
        val rawTextCombined = listOfNotNull(
            description,
            counterpartyName,
            remittanceInfo,
            rawText
        ).joinToString(" ")
        val normalizedRaw = normalizeBrokenWords(normalizeSpaces(rawTextCombined))
        val lower = normalizedRaw.lowercase()

        val salaryLike = isSalaryLike(lower)
        val rentLike = isRentLike(lower)
        val refundLike = isRefundLike(lower)
        val cashWithdrawal = isCashWithdrawal(lower)
        val cashDeposit = isCashDeposit(lower)
        val investmentLike = isInvestmentLike(lower)
        val transferLike = isTransferLike(lower)
        val bankFeeLike = isBankFeeLike(lower)
        val travelLike = isTravelLike(lower)
        val categoryHint = extractCategoryHint(normalizedRaw)

        val type = when {
            cashWithdrawal -> TransactionSignalType.CASH_WITHDRAWAL
            cashDeposit -> TransactionSignalType.CASH_DEPOSIT
            salaryLike -> TransactionSignalType.SALARY
            refundLike -> TransactionSignalType.REFUND
            investmentLike -> TransactionSignalType.INVESTMENT
            lower.contains("paypal") -> TransactionSignalType.PAYPAL
            lower.contains("visa") -> TransactionSignalType.VISA_CARD
            lower.contains("mastercard") || lower.contains("debitkartenzahlung") ||
                lower.contains("kartenzahlung") || lower.contains("girocard") ||
                lower.contains("debitk") -> TransactionSignalType.CARD_PAYMENT
            bankFeeLike -> TransactionSignalType.BANK_FEE
            rentLike -> TransactionSignalType.RENT_OR_UTILITIES
            transferLike -> TransactionSignalType.TRANSFER
            lower.contains("lastschrift") || lower.contains("basislastschrift") || lower.contains("sdd lastschr") -> TransactionSignalType.DIRECT_DEBIT
            else -> TransactionSignalType.UNKNOWN
        }

        val effectiveMerchant = when (type) {
            TransactionSignalType.PAYPAL, TransactionSignalType.REFUND -> extractPaypalMerchant(normalizedRaw)
            TransactionSignalType.VISA_CARD -> extractVisaMerchant(normalizedRaw) ?: extractCardMerchant(normalizedRaw)
            TransactionSignalType.CARD_PAYMENT -> extractMastercardBulletMerchant(normalizedRaw) ?: extractCardMerchant(normalizedRaw)
            else -> counterpartyName?.takeIf { it.isNotBlank() }
        }?.let { cleanupMerchant(it) }
            ?.takeIf { it.length >= 2 }

        val searchText = effectiveMerchant ?: normalizedRaw

        return TransactionSignals(
            type = type,
            effectiveMerchant = effectiveMerchant,
            normalizedSearchText = searchText,
            categoryHint = categoryHint,
            isSalaryLike = salaryLike,
            isRentLike = rentLike,
            isRefundLike = refundLike,
            isCashWithdrawal = cashWithdrawal,
            isCashDeposit = cashDeposit,
            isInvestmentLike = investmentLike,
            isTransferLike = transferLike,
            isBankFeeLike = bankFeeLike,
            isTravelLike = travelLike
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

    private fun extractCardMerchant(text: String): String? {
        cardDoubleSlashMerchantRegex.find(text)?.let { match ->
            val candidate = match.groupValues.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank()) return candidate
        }

        girocardMerchantRegex.find(text)?.let { match ->
            val candidate = match.groupValues.getOrNull(1)?.trim()
            if (!candidate.isNullOrBlank()) return candidate
        }

        return null
    }

    private fun extractMastercardBulletMerchant(text: String): String? {
        val match = mastercardBulletRegex.find(text) ?: return null
        return match.groupValues.getOrNull(1)?.trim()
    }

    private fun extractCategoryHint(text: String): TransactionCategory? {
        val match = mastercardBulletRegex.find(text)
        val hint = match?.groupValues?.getOrNull(2)?.lowercase() ?: text.lowercase()

        return when {
            hint.contains("bars") || hint.contains("restaurants") || hint.contains("restaurant") -> TransactionCategory.RESTAURANT
            hint.contains("gesundheit") || hint.contains("droger") -> TransactionCategory.HEALTH
            hint.contains("freizeit") || hint.contains("entertainment") -> TransactionCategory.ENTERTAINMENT
            hint.contains("reisen") || hint.contains("travel") || hint.contains("hotel") -> TransactionCategory.TRAVEL
            hint.contains("lebensmittel") || hint.contains("supermarket") || hint.contains("grocery") -> TransactionCategory.SUPERMARKET
            else -> null
        }
    }

    private fun cleanupMerchant(value: String): String {
        return normalizeBrokenWords(value)
            .replace(cardNoiseRegex, " ")
            .replace(paypalLegalSuffixRegex, " ")
            .replace(Regex("[.,;:_]+"), " ")
            .let(::normalizeSpaces)
    }

    private fun normalizeBrokenWords(value: String): String {
        return value
            .replace(Regex("(?i)se\\s+rvices"), "services")
            .replace(Regex("(?i)servi\\s+ces"), "services")
            .replace(Regex("(?i)vertr\\s+ieb"), "vertrieb")
            .replace(Regex("(?i)vertri\\s+eb"), "vertrieb")
            .replace(Regex("(?i)koc\\s+he"), "koche")
            .replace(Regex("(?i)otr\\s+ium"), "otrium")
            .replace(Regex("(?i)al\\s+ipay"), "alipay")
            .replace(Regex("(?i)b\\s+ei"), "bei")
            .replace(Regex("(?i)i\\s+hr"), "ihr")
    }

    private fun normalizeSpaces(value: String): String {
        return value.replace(whitespaceRegex, " ").trim()
    }

    private fun isSalaryLike(lower: String): Boolean {
        return listOf(
            "gehalt/rente",
            "lohn/gehalt",
            "lohn gehalt",
            "gehalt",
            "bezuege",
            "bezüge",
            "bundeskasse"
        ).any { lower.contains(it) }
    }

    private fun isRentLike(lower: String): Boolean {
        return listOf(
            "miete",
            "kaution tiefgarage",
            "tiefgarage miete",
            "hausgeld",
            "wohngeld",
            "vorschuss hausgeld",
            "nebenkosten",
            "strom",
            "waermestrom",
            "wärmestrom",
            "stadtwerke",
            "energie",
            "e.on",
            "wasserverband",
            "gehag",
            "tilgung",
            "zinsen"
        ).any { lower.contains(it) }
    }

    private fun isRefundLike(lower: String): Boolean {
        return lower.contains("gutschrift") ||
            lower.contains("gutschriftsbeleg") ||
            lower.contains("erstattung") ||
            lower.contains("rueckerstattung") ||
            lower.contains("rückerstattung") ||
            lower.contains("abbuchung vom paypal-konto") ||
            lower.contains("familienkasse") ||
            lower.contains("ueberschuss") ||
            lower.contains("überschuss")
    }

    private fun isCashWithdrawal(lower: String): Boolean {
        return lower.contains("bargeldauszahlung") ||
            lower.contains("barg.ausz") ||
            lower.contains("bargeldausz") ||
            lower.contains("cash withdrawal") ||
            lower.contains("atm") ||
            lower.contains("geldautomat") ||
            lower.contains("automated teller")
    }

    private fun isCashDeposit(lower: String): Boolean {
        return lower.contains("bargeldeinzahlung") ||
            lower.contains("bargeldeinz") ||
            lower.contains("sb-einzahlung") ||
            lower.contains("cash deposit")
    }

    private fun isInvestmentLike(lower: String): Boolean {
        return lower.contains("revolut digital assets") ||
            lower.contains("purchase of btc") ||
            lower.contains("purchase of eth") ||
            lower.contains("binance") ||
            lower.contains("bitcoin") ||
            lower.contains("crypto") ||
            lower.contains("depot") ||
            lower.contains("wertpapier") ||
            lower.contains("kupon") ||
            lower.contains("ertraegnisgutschrift") ||
            lower.contains("erträgnisgutschrift")
    }

    private fun isTransferLike(lower: String): Boolean {
        return lower.contains("ueberweisung") ||
            lower.contains("überweisung") ||
            lower.contains("sepa-credit transfer") ||
            lower.contains("zahlung von") ||
            lower.contains("zahlungseingang") ||
            lower.contains("ausführung dauerauftrag") ||
            lower.contains("dauerauftrag") ||
            lower.contains("interne umbuchung") ||
            lower.contains("extra konto")
    }

    private fun isBankFeeLike(lower: String): Boolean {
        return lower.contains("gebühr") ||
            lower.contains("gebuehr") ||
            lower.contains("entgelt") ||
            lower.contains("depotentgelt") ||
            lower.contains("grundpreis") ||
            lower.contains("auslandseinsatzentgelt")
    }

    private fun isTravelLike(lower: String): Boolean {
        return lower.contains("hotel") ||
            lower.contains("airbnb") ||
            lower.contains("booking.com") ||
            lower.contains("lufthansa") ||
            lower.contains("easyjet") ||
            lower.contains("ryanair") ||
            lower.contains("eurowings") ||
            lower.contains("condor") ||
            lower.contains("airline") ||
            lower.contains("airways") ||
            lower.contains("airport") ||
            lower.contains("flug") ||
            lower.contains("flight")
    }
}
