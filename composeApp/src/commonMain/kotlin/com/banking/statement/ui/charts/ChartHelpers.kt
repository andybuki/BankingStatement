package com.banking.statement.ui.charts

import androidx.compose.ui.graphics.Color
import com.banking.statement.categorization.TransactionCategory
import kotlin.math.roundToInt

/**
 * Data class for stacked area chart input.
 */
data class MonthCategoryBreakdown(
    val month: String,
    val categoryAmounts: List<Pair<TransactionCategory, Double>>
)

/**
 * Data class for merchant spending chart input.
 */
data class MerchantSpendingData(
    val name: String,
    val amount: Double,
    val transactionCount: Int
)

/**
 * Parse hex color string to Color (cross-platform).
 * Handles both #RRGGBB and #AARRGGBB formats.
 */
fun parseColor(colorString: String): Color {
    return try {
        val cleanColor = colorString.removePrefix("#")
        val colorInt = cleanColor.toLong(16)

        when (cleanColor.length) {
            6 -> {
                // RGB format: #RRGGBB
                val r = ((colorInt shr 16) and 0xFF) / 255f
                val g = ((colorInt shr 8) and 0xFF) / 255f
                val b = (colorInt and 0xFF) / 255f
                Color(r, g, b)
            }
            8 -> {
                // ARGB format: #AARRGGBB
                val a = ((colorInt shr 24) and 0xFF) / 255f
                val r = ((colorInt shr 16) and 0xFF) / 255f
                val g = ((colorInt shr 8) and 0xFF) / 255f
                val b = (colorInt and 0xFF) / 255f
                Color(r, g, b, a)
            }
            else -> Color.Gray
        }
    } catch (e: Exception) {
        Color.Gray
    }
}

internal fun formatCurrencyChart(amount: Double): String {
    val formatted = ((amount * 100).roundToInt() / 100.0).toString().replace('.', ',')
    return "-$formatted €"
}

internal fun formatAmountShort(amount: Double): String {
    val rounded = (amount * 100).roundToInt() / 100.0
    return rounded.toString()
}
