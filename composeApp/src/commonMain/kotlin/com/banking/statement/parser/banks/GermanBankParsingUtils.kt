package com.banking.statement.parser.banks

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Common German transaction type keywords
internal val germanTransactionTypes = listOf(
    "Lastschrift", "Gutschrift", "Überweisung", "Dauerauftrag",
    "Gehalt", "Lohn", "Kartenzahlung", "Bargeldauszahlung",
    "Abschluss", "Zinsen", "Entgelt", "Einzahlung", "Auszahlung",
    "SEPA-Lastschrift", "SEPA-Überweisung", "Kontoführung",
    "Geldautomat", "Echtzeitüberweisung", "Abrechnung",
    "Barauszahlung", "Bareinzahlung", "Scheckeinreichung",
    "Wertpapier", "Dividende", "Zinsabschluss",
    "SEPA Lastschrifteinzug", "SEPA-Basislastschrift", "Basislastschrift",
    "Zahlungseingang", "SDD Lastschr", "Wertstellung"
)

// Keywords that indicate INCOME (positive amount)
internal val incomeKeywords = listOf(
    "gutschrift", "zahlungseingang", "gehalt", "lohn", "einzahlung",
    "geldeingang", "eingang", "haben", "überweisung von", "zahlung von",
    "erstattung", "rückerstattung", "zinsen", "dividende", "bonus"
)

// Keywords that indicate EXPENSE (negative amount)
internal val expenseKeywords = listOf(
    "lastschrift", "abbuchung", "auszahlung", "geldausgang", "ausgang",
    "soll", "kartenzahlung", "überweisung an", "zahlung an", "entgelt",
    "gebühr", "kosten"
)

internal fun parseGermanDate(dateStr: String): LocalDate? {
    return try {
        val parts = dateStr.split(".")
        if (parts.size == 3) {
            LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        } else if (parts.size == 2) {
            // Short date format: DD.MM. - use current year
            val currentYear = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).year
            LocalDate(currentYear, parts[1].trimEnd('.').toInt(), parts[0].toInt())
        } else null
    } catch (e: Exception) {
        null
    }
}

internal fun parseGermanAmount(amountStr: String): Double? {
    return try {
        amountStr
            .replace("€", "")
            .replace("EUR", "")
            .replace(" ", "")
            .replace(".", "")
            .replace(",", ".")
            .replace("−", "-")  // Unicode minus to regular minus
            .replace("–", "-")  // En dash to minus
            .trim()
            .toDouble()
    } catch (e: Exception) {
        null
    }
}

/**
 * Parse amount with sign suffix/prefix: "25,49+", "128,96 S", "2.500,00 H", "- 492,00", "19,90 -"
 * Returns Pair(amount, isExpense)
 */
internal fun parseGermanAmountWithSign(text: String, context: String = ""): Pair<Double, Boolean>? {
    val normalized = text
        .replace("−", "-")
        .replace("–", "-")
        .trim()

    val amountPattern = Regex(
        """([+-])?\s*(\d{1,3}(?:[.]\d{3})*,\d{2})\s*(?:€|EUR)?\s*([+-SH])?""",
        RegexOption.IGNORE_CASE
    )

    val match = amountPattern.find(normalized) ?: return null
    val prefixSign = match.groupValues[1]
    val amountStr = match.groupValues[2]
    val suffixSign = match.groupValues[3].uppercase()

    val amount = parseGermanAmount(amountStr) ?: return null

    val isExpense = when {
        prefixSign == "-" -> true
        prefixSign == "+" -> false
        suffixSign == "-" -> true
        suffixSign == "+" -> false
        suffixSign == "S" -> true   // Soll = debit = expense
        suffixSign == "H" -> false  // Haben = credit = income
        else -> isExpenseFromContext(context)
    }

    return Pair(amount, isExpense)
}

/**
 * Determine if transaction is expense based on description keywords
 */
internal fun isExpenseFromContext(context: String): Boolean {
    val lower = context.lowercase()
    for (keyword in incomeKeywords) {
        if (lower.contains(keyword)) return false
    }
    for (keyword in expenseKeywords) {
        if (lower.contains(keyword)) return true
    }
    // Default to expense for unknown
    return true
}

/**
 * Determine sign multiplier from context and parsed sign
 */
internal fun getSignMultiplier(signStr: String?, context: String): Double {
    val sign = signStr?.trim()?.uppercase() ?: ""
    return when {
        sign.startsWith("-") || sign == "S" -> -1.0
        sign.startsWith("+") || sign == "H" -> 1.0
        !isExpenseFromContext(context) -> 1.0
        else -> -1.0
    }
}

internal fun normalizeYear(dateStr: String): String {
    val parts = dateStr.split(".")
    if (parts.size == 3 && parts[2].length == 2) {
        val year = parts[2].toIntOrNull() ?: return dateStr
        val fullYear = if (year > 50) 1900 + year else 2000 + year
        return "${parts[0]}.${parts[1]}.$fullYear"
    }
    // Handle short dates: "06.07" -> add current year
    if (parts.size == 2 || (parts.size == 3 && parts[2].isEmpty())) {
        val currentYear = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).year
        return "${parts[0]}.${parts[1]}.$currentYear"
    }
    return dateStr
}

internal fun extractIban(text: String): String? {
    val ibanPattern = Regex("([A-Z]{2}\\d{2}[\\s]?(?:\\d{4}[\\s]?){4}\\d{2})")
    val match = ibanPattern.find(text.uppercase())
    return match?.groupValues?.get(1)?.replace("\\s".toRegex(), "")
}

internal fun extractStatementPeriod(text: String): String? {
    val patterns = listOf(
        Regex("Kontoauszug\\s+(?:Nr\\.?\\s*\\d+\\s+)?(?:vom\\s+)?(\\d{2}\\.\\d{2}\\.\\d{4})(?:\\s*[-–bis]+\\s*(\\d{2}\\.\\d{2}\\.\\d{4}))?", RegexOption.IGNORE_CASE),
        Regex("Auszug\\s+(\\w+\\s+\\d{4})", RegexOption.IGNORE_CASE),
        Regex("Zeitraum[:\\s]+(\\d{2}\\.\\d{2}\\.\\d{4})\\s*[-–bis]+\\s*(\\d{2}\\.\\d{2}\\.\\d{4})", RegexOption.IGNORE_CASE),
        Regex("(\\w+)\\s+(\\d{4})\\s*Kontoauszug", RegexOption.IGNORE_CASE)
    )

    for (pattern in patterns) {
        val match = pattern.find(text)
        if (match != null) {
            return match.groupValues.drop(1).filter { it.isNotBlank() }.joinToString(" - ")
        }
    }
    return null
}

internal fun detectTransactionType(description: String): String {
    val trimmed = description.trim()
    val lower = trimmed.lowercase()

    for (type in germanTransactionTypes) {
        val typeLower = type.lowercase()
        if (lower.startsWith(typeLower)) {
            return type
        }
    }

    if (lower.startsWith("sepa")) {
        if (lower.contains("lastschrift")) return "SEPA-Lastschrift"
        if (lower.contains("überweisung")) return "SEPA-Überweisung"
        return "SEPA"
    }

    val firstWord = trimmed.split(Regex("[\\s-]")).firstOrNull()?.trim()
    if (firstWord != null) {
        for (type in germanTransactionTypes) {
            if (firstWord.equals(type, ignoreCase = true)) {
                return type
            }
        }
    }

    for (type in germanTransactionTypes) {
        if (lower.contains(type.lowercase())) {
            return type
        }
    }

    return "Buchung"
}

/**
 * Extract counterparty name using German banking patterns
 */
internal fun extractCounterpartyGerman(description: String): String? {
    val holder = extractAccountHolder(description)
    if (holder != null) return holder

    val patterns = listOf(
        // "PayPal Europe S.a.r.l. et Cie S.C.A"
        Regex("""^([A-Za-zäöüÄÖÜß][A-Za-zäöüÄÖÜß\s.\-&]+(?:GmbH|AG|KG|e\.V\.|S\.A\.|Ltd|Inc|SE))""", RegexOption.IGNORE_CASE),
        // First meaningful words before special chars or markers
        Regex("""^([A-Za-zäöüÄÖÜß][A-Za-zäöüÄÖÜß\s]{2,40})(?:\s+DE\d|\s+EREF|\s+SVWZ|/|,)""")
    )

    for (pattern in patterns) {
        val match = pattern.find(description)
        if (match != null) {
            val name = match.groupValues[1].trim()
            if (name.length > 2 && !germanTransactionTypes.any { name.equals(it, ignoreCase = true) }) {
                return name
            }
        }
    }

    return null
}

internal fun extractCounterparty(description: String): String? {
    // First try German banking patterns (from jejik-mt940)
    val germanCounterparty = extractCounterpartyGerman(description)
    if (germanCounterparty != null) return germanCounterparty

    // Fallback: extract first meaningful words
    val words = description.split(Regex("\\s+"))
        .filter { word ->
            word.length > 2 &&
            !word.all { c -> c.isDigit() || c == '.' || c == ',' } &&
            !germanTransactionTypes.any { word.equals(it, ignoreCase = true) }
        }
        .take(5)

    return if (words.isNotEmpty()) {
        words.joinToString(" ").take(50)
    } else null
}

internal fun isHeaderOrFooter(line: String): Boolean {
    val lower = line.lowercase().trim()

    // Balance/summary lines - must be filtered out
    if (lower.contains("neuer saldo") ||
        lower.contains("alter saldo") ||
        lower.contains("anfangssaldo") ||
        lower.contains("endsaldo") ||
        lower.contains("kontosaldo") ||
        lower.contains("gesamtsaldo") ||
        lower.contains("kontostand") ||
        lower.contains("summe haben") ||
        lower.contains("summe soll") ||
        lower.contains("disponibel") ||
        lower.startsWith("saldo") ||
        lower == "kontonummer" ||
        lower == "filialnummer" ||
        lower == "iban" ||
        lower == "bic") {
        return true
    }

    // Page headers/footers
    if ((lower.contains("seite") && lower.contains("von")) ||
        (lower.contains("kontoauszug") && (lower.contains("nr") || lower.contains("nummer"))) ||
        (lower.contains("iban") && lower.contains("bic")) ||
        lower.contains("blz") ||
        (lower.contains("datum") && lower.contains("betrag") && lower.contains("saldo")) ||
        lower.contains("übertrag") ||
        lower.contains("buchungsdatum") && lower.contains("wertstellung") ||
        lower.contains("fortsetzung") ||
        lower.startsWith("buchungstext") ||
        lower.startsWith("verwendungszweck") && lower.length < 25) {
        return true
    }

    // Account number lines (just digits separated by spaces)
    if (Regex("""^\d+\s*\d*\s*$""").matches(line.trim()) && line.trim().length < 15) {
        return true
    }

    // Too short lines
    if (line.trim().length < 5) {
        return true
    }

    return false
}

/**
 * Check if this looks like a balance entry rather than a real transaction.
 * These should not be included in the transaction list.
 */
internal fun isBalanceEntry(description: String, rawText: String): Boolean {
    val lowerDesc = description.lowercase()
    val lowerRaw = rawText.lowercase()

    val balanceKeywords = listOf(
        "neuer saldo", "alter saldo", "anfangssaldo", "endsaldo",
        "kontosaldo", "gesamtsaldo", "kontostand", "saldo per",
        "disponibel", "verfügbar", "guthaben per", "summe haben",
        "summe soll", "kontonummer", "filialnummer"
    )

    for (keyword in balanceKeywords) {
        if (lowerDesc.contains(keyword) || lowerRaw.contains(keyword)) {
            return true
        }
    }

    if (description.trim().uppercase() in listOf("EUR", "€", "EURO", "")) {
        return true
    }

    return false
}

/**
 * Preprocess lines: Join split dates like "02.05.\n2024" -> "02.05.2024"
 */
internal fun preprocessLines(lines: List<String>): List<String> {
    val result = mutableListOf<String>()
    var i = 0
    while (i < lines.size) {
        var line = lines[i]

        val incompleteDatePattern = Regex("""(\d{2}\.\d{2}\.)\s*$""")
        val yearPattern = Regex("""^(\d{4})\s*$""")

        if (incompleteDatePattern.containsMatchIn(line) && i + 1 < lines.size) {
            val nextLine = lines[i + 1].trim()
            val yearMatch = yearPattern.find(nextLine)
            if (yearMatch != null) {
                line = line.trimEnd() + yearMatch.groupValues[1]
                i++ // Skip the year line
            }
        }

        result.add(line)
        i++
    }
    return result
}
