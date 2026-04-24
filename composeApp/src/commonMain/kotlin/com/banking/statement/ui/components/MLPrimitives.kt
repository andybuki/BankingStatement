package com.banking.statement.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import kotlin.math.abs
import kotlin.math.roundToInt

// ============================================================
// Colour parsing helper (duplicated small utility — Color(hex)).
// ============================================================

private fun parseHex(hex: String): Color {
    val clean = hex.removePrefix("#")
    val value = clean.toLong(16)
    return Color(if (clean.length <= 6) value or 0xFF000000L else value)
}

fun TransactionCategory.composeColor(): Color = parseHex(this.color)

fun TransactionCategory.iconVector(): ImageVector = when (this) {
    TransactionCategory.RENT -> Icons.Filled.Home
    TransactionCategory.TRANSPORT -> Icons.Filled.DirectionsBus
    TransactionCategory.SUPERMARKET -> Icons.Filled.ShoppingCart
    TransactionCategory.RESTAURANT -> Icons.Filled.Restaurant
    TransactionCategory.SHOPPING -> Icons.Filled.ShoppingBag
    TransactionCategory.HEALTH -> Icons.Filled.MedicalServices
    TransactionCategory.INSURANCE -> Icons.Filled.Security
    TransactionCategory.ENTERTAINMENT -> Icons.Filled.Movie
    TransactionCategory.SUBSCRIPTIONS -> Icons.Filled.Subscriptions
    TransactionCategory.INVESTMENT -> Icons.Filled.TrendingUp
    TransactionCategory.TRAVEL -> Icons.Filled.Flight
    TransactionCategory.SALARY -> Icons.Filled.Payments
    TransactionCategory.REFUND -> Icons.Filled.Replay
    TransactionCategory.TRANSFER -> Icons.Filled.SwapHoriz
    TransactionCategory.EDUCATION -> Icons.Filled.School
    TransactionCategory.TAXES -> Icons.Filled.Receipt
    TransactionCategory.OTHER -> Icons.Filled.Category
}

// ============================================================
// MLCard — standardized surface. Default = 16dp radius, 16dp
// padding, xs shadow. `floating` lifts to sm shadow for cards
// that sit on the navy background.
// ============================================================

@Composable
fun MLCard(
    modifier: Modifier = Modifier,
    padding: Dp = AppSpacing.s4,
    radius: Dp = AppRadii.lg,
    floating: Boolean = false,
    border: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (floating) AppElevations.sm else AppElevations.xs,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(AppColors.CardBackground)
            .then(if (border) Modifier.border(1.dp, AppColors.Divider, shape) else Modifier)
            .padding(padding),
        content = content
    )
}

// ============================================================
// EyebrowLabel — small uppercase caption used above sections.
// ============================================================

@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AppColors.TextTertiary
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.66.sp,
        color = color
    )
}

// ============================================================
// MoneyAmount — Roboto-Mono styled number coloured by sign.
// ============================================================

@Composable
fun MoneyAmount(
    value: Double,
    modifier: Modifier = Modifier,
    size: Int = 16,
    weight: FontWeight = FontWeight.Medium,
    showSign: Boolean = true,
    currencySymbol: String = "€"
) {
    val positive = value >= 0
    val color = if (positive) AppColors.Income else AppColors.Expenses
    val formatted = formatAmount2(abs(value))
    val sign = when {
        !showSign -> ""
        positive -> "+"
        else -> "−"
    }
    Text(
        text = "$sign$currencySymbol$formatted",
        modifier = modifier,
        fontSize = size.sp,
        fontWeight = weight,
        fontFamily = FontFamily.Monospace,
        color = color
    )
}

private fun formatAmount2(value: Double): String {
    val cents = (value * 100.0).roundToInt()
    val whole = cents / 100
    val frac = cents % 100
    val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
    val fracStr = frac.toString().padStart(2, '0')
    return "$wholeStr.$fracStr"
}

// ============================================================
// CategoryAvatar — coloured rounded square with category icon.
// Background is category color at ~13% alpha; icon in full color.
// ============================================================

@Composable
fun CategoryAvatar(
    category: TransactionCategory,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    customColor: Color? = null
) {
    val c = customColor ?: category.composeColor()
    val cornerFraction = 3.2f
    val cornerRadius = (size.value / cornerFraction).dp
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(c.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = category.iconVector(),
            contentDescription = null,
            tint = c,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

// ============================================================
// PeriodPills — Week / Month / Year / All toggle row.
// ============================================================

@Composable
fun PeriodPills(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            val active = opt == selected
            Surface(
                onClick = { onSelect(opt) },
                shape = RoundedCornerShape(AppRadii.pill),
                color = if (active) AppColors.Primary else AppColors.CardBackground,
                contentColor = if (active) Color.White else AppColors.TextSecondary,
                shadowElevation = if (active) 0.dp else AppElevations.xs
            ) {
                Text(
                    text = opt,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ============================================================
// TrendPill — small ↑ 12% / ↓ 8% chip, green for positive
// (income) / red for negative. Use opposite polarity for
// expense-context (where spending less is good).
// ============================================================

@Composable
fun TrendPill(
    percent: Int,
    modifier: Modifier = Modifier,
    positiveIsGood: Boolean = true
) {
    if (percent == 0) {
        Surface(
            shape = RoundedCornerShape(AppRadii.pill),
            color = AppColors.SurfaceTint,
            modifier = modifier
        ) {
            Text(
                text = "—",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextTertiary
            )
        }
        return
    }
    val up = percent > 0
    val good = if (positiveIsGood) up else !up
    val bg = if (good) AppColors.IncomeTint else AppColors.ExpenseTint
    val fg = if (good) AppColors.Income else AppColors.Expenses
    val arrow = if (up) "↑" else "↓"
    Surface(
        shape = RoundedCornerShape(AppRadii.pill),
        color = bg,
        contentColor = fg,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = "$arrow ${abs(percent)}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ============================================================
// TipCard — insight card with blue tint bg + 3dp blue left
// border + sparkle icon.
// ============================================================

@Composable
fun TipCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.AutoAwesome
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.InfoTint)
            .padding(AppSpacing.s4),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.s3)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .background(AppColors.Primary, RoundedCornerShape(AppRadii.pill))
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AppColors.Primary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = body,
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                lineHeight = 17.sp
            )
        }
    }
}

// ============================================================
// QuickActionCard — grid tile with tinted icon + label +
// subtitle. Used on Home for "Import statement" / "Accounts".
// ============================================================

@Composable
fun QuickActionCard(
    label: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.CardBackground)
            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
            .clickable(onClick = onClick)
            .padding(AppSpacing.s3 + 2.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s3 - 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
        Column {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = AppColors.TextTertiary
            )
        }
    }
}

// ============================================================
// SectionTitle — card heading pair with optional right action.
// ============================================================

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
        if (actionText != null) {
            Text(
                text = actionText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Primary,
                modifier = if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier
            )
        }
    }
}
