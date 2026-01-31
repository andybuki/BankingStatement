package com.bankwise.app.categorization

import com.bankwise.app.AppStrings

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
    // Housing (includes utilities)
    RENT(
        displayName = "Rent & Utilities",
        displayNameDe = "Miete & Nebenkosten",
        icon = "home",
        color = "#E57373"
    ),

    // Transportation (includes public transport, car, fuel)
    TRANSPORT(
        displayName = "Transport",
        displayNameDe = "Transport",
        icon = "directions_bus",
        color = "#2196F3"
    ),

    // Food & Groceries
    SUPERMARKET(
        displayName = "Supermarket",
        displayNameDe = "Supermarkt",
        icon = "shopping_cart",
        color = "#81C784"
    ),
    RESTAURANT(
        displayName = "Restaurant",
        displayNameDe = "Restaurant",
        icon = "restaurant",
        color = "#FF8A65"
    ),

    // Shopping (includes online shopping)
    SHOPPING(
        displayName = "Shopping",
        displayNameDe = "Einkaufen",
        icon = "shopping_bag",
        color = "#BA68C8"
    ),

    // Health
    HEALTH(
        displayName = "Health",
        displayNameDe = "Gesundheit",
        icon = "medical_services",
        color = "#F06292"
    ),

    // Insurance
    INSURANCE(
        displayName = "Insurance",
        displayNameDe = "Versicherung",
        icon = "security",
        color = "#7986CB"
    ),

    // Entertainment
    ENTERTAINMENT(
        displayName = "Entertainment",
        displayNameDe = "Unterhaltung",
        icon = "movie",
        color = "#9575CD"
    ),

    // Subscriptions (includes fitness, phone & internet)
    SUBSCRIPTIONS(
        displayName = "Subscriptions",
        displayNameDe = "Abonnements",
        icon = "subscriptions",
        color = "#4DB6AC"
    ),

    // Investment
    INVESTMENT(
        displayName = "Investment",
        displayNameDe = "Investition",
        icon = "trending_up",
        color = "#AED581"
    ),

    // Travel
    TRAVEL(
        displayName = "Travel",
        displayNameDe = "Reisen",
        icon = "flight",
        color = "#29B6F6"
    ),

    // Income
    SALARY(
        displayName = "Salary",
        displayNameDe = "Gehalt",
        icon = "payments",
        color = "#66BB6A"
    ),
    REFUND(
        displayName = "Refund",
        displayNameDe = "Rückerstattung",
        icon = "replay",
        color = "#26A69A"
    ),

    // Transfers
    TRANSFER(
        displayName = "Transfer",
        displayNameDe = "Überweisung",
        icon = "swap_horiz",
        color = "#78909C"
    ),

    // Education
    EDUCATION(
        displayName = "Education",
        displayNameDe = "Bildung",
        icon = "school",
        color = "#FFD54F"
    ),

    // Taxes
    TAXES(
        displayName = "Taxes",
        displayNameDe = "Steuern",
        icon = "receipt_long",
        color = "#795548"
    ),

    // Other/Unknown
    OTHER(
        displayName = "Other",
        displayNameDe = "Sonstiges",
        icon = "category",
        color = "#B0BEC5"
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
        private var keywordDatabase: KeywordDatabase? = null

        /**
         * Set the keyword database for categorization
         */
        fun setKeywordDatabase(database: KeywordDatabase?) {
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
