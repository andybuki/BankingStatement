package com.banking.statement.categorization

/**
 * Predefined transaction categories with multilingual support
 */
enum class TransactionCategory(
    val displayName: String,
    val displayNameDe: String,  // German translation
    val icon: String,
    val color: String
) {
    // Housing & Utilities
    RENT(
        displayName = "Rent",
        displayNameDe = "Miete",
        icon = "home",
        color = "#E57373"
    ),
    UTILITIES(
        displayName = "Utilities",
        displayNameDe = "Nebenkosten",
        icon = "bolt",
        color = "#FFB74D"
    ),

    // Transportation
    PUBLIC_TRANSPORT(
        displayName = "Public Transport",
        displayNameDe = "Öffentliche Verkehrsmittel",
        icon = "train",
        color = "#4FC3F7"
    ),
    CAR(
        displayName = "Car & Fuel",
        displayNameDe = "Auto & Kraftstoff",
        icon = "car",
        color = "#90A4AE"
    ),

    // Food & Groceries
    SUPERMARKET(
        displayName = "Supermarket",
        displayNameDe = "Supermarkt",
        icon = "shopping_cart",
        color = "#81C784"
    ),
    RESTAURANT(
        displayName = "Restaurant & Food",
        displayNameDe = "Restaurant & Essen",
        icon = "restaurant",
        color = "#FF8A65"
    ),

    // Shopping
    SHOPPING(
        displayName = "Shopping",
        displayNameDe = "Einkaufen",
        icon = "shopping_bag",
        color = "#BA68C8"
    ),

    // Health & Insurance
    HEALTH(
        displayName = "Health",
        displayNameDe = "Gesundheit",
        icon = "medical_services",
        color = "#F06292"
    ),
    INSURANCE(
        displayName = "Insurance",
        displayNameDe = "Versicherung",
        icon = "security",
        color = "#7986CB"
    ),

    // Entertainment & Subscriptions
    ENTERTAINMENT(
        displayName = "Entertainment",
        displayNameDe = "Unterhaltung",
        icon = "movie",
        color = "#9575CD"
    ),
    SUBSCRIPTIONS(
        displayName = "Subscriptions",
        displayNameDe = "Abonnements",
        icon = "subscriptions",
        color = "#4DB6AC"
    ),

    // Communication
    PHONE_INTERNET(
        displayName = "Phone & Internet",
        displayNameDe = "Telefon & Internet",
        icon = "phone",
        color = "#4DD0E1"
    ),

    // Financial
    BANK_FEES(
        displayName = "Bank Fees",
        displayNameDe = "Bankgebühren",
        icon = "account_balance",
        color = "#A1887F"
    ),
    INVESTMENT(
        displayName = "Investment",
        displayNameDe = "Investition",
        icon = "trending_up",
        color = "#AED581"
    ),

    // Sports & Fitness
    FITNESS(
        displayName = "Fitness & Sports",
        displayNameDe = "Fitness & Sport",
        icon = "fitness_center",
        color = "#FF7043"
    ),

    // Travel & Accommodation
    TRAVEL(
        displayName = "Travel",
        icon = "flight",
        color = "#29B6F6"
    ),

    // Income
    SALARY(
        displayName = "Income",
        icon = "payments",
        color = "#66BB6A"
    ),
    REFUND(
        displayName = "Refund",
        icon = "replay",
        color = "#26A69A"
    ),

    // Transfers
    TRANSFER(
        displayName = "Transfer",
        icon = "swap_horiz",
        color = "#78909C"
    ),

    // Cash
    CASH(
        displayName = "Cash Withdrawal",
        icon = "atm",
        color = "#8D6E63"
    ),

    // PayPal & Payment Services
    PAYMENT_SERVICE(
        displayName = "Payment Service",
        icon = "payment",
        color = "#5C6BC0"
    ),

    // Uncategorized (default)
    OTHER(
        displayName = "Other",
        icon = "more_horiz",
        color = "#BDBDBD"
    );

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
