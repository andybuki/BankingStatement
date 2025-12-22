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
            "gehag", "vonovia", "deutsche wohnen", "immobilien",
            "nettomiete", "kaltmiete", "warmmiete",
            "nebenkosten", "betriebskosten", "heizkosten", "mietkaution", "kaution",
            "courtage", "leg immobilien", "grand city properties",
            "covivio", "bonovia", "hausgeld", "hausverwaltung berlin",
            "leg immobilien / leg wohnen", "vivawest", "grand city properties", "covivio",
            "adler real estate", "saga", "gewobag", "degewo", "howoge",
            "stadt und land", "gesobau", "gewofag", "wbm", "gewoba",
            "wiro", "abg frankfurt holding"
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
            "db ", "mvv", "hvv", "kvb", "öpnv", "nahverkehr", "vbb", "transport", "bus",
            "zug", "fernverkehr","metro", "avv","rmv","vgn","ivb","svv","carsharing","flixbus",
            "flixtrain","bike-and-ride","kvb-nextbike","uber","bolt"

        )
    ),
    CAR(
        displayName = "Car & Fuel",
        icon = "car",
        color = "#90A4AE",
        keywords = listOf(
            "tankstelle","shell", "aral", "esso", "jet", "total", "fuel", "benzin",
            "diesel", "kfz", "auto", "werkstatt", "reifen", "adac", "raststätte",
            "totalenergies", "startankstelle", "agip", "eni", "bfttankstelle",
            "oktan", "super", "superplus", "e10", "bleifrei", "kraftstoff",
            "sprit", "biodiesel", "autogas", "cng", "erdgastankstelle",
            "stromtankstelle", "ladesäule", "schnelllader", "e-autoladen",
            "tüv", "dekra", "kfz-steuer",
            "kfz-versicherung", "vollkasko", "teilkasko", "haftpflicht", "kfz-werkstatt",
            "meisterwerkstatt", "freiewerkstatt", "vertragshändler", "karosseriebau",
            "lackiererei", "felgen", "sommerreifen", "winterreifen", "ganzjahresreifen",
            "reifenservice", "reifenwechsel", "spurvermessung", "ölwechsel",
            "inspektion", "kundendienst", "wartung", "kfz-service", "panne",
            "abschleppdienst", "adacplusmitgliedschaft", "schutzbrief", "notruf",
            "rastplatz", "parkplatz", "parkhaus", "tiefgarage", "tanksäule",
            "zapfsäule", "vignetten", "maut"
        )
    ),

    // Food & Groceries
    SUPERMARKET(
        displayName = "Supermarket",
        icon = "shopping_cart",
        color = "#81C784",
        keywords = listOf(
            "rewe", "edeka", "lidl", "aldi", "penny", "netto", "kaufland",
            "real", "supermarkt", "dm-", "rossmann", "müller drogerie","norma",
            "alnatura","nah und gut","ledo","nah & gut","denns bioMarkt"
        )
    ),
    RESTAURANT(
        displayName = "Restaurant & Food",
        icon = "restaurant",
        color = "#FF8A65",
        keywords = listOf(
            "restaurant", "gaststätte", "bistro", "cafe", "coffee", "starbucks",
            "mcdonald", "burger king", "subway", "pizza", "sushi", "döner",
            "lieferando", "lieferheld", "uber eats", "wolt", "deliveroo","pizzeria",
            "ristorante","taverna","bäckerei","baeckerei","café","cocktailbar","backshop",
            "McDonald's","burger","imbiss","grill","gasthaus","gasthof","steakhouse",
            "brauhaus","gaststaette","backhaus","trattoria","frühstück","fruehstueck",
            "mensa","osteria","hausbrauerei","backstube","kneipe","schlossbrauerei","caffè",
            "pub","kebab","familienhaus","speisen","bäcker","kaffeerösterei","kebap",
            "brasserie","teestube","streetfood","eis","vegan"
        )
    ),

    // Shopping
    SHOPPING(
        displayName = "Shopping",
        icon = "shopping_bag",
        color = "#BA68C8",
        keywords = listOf(
            "amazon", "ebay", "zalando", "otto", "h&m", "zara", "mediamarkt",
            "saturn", "ikea", "möbel", "fashion", "kleidung", "electronics",
            "computer","schuhe","hausgeräte","primark","elektromarkt","fashion",
            "vintage","bauhaus"
        )
    ),

    // Health & Insurance
    HEALTH(
        displayName = "Health",
        icon = "medical_services",
        color = "#F06292",
        keywords = listOf(
            "apotheke", "pharmacy", "arzt", "doctor", "krankenhaus", "hospital",
            "zahnarzt", "dentist", "optiker", "brille","hausarztpraxis","dr. med.",
            "kosmetik","zahnarztpraxis","hausärztliche praxis","dr.med","praxis",
            "dermacenter","fußpflegesalon","cosmetic","hautarzt","dr.","sonnenstudio",
            "nagelstudio","zahnmedizin","dental","augenärzte","augenarzt","kinderarzt",
            "frauenarzt","beauty","kinderärzte","medizinisches","medizinische","radiologiezentrum",
            "dermazentrum","hausärzte","fußpflege","urologie","psychotherapeutische","therapeutin",
            "therapeut","gesundheitszentrum","kieferorthopädie","zahnärzte","zahnärztin","frauenheilkunde",
            "spa","zahn"
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
             "kino", "cinema", "theater", "konzert", "ticket", "eventim",
            "palast","filmhaus","komödie",
            "kinemathek","casino","cinemaxX","cineStar","diskothek","festspielbüro","bühne"
        )
    ),
    SUBSCRIPTIONS(
        displayName = "Subscriptions",
        icon = "subscriptions",
        color = "#4DB6AC",
        keywords = listOf(
            "abo", "subscription", "mitgliedschaft", "membership", "patreon",
            "youtube premium", "apple", "google one", "cloud", "icloud",
            "abo", "abonnement", "subscription", "membership",
            "patreon", "patreon creator","youtube music",
            "spotify", "spotify premium", "netflix", "disney+", "amazon prime",
            "prime video", "apple music", "apple tv+", "icloud+", "google one",
            "google drive", "google play pass", "microsoft 365", "onedrive",
            "dropbox", "cloud", "cloud speicher", "cloud storage",
            "streaming abo", "gaming abo", "xbox game pass", "playstation plus",
            "nintendo switch online", "audible", "zeit abo",
            "bild plus", "spiegel+", "onlyfans", "twitch sub",
            "twitch subscription", "deezer", "tidal", "soundcloud go+",
            "amazon music", "kindle unlimited", "scribd",
            "linkedin premium", "adobe creative cloud", "canva pro",
            "grammarly premium", "nordvpn", "expressvpn", "surfshark",
            "bitwarden premium", "lastpass", "1password", "strava summit",
            "myfitnesspal premium", "calm", "headspace", "duolingo super",
            "babbel", "busuu premium", "sky ticket", "wow (seriös)",
            "magzter", "readly", "kobo plus"
        )
    ),

    // Communication
    PHONE_INTERNET(
        displayName = "Phone & Internet",
        icon = "phone",
        color = "#4DD0E1",
        keywords = listOf(
            "telekom", "vodafone", "o2", "telefonica", "1&1", "congstar",
            "mobilfunk", "internet", "dsl", "kabel deutschland", "unity media",
            "deutsche telekom", "t-mobile", "magenta", "magenta tv",
            "speedport", "vodafone cable", "gigacube", "o2 mobilfunk",
            "o2 community", "1und1", "drillisch", "smartmobil",
            "klarmobil", "mobilcom-debitel", "otelo", "simply",
            "freenet", "mobilfunk", "handyvertrag", "prepaid", "vertrag",
            "sim only", "e-sim", "ftth", "unitymedia",
            "unity", "vaudafone cable (schreibvariante)",
            "pŸur", "netcologne"
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
            "scalable", "investment", "dividende", "wertpapierdepot",
            "broker", "trading app", "aktienkauf",
            "daytrading", "swing trading", "etf sparplan",
            "indexfonds", "investmentfonds", "themenfonds", "scalable capital",
            "scalable broker", "consorsbank", "comdirect",
            "ing depot", "dkb depot", "smartbroker",
            "finanzen net zero", "justtrade", "investieren",
            "ausschüttung", "thesaurierend", "sparplan", "robo advisor",
            "long term investing", "value investing", "growth stocks", "krypto",
            "bitcoin etf", "nachhaltig etf"
        )
    ),

    // Sports & Fitness
    FITNESS(
        displayName = "Fitness & Sports",
        icon = "fitness_center",
        color = "#FF7043",
        keywords = listOf(
            "fitness", "gym", "mcfit", "fitx", "urban sports", "yoga",
            "sport", "schwimmbad", "pool", "verein", "fitnessstudio",
            "urban sports club", "clever fit", "john reed", "high5 fitness",
            "easyfitness", "rsg group", "lifefit group", "pfitzenmeier",
            "kieser training", "mrs sporty", "bodystreet", "fitbox",
            "körperformen", "25minutes", "fitness first",
            "yogastudio", "pilates", "spinning", "crossfit", "bootcamp",
            "sportschule", "freibad", "hallenbad", "verein",
            "sportverein", "tsv", "fitness abo",
            "monatskarte fitness", "probetraining","sportpark"
        )
    ),

    // Travel & Accommodation
    TRAVEL(
        displayName = "Travel",
        icon = "flight",
        color = "#29B6F6",
        keywords = listOf(
            "hotel", "hostel", "airbnb", "booking.com", "expedia", "trivago",
            "airline", "lufthansa", "ryanair", "easyjet", "flug", "flight",
            "car rental", "sixt", "europcar", "hertz", "avis",
            "holidaycheck", "kayak", "hotels.com", "agoda",
            "fewo-direkt", "hrs", "momondo", "airline",
            "lufthansa", "ryanair", "easyjet",
            "eurowings", "condor", "tuifly", "check-in",
            "boarding", "layover", "car rental",
            "budget rent a car", "enterprise", "flizzr",
            "sunexpress deutschland", "sundair", "hahn air",
            "luftfahrtgesellschaft walter",
            "air dolomiti", "discover airlines",
            "lufthansa cityline", "norwegian", "vueling", "wizz air", "volotea"
        )
    ),

    // Income
    SALARY(
        displayName = "Income",
        icon = "payments",
        color = "#66BB6A",
        keywords = listOf(
            "gehalt", "lohn", "salary", "wage", "vergütung", "arbeitgeber",
            "rente", "pension", "ruhestand", "bundeskasse", "rentenversicherung",
            "altersvorsorge", "betriebsrente", "riester", "rürup"
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
            "bargeld", "cash", "geldautomat", "atm", "auszahlung",
            "geldabhebung", "bar abheben", "cashpoint",
            "ec-karte abheben", "girocard auszahlung", "cash group",
            "cashpool", "sparkassen automaten",
            "volksbank geldautomat", "commerzbank atm", "deutsche bank geldautomat",
            "postbank auszahlung", "shell tankstelle geldautomat",
            "rewe cashback", "dm bargeld", "netto geldabheben",
            "penny cashpoint", "kostenlose geldautomaten",
            "gebührenfreie auszahlung", "geldautomaten suche",
            "atm locator", "bargeldcode", "postbank bargeldcode"
        )
    ),

    // PayPal & Payment Services
    PAYMENT_SERVICE(
        displayName = "Payment Service",
        icon = "payment",
        color = "#5C6BC0",
        keywords = listOf(
            "paypal", "klarna", "sofort", "giropay", "apple pay", "google pay",
            "sofortüberweisung", "amazon pay", "stripe", "mollie",
            "adyen", "multisafepay", "payone",
            "ratepay", "paydirekt", "ideal",
            "eps", "twint", "alipay", "wechat pay"
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
