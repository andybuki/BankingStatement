package com.banking.statement.categorization.ml

import com.banking.statement.parser.ParsedTransaction
import kotlin.math.abs

/**
 * Mirrors the feature pipeline in `ml/train_category_model.py` so that on-device
 * inference sees the same token stream the model was trained on.
 *
 * The output is a single space-joined string identical in shape to what
 * `build_features` emits for a row: [merchant merchant hint_text amount_text clean_desc].
 * Any drift between this file and the Python preprocessor will silently degrade
 * predictions, which is why MlFeatureExtractorTest pins golden inputs.
 */
internal object MlFeatureExtractor {

    fun buildFeatureText(transaction: ParsedTransaction): String {
        val description = transaction.description.orEmpty()
        val counterparty = transaction.counterpartyName.orEmpty()
        val combined = "$description $counterparty"
        val merchant = extractEffectiveMerchant(combined)
        val cleanDesc = cleanupText(combined, keepNoise = false)
        val hintSource = listOfNotNull(merchant, cleanDesc).joinToString(" ").trim()
        val hintText = domainHints(hintSource).joinToString(" ")
        val amountText = amountFeatures(transaction.amount, hintSource).joinToString(" ")

        return listOf(merchant, merchant, hintText, amountText, cleanDesc)
            .filterNot { it.isNullOrEmpty() }
            .joinToString(" ")
    }

    // region Preprocessing

    private val brokenWordReplacements: List<Pair<Regex, String>> = listOf(
        Regex("(?i)se\\s+rvices") to "services",
        Regex("(?i)servi\\s+ces") to "services",
        Regex("(?i)vertr\\s+ieb") to "vertrieb",
        Regex("(?i)vertri\\s+eb") to "vertrieb",
        Regex("(?i)movietainm\\s+ent") to "movietainment",
        Regex("(?i)icketing") to "ticketing",
        Regex("(?i)krankenversich\\s+erung") to "krankenversicherung",
        Regex("(?i)koc\\s+he") to "koche",
        Regex("(?i)otr\\s+ium") to "otrium",
        Regex("(?i)al\\s+ipay") to "alipay",
        Regex("(?i)b\\s+ei") to "bei",
        Regex("(?i)i\\s+hr") to "ihr"
    )

    private val arnRegex = Regex("arn\\w+", RegexOption.IGNORE_CASE)
    private val gkkaRegex = Regex("\\b\\w*gkka\\w*\\b", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex("\\b\\d{1,2}\\.\\d{1,2}(?:\\.\\d{2,4})?\\b")
    private val decimalRegex = Regex("\\b\\d+[,.]\\d+\\b")
    private val longNumberRegex = Regex("\\b\\d{3,}\\b")
    private val nonAlnumRegex = Regex("[^a-z0-9 ]+")
    private val whitespaceRegex = Regex("\\s+")

    private val bankNoiseWords: Set<String> = setOf(
        "lastschrift", "basislastschrift", "einzug", "ueberweisung", "überweisung",
        "sepa", "svwz", "mref", "eref", "kref", "mandat", "referenz", "iban", "bic",
        "konto", "girokonto", "kontoauszug", "buchungstag", "valuta", "wertstellung",
        "betrag", "saldo", "neuer", "alter", "kunden", "information", "seite", "nummer",
        "nr", "xxxx", "arn", "visa", "mastercard", "girocard", "debitk", "kaufumsatz",
        "zahlung", "bezahlung", "rechnung", "abbuchung", "gutschrift", "entgelt",
        "gebühr", "gebuehr", "kundennummer", "kartenzahlung", "sagt", "danke",
        "paypal", "europe", "cie", "pp", "uhr", "kurs", "de", "nrxxxx",
        "januar", "februar", "maerz", "märz", "april", "mai", "juni", "juli", "august",
        "september", "oktober", "november", "dezember"
    )

    private val legalSuffixes: Set<String> = setOf(
        "gmbh", "ag", "kg", "ohg", "ug", "se", "inc", "ltd", "llc", "bv", "ab", "as",
        "sarl", "sca", "co", "et"
    )

    private fun normalizeBrokenWords(text: String): String {
        var result = text
        for ((pattern, replacement) in brokenWordReplacements) {
            result = pattern.replace(result, replacement)
        }
        return result
    }

    /** Counterpart of `cleanup_text` in the Python training script. */
    fun cleanupText(text: String, keepNoise: Boolean = false): String {
        var working = normalizeBrokenWords(text).lowercase()
        working = working
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
        working = arnRegex.replace(working, " ")
        working = gkkaRegex.replace(working, " ")
        working = dateRegex.replace(working, " ")
        working = decimalRegex.replace(working, " ")
        working = longNumberRegex.replace(working, " ")
        working = nonAlnumRegex.replace(working, " ")
        var words = whitespaceRegex.split(working).filter { it.isNotEmpty() }
        if (!keepNoise) {
            words = words.filter { it !in bankNoiseWords && it !in legalSuffixes && it.length > 1 }
        }
        return words.joinToString(" ").trim()
    }

    // endregion

    // region Effective merchant extraction

    private val paypalPatterns: List<Regex> = listOf(
        Regex(
            "i\\s*hr\\s+einkauf\\s+b\\s*ei\\s+(.+?)(?:\\s+mandat:|\\s+referenz:|/abbuchung|\\s+awv-meldepflicht|\\s+mref\\+|\\s+eref\\+|$)",
            RegexOption.IGNORE_CASE
        ),
        Regex("/\\.?\\s*([^/]+?),\\s*i\\s*hr\\s+einkauf", RegexOption.IGNORE_CASE),
        Regex("svwz\\+.*?\\.\\s*([^,]+?),\\s*i\\s*hr\\s+einkauf", RegexOption.IGNORE_CASE)
    )
    private val visaPattern = Regex(
        "\\bvisa\\s+(.+?)(?:\\s+nr\\s+xxxx|\\s+kaufumsatz|$)",
        RegexOption.IGNORE_CASE
    )
    private val cardSlashPattern = Regex(
        "(?:debitkartenzahlung|kartenzahlung|lastschrift\\s+aus\\s+kartenzahlung)?\\s*([A-ZÄÖÜ0-9 .&'\\-]+?)//[A-ZÄÖÜa-zäöüß .\\-]+/DE",
        RegexOption.IGNORE_CASE
    )

    /**
     * Mirrors `extract_effective_merchant` exactly: returns the cleaned match
     * for the first regex that matches, even if the cleaned string is empty.
     * Returns null only when no regex matches. Callers downstream filter empty
     * strings out of the joined feature text.
     */
    fun extractEffectiveMerchant(text: String): String? {
        val lower = text.lowercase()
        if ("paypal" in lower) {
            for (pattern in paypalPatterns) {
                val match = pattern.find(text) ?: continue
                return cleanupText(match.groupValues[1], keepNoise = false)
            }
        }
        visaPattern.find(text)?.let { match ->
            return cleanupText(match.groupValues[1], keepNoise = false)
        }
        cardSlashPattern.find(text)?.let { match ->
            return cleanupText(match.groupValues[1], keepNoise = false)
        }
        return null
    }

    // endregion

    // region Domain hints

    private val domainHintRules: List<Pair<String, List<String>>> = listOf(
        "hint_transport" to listOf("flix", "db vertrieb", "deutsche bahn", "bvg", "bahn", "logpay"),
        "hint_supermarket" to listOf("flaschenpost", "rewe", "lidl", "aldi", "edeka", "kaufland", "netto", "penny", "alnatura", "bio company", "reformhaus", "go asia"),
        "hint_restaurant" to listOf("backerei", "baeckerei", "bakery", "coffee", "cafe", "bistro", "gastro", "food", "drei koche", "sironi", "burger", "pizza", "sushi", "takeaway", "zeit fuer brot", "kame", "dashi", "ice cr"),
        "hint_shopping" to listOf("dm drogerie", "drogerie", "rossmann", "muji", "bauhaus", "ikea", "ebay", "amazon"),
        "hint_subscription" to listOf("apple services", "youtube premium", "google one", "spotify", "netflix", "lagoa yoga"),
        "hint_entertainment" to listOf("cinemaxx", "movietainment", "ticket messe", "fienta", "myjump", "therme"),
        "hint_travel" to listOf("hotel", "booking", "airbnb", "flight", "flug", "lufthansa", "easyjet", "ryanair"),
        "hint_transfer" to listOf("atm", "geldautomat", "abhebung", "auszahlung", "money transfer", "klarna"),
        "hint_rent" to listOf("miete", "hausverwaltung", "strom", "telekom", "enstroga", "gehag", "rundfunk"),
        "hint_insurance" to listOf("versicherung", "krankenversicherung", "generali", "astra"),
        "hint_salary" to listOf("gehalt", "rente", "bundeskasse", "bezuege")
    )

    fun domainHints(text: String): List<String> {
        val lowered = text.lowercase()
        return domainHintRules.mapNotNull { (hint, patterns) ->
            if (patterns.any { it in lowered }) hint else null
        }
    }

    // endregion

    // region Amount features

    private val restaurantWords = listOf(
        "restaurant", "gastro", "food", "coffee", "cafe", "bistro",
        "backerei", "baeckerei", "bakery"
    )
    private val travelWords = listOf(
        "hotel", "booking", "airbnb", "flight", "flug",
        "lufthansa", "easyjet", "ryanair"
    )
    private val groceryWords = listOf(
        "aldi", "lidl", "rewe", "edeka", "kaufland", "netto", "penny",
        "flaschenpost", "reformhaus", "go asia"
    )
    private val rentWords = listOf(
        "strom", "telekom", "miete", "gehag", "enstroga", "rundfunk", "hausverwaltung"
    )

    fun amountFeatures(amount: Double, text: String): List<String> {
        val absAmount = abs(amount)
        val lowered = text.lowercase()
        val features = mutableListOf<String>()

        features += when {
            amount > 0 -> "amount_income"
            amount < 0 -> "amount_expense"
            else -> "amount_zero"
        }

        features += when {
            absAmount < 5 -> "amount_micro"
            absAmount < 15 -> "amount_small"
            absAmount < 35 -> "amount_meal_range"
            absAmount < 100 -> "amount_medium"
            absAmount < 500 -> "amount_large"
            else -> "amount_very_large"
        }

        if (restaurantWords.any { it in lowered }) {
            if (absAmount in 5.0..80.0) {
                features += "amount_restaurant_plausible"
            } else if (absAmount > 100) {
                features += "amount_restaurant_high"
            }
        }
        if (travelWords.any { it in lowered }) {
            if (absAmount >= 80) features += "amount_travel_plausible"
        }
        if (groceryWords.any { it in lowered }) {
            if (absAmount in 1.0..250.0) features += "amount_grocery_plausible"
        }
        if (rentWords.any { it in lowered }) {
            if (absAmount >= 20) features += "amount_rent_utilities_plausible"
        }

        return features
    }

    // endregion
}
