package com.banking.statement.categorization

/**
 * Predefined transaction categories.
 * Keywords are now loaded from external CSV files for easier maintenance and localization.
 */
enum class TransactionCategory(
    val displayName: String,
    val icon: String,
    val color: String
) {
    // Housing & Utilities
    RENT(
        displayName = "Rent",
        icon = "home",
        color = "#E57373"
    ),
    UTILITIES(
        displayName = "Utilities",
        icon = "bolt",
        color = "#FFB74D"
    ),

    // Transportation
    PUBLIC_TRANSPORT(
        displayName = "Public Transport",
        icon = "train",
        color = "#4FC3F7"
    ),
    CAR(
        displayName = "Car & Fuel",
        icon = "car",
        color = "#90A4AE"
    ),

    // Food & Groceries
    SUPERMARKET(
        displayName = "Supermarket",
        icon = "shopping_cart",
        color = "#81C784"
    ),
    RESTAURANT(
        displayName = "Restaurant & Food",
        icon = "restaurant",
        color = "#FF8A65"
    ),

    // Shopping
    SHOPPING(
        displayName = "Shopping",
        icon = "shopping_bag",
        color = "#BA68C8"
    ),

    // Health & Insurance
    HEALTH(
        displayName = "Health",
        icon = "medical_services",
        color = "#F06292"
    ),
    INSURANCE(
        displayName = "Insurance",
        icon = "security",
        color = "#7986CB"
    ),

    // Entertainment & Subscriptions
    ENTERTAINMENT(
        displayName = "Entertainment",
        icon = "movie",
        color = "#9575CD"
    ),
    SUBSCRIPTIONS(
        displayName = "Subscriptions",
        icon = "subscriptions",
        color = "#4DB6AC"
    ),

    // Communication
    PHONE_INTERNET(
        displayName = "Phone & Internet",
        icon = "phone",
        color = "#4DD0E1"
    ),

    // Financial
    BANK_FEES(
        displayName = "Bank Fees",
        icon = "account_balance",
        color = "#A1887F"
    ),
    INVESTMENT(
        displayName = "Investment",
        icon = "trending_up",
        color = "#AED581"
    ),

    // Sports & Fitness
    FITNESS(
        displayName = "Fitness & Sports",
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
