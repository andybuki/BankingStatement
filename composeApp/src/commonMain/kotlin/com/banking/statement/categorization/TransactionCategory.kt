package com.banking.statement.categorization

/**
 * Predefined transaction categories with keywords for auto-matching
 */
enum class TransactionCategory(
    val displayName: String,
    val icon: String,
    val color: String,
    val keywords: List<String>
) {
    // Housing & Utilities
    RENT(
        displayName = "Rent",
        icon = "home",
        color = "#E57373",
        keywords = listOf(
            "miete", "rent", "wohnung", "wohnungsbau", "hausverwaltung",
            "gehag", "vonovia", "deutsche wohnen", "immobilien"
        )
    ),
    UTILITIES(
        displayName = "Utilities",
        icon = "bolt",
        color = "#FFB74D",
        keywords = listOf(
            "strom", "gas", "wasser", "stadtwerke", "vattenfall", "eon", "e.on",
            "enercity", "electricity", "heizung", "energie", "swb", "mainova"
        )
    ),

    // Transportation
    PUBLIC_TRANSPORT(
        displayName = "Public Transport",
        icon = "train",
        color = "#4FC3F7",
        keywords = listOf(
            "bvg", "berliner verkehrsbetriebe", "s-bahn", "u-bahn", "deutsche bahn",
            "db ", "mvv", "hvv", "kvb", "öpnv", "nahverkehr", "vbb", "transport"
        )
    ),
    CAR(
        displayName = "Car & Fuel",
        icon = "car",
        color = "#90A4AE",
        keywords = listOf(
            "tankstelle", "shell", "aral", "esso", "jet ", "total ", "fuel",
            "benzin", "diesel", "kfz", "auto", "werkstatt", "reifen", "adac"
        )
    ),

    // Food & Groceries
    SUPERMARKET(
        displayName = "Supermarket",
        icon = "shopping_cart",
        color = "#81C784",
        keywords = listOf(
            "rewe", "edeka", "lidl", "aldi", "penny", "netto", "kaufland",
            "real", "supermarkt", "dm-", "rossmann", "müller drogerie"
        )
    ),
    RESTAURANT(
        displayName = "Restaurant & Food",
        icon = "restaurant",
        color = "#FF8A65",
        keywords = listOf(
            "restaurant", "gaststätte", "bistro", "cafe", "coffee", "starbucks",
            "mcdonald", "burger king", "subway", "pizza", "sushi", "döner",
            "lieferando", "lieferheld", "uber eats", "wolt", "deliveroo"
        )
    ),

    // Shopping
    SHOPPING(
        displayName = "Shopping",
        icon = "shopping_bag",
        color = "#BA68C8",
        keywords = listOf(
            "amazon", "ebay", "zalando", "otto", "h&m", "zara", "mediamarkt",
            "saturn", "ikea", "möbel", "fashion", "kleidung", "electronics"
        )
    ),

    // Health & Insurance
    HEALTH(
        displayName = "Health",
        icon = "medical_services",
        color = "#F06292",
        keywords = listOf(
            "apotheke", "pharmacy", "arzt", "doctor", "krankenhaus", "hospital",
            "zahnarzt", "dentist", "optiker", "brille"
        )
    ),
    INSURANCE(
        displayName = "Insurance",
        icon = "security",
        color = "#7986CB",
        keywords = listOf(
            "versicherung", "insurance", "allianz", "axa", "huk", "ergo",
            "generali", "krankenversicherung", "haftpflicht", "kfz-versicherung"
        )
    ),

    // Entertainment & Subscriptions
    ENTERTAINMENT(
        displayName = "Entertainment",
        icon = "movie",
        color = "#9575CD",
        keywords = listOf(
            "netflix", "spotify", "disney", "amazon prime", "kino", "cinema",
            "theater", "konzert", "ticket", "eventim"
        )
    ),
    SUBSCRIPTIONS(
        displayName = "Subscriptions",
        icon = "subscriptions",
        color = "#4DB6AC",
        keywords = listOf(
            "abo", "subscription", "mitgliedschaft", "membership", "patreon",
            "youtube premium", "apple", "google one", "cloud", "icloud"
        )
    ),

    // Communication
    PHONE_INTERNET(
        displayName = "Phone & Internet",
        icon = "phone",
        color = "#4DD0E1",
        keywords = listOf(
            "telekom", "vodafone", "o2", "telefonica", "1&1", "congstar",
            "mobilfunk", "internet", "dsl", "kabel deutschland", "unity media"
        )
    ),

    // Financial
    BANK_FEES(
        displayName = "Bank Fees",
        icon = "account_balance",
        color = "#A1887F",
        keywords = listOf(
            "kontoführung", "bankgebühr", "entgelt", "gebühr", "bank fee"
        )
    ),
    INVESTMENT(
        displayName = "Investment",
        icon = "trending_up",
        color = "#AED581",
        keywords = listOf(
            "depot", "aktien", "etf", "fond", "wertpapier", "trade republic",
            "scalable", "investment", "dividende"
        )
    ),

    // Sports & Fitness
    FITNESS(
        displayName = "Fitness & Sports",
        icon = "fitness_center",
        color = "#FF7043",
        keywords = listOf(
            "fitness", "gym", "mcfit", "fitx", "urban sports", "yoga",
            "sport", "schwimmbad", "pool", "verein"
        )
    ),

    // Income
    SALARY(
        displayName = "Salary",
        icon = "payments",
        color = "#66BB6A",
        keywords = listOf(
            "gehalt", "lohn", "salary", "wage", "vergütung", "arbeitgeber"
        )
    ),
    REFUND(
        displayName = "Refund",
        icon = "replay",
        color = "#26A69A",
        keywords = listOf(
            "erstattung", "refund", "rückzahlung", "gutschrift", "storno"
        )
    ),

    // Transfers
    TRANSFER(
        displayName = "Transfer",
        icon = "swap_horiz",
        color = "#78909C",
        keywords = listOf(
            "überweisung", "transfer", "umbuchung"
        )
    ),

    // Cash
    CASH(
        displayName = "Cash Withdrawal",
        icon = "atm",
        color = "#8D6E63",
        keywords = listOf(
            "bargeld", "cash", "geldautomat", "atm", "auszahlung"
        )
    ),

    // PayPal & Payment Services
    PAYMENT_SERVICE(
        displayName = "Payment Service",
        icon = "payment",
        color = "#5C6BC0",
        keywords = listOf(
            "paypal", "klarna", "sofort", "giropay", "apple pay", "google pay"
        )
    ),

    // Uncategorized (default)
    OTHER(
        displayName = "Other",
        icon = "more_horiz",
        color = "#BDBDBD",
        keywords = emptyList()
    );

    companion object {
        /**
         * Find the best matching category for a transaction description
         */
        fun categorize(description: String, counterparty: String? = null): TransactionCategory {
            val searchText = "${description.lowercase()} ${counterparty?.lowercase() ?: ""}"

            // Find category with most keyword matches
            var bestMatch: TransactionCategory? = null
            var bestScore = 0

            for (category in entries) {
                if (category == OTHER) continue

                val score = category.keywords.count { keyword ->
                    searchText.contains(keyword.lowercase())
                }

                if (score > bestScore) {
                    bestScore = score
                    bestMatch = category
                }
            }

            return bestMatch ?: OTHER
        }
    }
}
