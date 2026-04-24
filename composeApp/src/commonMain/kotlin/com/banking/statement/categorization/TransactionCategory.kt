package com.banking.statement.categorization

import com.banking.statement.AppStrings

/**
 * Predefined transaction categories with multilingual support
 * Consolidated to 17 essential categories
 */
enum class TransactionCategory(
    val displayName: String,
    val displayNameDe: String,
    val icon: String,
    val color: String
) {
    // Colors follow MoneyLupe category seed palette (see README.md VISUAL FOUNDATIONS).
    // Housing (includes utilities)
    RENT(
        displayName = "Rent & Utilities",
        displayNameDe = "Miete & Nebenkosten",
        icon = "home",
        color = "#0EA5E9"
    ),

    // Transportation (includes public transport, car, fuel)
    TRANSPORT(
        displayName = "Transport",
        displayNameDe = "Transport",
        icon = "directions_bus",
        color = "#F59E0B"
    ),

    // Food & Groceries
    SUPERMARKET(
        displayName = "Supermarket",
        displayNameDe = "Supermarkt",
        icon = "shopping_cart",
        color = "#22C55E"
    ),
    RESTAURANT(
        displayName = "Restaurant",
        displayNameDe = "Restaurant",
        icon = "restaurant",
        color = "#EF4444"
    ),

    // Shopping (includes online shopping)
    SHOPPING(
        displayName = "Shopping",
        displayNameDe = "Einkaufen",
        icon = "shopping_bag",
        color = "#EC4899"
    ),

    // Health
    HEALTH(
        displayName = "Health",
        displayNameDe = "Gesundheit",
        icon = "medical_services",
        color = "#14B8A6"
    ),

    // Insurance
    INSURANCE(
        displayName = "Insurance",
        displayNameDe = "Versicherung",
        icon = "security",
        color = "#6366F1"
    ),

    // Entertainment
    ENTERTAINMENT(
        displayName = "Entertainment",
        displayNameDe = "Unterhaltung",
        icon = "movie",
        color = "#A855F7"
    ),

    // Subscriptions (includes fitness, phone & internet)
    SUBSCRIPTIONS(
        displayName = "Subscriptions",
        displayNameDe = "Abonnements",
        icon = "subscriptions",
        color = "#8B5CF6"
    ),

    // Investment
    INVESTMENT(
        displayName = "Investment",
        displayNameDe = "Investition",
        icon = "trending_up",
        color = "#10B981"
    ),

    // Travel
    TRAVEL(
        displayName = "Travel",
        displayNameDe = "Reisen",
        icon = "flight",
        color = "#06B6D4"
    ),

    // Income
    SALARY(
        displayName = "Salary",
        displayNameDe = "Gehalt",
        icon = "payments",
        color = "#16A34A"
    ),
    REFUND(
        displayName = "Refund",
        displayNameDe = "Rückerstattung",
        icon = "replay",
        color = "#84CC16"
    ),

    // Transfers
    TRANSFER(
        displayName = "Transfer",
        displayNameDe = "Überweisung",
        icon = "swap_horiz",
        color = "#64748B"
    ),

    // Education
    EDUCATION(
        displayName = "Education",
        displayNameDe = "Bildung",
        icon = "school",
        color = "#F97316"
    ),

    // Taxes
    TAXES(
        displayName = "Taxes",
        displayNameDe = "Steuern",
        icon = "receipt_long",
        color = "#71717A"
    ),

    // Other/Unknown
    OTHER(
        displayName = "Other",
        displayNameDe = "Sonstiges",
        icon = "category",
        color = "#94A3B8"
    );

    /**
     * Get localized display name based on system language (legacy method)
     */
    fun getLocalizedName(useGerman: Boolean = true): String {
        return if (useGerman) displayNameDe else displayName
    }

    /**
     * Get localized display name from AppStrings
     */
    fun getLocalizedName(strings: AppStrings): String {
        return when (this) {
            RENT -> strings.categoryRent
            TRANSPORT -> strings.categoryTransport
            SUPERMARKET -> strings.categorySupermarket
            RESTAURANT -> strings.categoryRestaurant
            SHOPPING -> strings.categoryShopping
            HEALTH -> strings.categoryHealth
            INSURANCE -> strings.categoryInsurance
            ENTERTAINMENT -> strings.categoryEntertainment
            SUBSCRIPTIONS -> strings.categorySubscriptions
            INVESTMENT -> strings.categoryInvestment
            TRAVEL -> strings.categoryTravel
            SALARY -> strings.categorySalary
            REFUND -> strings.categoryRefund
            TRANSFER -> strings.categoryTransfer
            EDUCATION -> strings.categoryEducation
            TAXES -> strings.categoryTaxes
            OTHER -> strings.categoryOther
        }
    }

    companion object {
        // Reference to external keyword database (set by MainActivity)
        private var keywordDatabase: KeywordLookup? = null

        /**
         * Set the keyword database for categorization.
         * Accepts any KeywordLookup implementation (KeywordDatabase or KeywordDatabaseOptimized).
         */
        fun setKeywordDatabase(database: KeywordLookup?) {
            keywordDatabase = database
        }

        /**
         * Find the best matching category for a transaction description.
         * Uses external keyword database if available.
         */
        fun categorize(description: String, counterparty: String? = null): TransactionCategory {
            // Use external keyword database if available
            keywordDatabase?.let { db ->
                val category = db.findCategory(description, counterparty)
                if (category != null) {
                    return category
                }
            }

            // No match found
            return OTHER
        }
    }
}
