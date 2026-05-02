package com.banking.statement.categorization

/**
 * Curated alias map for common merchants whose statement text differs from
 * the canonical brand name in the merchant CSV. Keys are alternate spellings
 * (lowercase, normalized form) that should resolve to the canonical merchant
 * already present in the loaded merchant database.
 *
 * Aliases are *additions* on top of the merchant CSV — they don't override
 * an existing exact match. Add a new alias when statement text consistently
 * uses a form that the CSV doesn't list (abbreviations, payment-processor
 * descriptors, common typos).
 */
object MerchantAliases {

    /**
     * Map of alias → canonical merchant name (both already in normalized form
     * — lowercase, no special chars, no extra whitespace).
     */
    val aliases: Map<String, String> = mapOf(
        // Supermarkets — German
        "rewe markt" to "rewe",
        "rewe city" to "rewe",
        "rewe to go" to "rewe",
        "edeka markt" to "edeka",
        "edeka neukauf" to "edeka",
        "lidl dienstleistung" to "lidl",
        "lidl sagt danke" to "lidl",
        "aldi sued" to "aldi",
        "aldi süd" to "aldi",
        "aldi nord" to "aldi",
        "kaufland dienstleistung" to "kaufland",
        "netto marken discount" to "netto",
        "netto deutschland" to "netto",
        "penny markt" to "penny",
        "real sb warenhaus" to "real",

        // Drugstores
        "dm drogerie" to "dm",
        "dm drogeriemarkt" to "dm",
        "rossmann drogerie" to "rossmann",
        "mueller drogerie" to "mueller",
        "müller drogerie" to "müller",

        // Bakeries / coffee
        "starbucks coffee" to "starbucks",
        "mcdonalds restaurant" to "mcdonalds",
        "mcdonald s" to "mcdonalds",
        "burger king restaurant" to "burger king",
        "kfc restaurant" to "kfc",

        // Transport
        "deutsche bahn" to "db",
        "db fernverkehr" to "db",
        "db vertrieb" to "db",
        "bvg fahrausweise" to "bvg",
        "mvv tarifgebiet" to "mvv",

        // Online / subscriptions
        "amazon mktplce" to "amazon",
        "amazon eu" to "amazon",
        "amzn mktp" to "amazon",
        "amzn digital" to "amazon",
        "netflix com" to "netflix",
        "spotify ab" to "spotify",
        "apple com bill" to "apple",
        "google one" to "google",
        "google services" to "google",

        // Mobility / fuel
        "shell deutschland" to "shell",
        "aral tankstelle" to "aral",
        "esso station" to "esso",
        "total tankstelle" to "total",

        // Travel
        "booking com" to "booking",
        "airbnb payments" to "airbnb",
        "uber eats" to "uber",
        "lyft ride" to "lyft",

        // Food delivery
        "lieferando de" to "lieferando",
        "wolt deliver" to "wolt"
    )
}
