package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.LocalStrings
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing

/**
 * Sheet shown when the user taps a transaction row.
 *
 * Single-tap doesn't immediately open the category picker — it opens this
 * action sheet so the same gesture can also lead to "view source PDF". One
 * extra tap from here opens the category picker.
 */
@Composable
fun TransactionActionsDialog(
    transaction: TransactionDisplay,
    canViewSourcePdf: Boolean,
    onChangeCategory: () -> Unit,
    onViewSourcePdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(AppElevations.md, RoundedCornerShape(AppRadii.xl), clip = false)
                .clip(RoundedCornerShape(AppRadii.xl))
                .background(AppColors.CardBackground)
                .padding(AppSpacing.s5)
        ) {
            EyebrowLabel(text = "Transaction")
            Spacer(Modifier.height(4.dp))
            Text(
                text = TransactionDisplay.extractDisplayName(
                    transaction.counterparty,
                    transaction.description
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${transaction.date}  ·  ${transaction.effectiveCategoryName}",
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )

            Spacer(Modifier.height(AppSpacing.s4))

            ActionRow(
                icon = Icons.Filled.Edit,
                iconTint = AppColors.Primary,
                label = strings.changeCategory,
                subtitle = "Pick a different predefined or custom category",
                onClick = onChangeCategory
            )
            if (canViewSourcePdf) {
                Spacer(Modifier.height(AppSpacing.s2))
                ActionRow(
                    icon = Icons.Filled.Description,
                    iconTint = Color(0xFFEF4444),
                    label = "View source PDF",
                    subtitle = if (transaction.sourcePage != null)
                        "Open the original statement at page ${transaction.sourcePage + 1}"
                    else
                        "Open the original statement PDF",
                    onClick = onViewSourcePdf
                )
            }

            Spacer(Modifier.height(AppSpacing.s3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextSecondary)
                ) {
                    Text(strings.cancel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AppRadii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.SurfaceTint)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.s3, vertical = AppSpacing.s3),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(AppSpacing.s3))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
        }
    }
}
