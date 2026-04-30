package com.banking.statement.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.CustomCategory
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.ui.components.CategoryAvatar
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing

/**
 * Modal sheet that mirrors ui_kits/mobile/ScreensC.MLChangeCategorySheet:
 * a 18dp white sheet with a 20sp title, a scrollable list of category
 * buttons (32dp avatar + name; selected row gets blue-tint background
 * + 1dp accent outline + accent text), and a footer with "New Category"
 * and "Cancel" text buttons.
 */
@Composable
fun CategoryPickerDialog(
    currentCategory: TransactionCategory,
    customCategories: List<CustomCategory> = emptyList(),
    currentCustomCategoryId: Long? = null,
    onCategorySelected: (TransactionCategory) -> Unit,
    onCustomCategorySelected: ((Long) -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
                .shadow(AppElevations.md, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.CardBackground)
        ) {
            // Title — matches MLChangeCategorySheet (20sp / 700)
            Text(
                text = strings.changeCategory,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 12.dp
                )
            )

            // Category list
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                customCategories.forEach { customCategory ->
                    val color = parseColor(customCategory.color)
                    val isSelected = currentCustomCategoryId == customCategory.id
                    CategoryPickerRow(
                        label = customCategory.name,
                        accent = color,
                        isSelected = isSelected,
                        onClick = { onCustomCategorySelected?.invoke(customCategory.id) },
                        leading = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color.copy(alpha = 0.13f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = customCategory.icon,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    )
                }
                if (customCategories.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppColors.SurfaceTint)
                    )
                }

                TransactionCategory.entries
                    .filter { it != TransactionCategory.OTHER }
                    .forEach { category ->
                        val isSelected = category == currentCategory && currentCustomCategoryId == null
                        CategoryPickerRow(
                            label = category.getLocalizedName(strings),
                            accent = AppColors.Primary,
                            isSelected = isSelected,
                            onClick = { onCategorySelected(category) },
                            leading = { CategoryAvatar(category = category, size = 32.dp) }
                        )
                    }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Footer — top divider + "New Category" + "Cancel"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppColors.Divider)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onManageCategories != null) {
                    TextButton(
                        onClick = onManageCategories,
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Primary)
                    ) {
                        Text(
                            text = strings.newCategory,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(AppSpacing.s1))
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Primary)
                ) {
                    Text(
                        text = strings.cancel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPickerRow(
    label: String,
    accent: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) AppColors.PrimaryContainer.copy(alpha = 0.4f) else Color.Transparent)
            .then(
                if (isSelected) Modifier.border(1.dp, accent, shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading()
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) accent else AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}
