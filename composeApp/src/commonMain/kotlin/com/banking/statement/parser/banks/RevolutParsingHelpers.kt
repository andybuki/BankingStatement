package com.banking.statement.parser.banks

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ============================================================
// Shared helpers used by the Revolut format parsers.
// All have a "revolut" prefix to avoid collision with the
// German bank helpers that live in GermanBankParsingUtils.kt.
// ============================================================

internal fun revolutDetectCurrency(text: String): String {
    val lower = text.lowercase()
    return when {
        lower.contains("£") || lower.contains("gbp") || lower.contains("pound") -> "GBP"
        lower.contains("$") || lower.contains("usd") -> "USD"
        lower.contains("chf") -> "CHF"
        lower.contains("pln") || lower.contains("zł") -> "PLN"
        else -> "EUR"  // Default
    }
}

internal fun revolutExtractAccountNumber(text: String): String? {
    val ibanPattern = Regex("([A-Z]{2}\\d{2}[\\s]?(?:[A-Z0-9]{4}[\\s]?){2,7}[A-Z0-9]{1,4})")
    val match = ibanPattern.find(text.uppercase())
    return match?.groupValues?.get(1)?.replace("\\s".toRegex(), "")
}

internal fun revolutExtractStatementPeriod(text: String): String? {
    // English: "1 August 2025 to 12 September 2025" or "1 Aug 2025 - 12 Sep 2025"
    // German: "1. August 2025 bis 12. September 2025" or "Kontotransaktionen von 1. August 2025 bis 12. September 2025"
    val periodPatterns = listOf(
        Regex("""von\s+(\d{1,2}\.?\s+\w+\s+\d{4})\s+bis\s+(\d{1,2}\.?\s+\w+\s+\d{4})""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,2}\.?\s+\w+\s+\d{4})\s*[-–to]+\s*(\d{1,2}\.?\s+\w+\s+\d{4})""", RegexOption.IGNORE_CASE)
    )
    for (pattern in periodPatterns) {
        pattern.find(text)?.let { match ->
            return "${match.groupValues[1]} - ${match.groupValues[2]}"
        }
    }
    return null
}

internal fun revolutExtractCounterparty(description: String): String? {
    val words = description.split(Regex("\\s+"))
        .filter { it.length > 2 && !it.all { c -> c.isDigit() || c == '.' || c == ',' } }
        .take(5)
    return if (words.isNotEmpty()) words.joinToString(" ").take(50) else null
}

internal fun revolutDetectTransactionType(description: String): String {
    val lower = description.lowercase()
    return when {
        lower.contains("card payment") || lower.contains("pos") -> "Card Payment"
        lower.contains("transfer") || lower.contains("sent") -> "Transfer"
        lower.contains("top-up") || lower.contains("topup") -> "Top Up"
        lower.contains("exchange") || lower.contains("exchanged") -> "Exchange"
        lower.contains("received") -> "Received"
        lower.contains("atm") || lower.contains("withdraw") -> "ATM"
        lower.contains("refund") -> "Refund"
        lower.contains("subscription") -> "Subscription"
        lower.contains("fee") || lower.contains("charge") -> "Fee"
        else -> "Transaction"
    }
}

internal fun revolutIsHeaderOrFooter(line: String): Boolean {
    val lower = line.lowercase()
    return lower.contains("page") && lower.contains("of") ||
           lower.contains("statement") && lower.contains("account") ||
           lower.contains("opening balance") ||
           lower.contains("closing balance") ||
           lower.contains("date") && lower.contains("description") && lower.contains("amount") ||
           lower.contains("money in") && lower.contains("money out") ||
           lower.contains("total") && lower.contains("balance") ||
           lower.contains("revolut ltd") ||
           lower.contains("generated on") ||
           line.length < 5
}

internal fun revolutExtractDate(
    line: String,
    datePatternDMY: Regex,
    datePatternMDY: Regex,
    datePatternNumeric: Regex,
    datePatternISO: Regex
): LocalDate? {
    // Try DMY format: "25 Dec 2024"
    datePatternDMY.find(line)?.let { match ->
        return revolutParseDateMatch(match)
    }

    // Try MDY format: "Dec 25, 2024"
    datePatternMDY.find(line)?.let { match ->
        val monthStr = match.groupValues[1].lowercase().take(3)
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        val month = revolutMonths[monthStr] ?: return null
        return try { LocalDate(year, month, day) } catch (e: Exception) { null }
    }

    // Try numeric format: "25/12/2024" (assume DD/MM/YYYY — European)
    datePatternNumeric.find(line)?.let { match ->
        val first = match.groupValues[1].toIntOrNull() ?: return null
        val second = match.groupValues[2].toIntOrNull() ?: return null
        val year = match.groupValues[3].toIntOrNull() ?: return null
        return try { LocalDate(year, second, first) } catch (e: Exception) { null }
    }

    // Try ISO format: "2024-12-25"
    datePatternISO.find(line)?.let { match ->
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return try { LocalDate(year, month, day) } catch (e: Exception) { null }
    }

    return null
}

internal fun revolutParseDateMatch(match: MatchResult): LocalDate? {
    return try {
        val groups = match.groupValues
        when {
            // "25 Dec 2024" format
            groups.size >= 4 && groups[2].length >= 3 -> {
                val day = groups[1].toIntOrNull() ?: return null
                val monthStr = groups[2].lowercase().take(3)
                val year = groups[3].toIntOrNull()
                    ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
                val month = revolutMonths[monthStr] ?: return null
                LocalDate(year, month, day)
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

internal fun revolutParseAmount(amounts: List<MatchResult>): Double? {
    if (amounts.isEmpty()) return null

    // For Revolut, often we have Money In and Money Out columns
    // Take the first non-zero amount, or determine by sign
    for (match in amounts) {
        val sign = if (match.groupValues[2] == "-") -1 else 1
        val amountStr = match.groupValues[3]
            .replace(",", "")
            .replace("'", "")
        val amount = amountStr.toDoubleOrNull()
        if (amount != null && amount != 0.0) {
            return amount * sign
        }
    }
    return null
}

internal fun revolutParseAmountMatches(amounts: List<MatchResult>): Double? {
    if (amounts.isEmpty()) return null

    for (match in amounts) {
        val signStr = match.groupValues[1]
        val sign = when (signStr) {
            "-" -> -1
            "+" -> 1
            else -> -1  // Default to expense for Revolut
        }
        val amountStr = match.groupValues[3]
            .replace(",", "")
            .replace("'", "")
        val amount = amountStr.toDoubleOrNull()
        if (amount != null && amount != 0.0) {
            return amount * sign
        }
    }
    return null
}
