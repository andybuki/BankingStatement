package com.banking.statement.ui

import com.banking.statement.categorization.TransactionCategory

enum class TransactionSortOrder {
    DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC, NAME_ASC
}

enum class TransactionTimeFilter {
    ALL, WEEK, MONTH, YEAR, CUSTOM
}

/**
 * Display model for a transaction in the list.
 */
data class TransactionDisplay(
    val id: Long,
    val date: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val category: TransactionCategory,
    val counterparty: String?,
    val detailText: String? = null,
    val accountId: Long = 0,
    val accountName: String = "",
    // Custom category support
    val customCategoryId: Long? = null,
    val customCategoryName: String? = null,
    val customCategoryIcon: String? = null,
    val customCategoryColor: String? = null,
    // Source-document linking (PDF receipt view)
    val sourceStatementId: Long? = null,
    val sourcePdfPath: String? = null,
    val sourcePage: Int? = null,
    val sourceLineSnippet: String? = null,
    /** "x,y,w,h" fractional page coords (0..1) of the matched line, or null. */
    val sourceBbox: String? = null
) {
    /** Returns true if the transaction has a linked source PDF that can be opened. */
    val hasSourcePdf: Boolean get() = !sourcePdfPath.isNullOrBlank()
    /** Returns true if this transaction has a custom category assigned. */
    val hasCustomCategory: Boolean get() = customCategoryId != null

    /** Returns the effective display name for the category. */
    val effectiveCategoryName: String get() = customCategoryName ?: category.displayName

    /** Returns the effective icon for the category. */
    val effectiveCategoryIcon: String get() = customCategoryIcon ?: category.icon

    /** Returns the effective color for the category. */
    val effectiveCategoryColor: String get() = customCategoryColor ?: category.color

    companion object {
        /**
         * Extracts a clean display name from the counterparty and description.
         * Special handling for PayPal and other payment processors.
         */
        fun extractDisplayName(counterparty: String?, description: String): String {
            val counterpartyLower = counterparty?.lowercase() ?: ""
            val descriptionLower = description.lowercase()

            // PayPal special handling - extract merchant name
            if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
                val merchantName = extractPayPalMerchant(description)
                if (merchantName != null) {
                    return "PayPal · $merchantName"
                }
                return "PayPal"
            }

            // Use counterparty if available and meaningful
            if (!counterparty.isNullOrBlank() && counterparty.length > 2) {
                return counterparty.take(50)
            }

            // Clean up description for display
            return cleanDescriptionForDisplay(description)
        }

        /**
         * Extracts merchant name from PayPal transaction descriptions.
         * Example: "PayPal Europe S.a.r.l. ... Preply, Inc., Ihr Einkauf bei Preply, Inc."
         * Returns: "Preply, Inc."
         */
        private fun extractPayPalMerchant(description: String): String? {
            val patterns = listOf(
                // German: "Ihr Einkauf bei [Merchant]"
                Regex("""[Ii]hr [Ee]inkauf bei\s+(.+?)(?:\s*,\s*Ihr|\s*$)"""),
                // "bei [Merchant]" pattern
                Regex("""bei\s+([A-Z][^,]{2,30})"""),
                // Look for company patterns after PP reference numbers
                Regex("""/PP\.[^/]+/\.?\s*([A-Z][^,]{2,40})"""),
                // Look for merchant after long reference codes
                Regex("""\d{10,}/[^,]+,\s*([A-Z][^,]{2,40})""")
            )

            for (pattern in patterns) {
                val match = pattern.find(description)
                if (match != null) {
                    val merchant = match.groupValues[1].trim()
                        .replace(Regex("""\s+"""), " ")
                        .take(35)
                    if (merchant.length >= 3 && !merchant.all { it.isDigit() || it == '.' }) {
                        return merchant
                    }
                }
            }

            return null
        }

        /**
         * Cleans up a description for display, prioritizing readable text over numbers.
         */
        private fun cleanDescriptionForDisplay(description: String): String {
            val cleaned = description
                .replace(Regex("""^(SEPA-?|ÜBERWEISUNG|LASTSCHRIFT|KARTENZAHLUNG)\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^(Card payment|Transfer|Direct debit)\s*""", RegexOption.IGNORE_CASE), "")
                .trim()

            // If mostly numbers/codes, keep it shorter; otherwise allow longer text
            val textRatio = cleaned.count { it.isLetter() }.toFloat() / cleaned.length.coerceAtLeast(1)
            val maxLength = if (textRatio > 0.5) 70 else 50

            return if (cleaned.length <= maxLength) cleaned else cleaned.take(maxLength) + "…"
        }

        /**
         * Extracts additional detail text from remittance info or description.
         * This is shown as a secondary line in the UI.
         */
        fun extractDetailText(description: String, remittanceInfo: String?, counterparty: String?): String? {
            val counterpartyLower = counterparty?.lowercase() ?: ""
            val descriptionLower = description.lowercase()

            // For PayPal, try to extract the purchase description
            if (counterpartyLower.contains("paypal") || descriptionLower.contains("paypal")) {
                val purchaseDetail = extractPayPalPurchaseDetail(description)
                if (purchaseDetail != null) {
                    return purchaseDetail
                }
            }

            // Use remittance info if available and meaningful
            if (!remittanceInfo.isNullOrBlank() && remittanceInfo.length > 5) {
                val cleaned = cleanDetailText(remittanceInfo)
                if (cleaned != null) {
                    return cleaned
                }
            }

            // Extract second line info from description if present
            val lines = description.split(Regex("""[\n\r]+"""))
            if (lines.size > 1) {
                val secondLine = lines[1].trim()
                if (secondLine.length > 5) {
                    val cleaned = cleanDetailText(secondLine)
                    if (cleaned != null) {
                        return cleaned
                    }
                }
            }

            return null
        }

        /**
         * Cleans detail text by removing reference codes and validating content.
         * Returns null if the text is not meaningful for display.
         */
        private fun cleanDetailText(text: String): String? {
            val cleaned = text
                .replace(Regex("""Mandat:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Referenz:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Mandatsref\.?:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Gläubiger-?ID:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""Creditor-?ID:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""End-to-End-?Ref\.?:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""EREF:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""MREF:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""CRED:\s*\S+""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+"""), " ")
                .trim()

            // Check if remaining text is meaningful (>30% letters, at least 5 chars)
            if (cleaned.length < 5) return null
            val textRatio = cleaned.count { it.isLetter() }.toFloat() / cleaned.length
            if (textRatio < 0.3) return null

            return cleaned.take(60).let {
                if (cleaned.length > 60) "$it…" else it
            }
        }

        private fun extractPayPalPurchaseDetail(description: String): String? {
            // Look for "Ihr Einkauf bei" pattern and extract the full context
            val match = Regex("""[Ii]hr [Ee]inkauf bei\s+(.+)""").find(description)
            if (match != null) {
                return match.groupValues[1].take(50).let {
                    if (match.groupValues[1].length > 50) "$it…" else it
                }
            }
            return null
        }
    }
}

/**
 * Filter option for account dropdown.
 */
data class AccountFilterOption(
    val id: Long?,  // null means "All Accounts"
    val name: String
)
