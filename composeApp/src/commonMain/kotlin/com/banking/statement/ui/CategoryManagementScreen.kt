package com.banking.statement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import com.banking.statement.LocalStrings
import com.banking.statement.categorization.CustomCategory
import com.banking.statement.categorization.TransactionCategory
import com.banking.statement.ui.components.CategoryAvatar
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppSpacing
import org.jetbrains.compose.resources.painterResource

/**
 * Screen for managing custom categories — laid out per
 * ui_kits/mobile/ScreensC.MLManageCategoriesScreen:
 * navy sub-page header (back + title + "+ Add" pill), then a
 * SurfaceTint canvas with two stacked sections (Custom + Predefined).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    customCategories: List<CustomCategory>,
    onBackClick: () -> Unit,
    onAddCategory: (name: String, icon: String, color: String) -> Unit,
    onEditCategory: (id: Long, name: String, icon: String, color: String) -> Unit,
    onDeleteCategory: (id: Long) -> Unit
) {
    val strings = LocalStrings.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CustomCategory?>(null) }
    var deletingCategory by remember { mutableStateOf<CustomCategory?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.SurfaceTint)
    ) {
        // Sub-page header — back + title on the left, "+ Add" pill on the right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.HeaderBackground)
                .padding(horizontal = 12.dp)
                .padding(top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBackClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(Res.drawable.back),
                    contentDescription = strings.back,
                    modifier = Modifier.size(22.dp),
                    colorFilter = ColorFilter.tint(AppColors.HeaderText)
                )
                Text(
                    text = strings.manageCategories,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.HeaderText
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable { showAddDialog = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = AppColors.HeaderText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = strings.add,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.HeaderText
                )
            }
        }

        // Body
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "custom-section") {
                Column {
                    SectionHeading(text = strings.customCategories)
                    if (customCategories.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.CardBackground)
                                .padding(vertical = 28.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = strings.noCategoriesYet,
                                    fontSize = 14.sp,
                                    color = AppColors.TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = strings.createFirstCategory,
                                    fontSize = 12.sp,
                                    color = AppColors.TextTertiary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.CardBackground)
                        ) {
                            customCategories.forEachIndexed { index, category ->
                                CustomCategoryRow(
                                    category = category,
                                    isFirst = index == 0,
                                    onEdit = { editingCategory = category },
                                    onDelete = { deletingCategory = category }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "predefined-section") {
                Column {
                    SectionHeading(text = strings.predefinedCategories)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransactionCategory.entries.forEach { category ->
                            PredefinedCategoryRow(category = category)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        CategoryEditDialog(
            title = strings.addCategory,
            category = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, icon, color ->
                onAddCategory(name, icon, color)
                showAddDialog = false
            }
        )
    }

    editingCategory?.let { category ->
        CategoryEditDialog(
            title = strings.editCategory,
            category = category,
            onDismiss = { editingCategory = null },
            onSave = { name, icon, color ->
                onEditCategory(category.id, name, icon, color)
                editingCategory = null
            }
        )
    }

    deletingCategory?.let { category ->
        DeleteCategoryConfirmDialog(
            onConfirm = {
                onDeleteCategory(category.id)
                deletingCategory = null
            },
            onDismiss = { deletingCategory = null }
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.TextPrimary,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

@Composable
private fun CustomCategoryRow(
    category: CustomCategory,
    isFirst: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = parseHexColorSafe(category.color)
    Column {
        if (!isFirst) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 60.dp)
                    .height(1.dp)
                    .background(AppColors.SurfaceTint)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.icon,
                    fontSize = 18.sp
                )
            }
            Text(
                text = category.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = AppColors.Expenses.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PredefinedCategoryRow(category: TransactionCategory) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.CardBackground)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryAvatar(category = category, size = 36.dp)
        Text(
            text = category.getLocalizedName(strings),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Add / Edit category dialog — matches ScreensC.MLAddCategorySheet.
 * 18dp white sheet, name input on a SurfaceTint field, horizontal-scroll
 * icon + color pickers, live preview row, footer with Cancel / Save.
 */
@Composable
private fun CategoryEditDialog(
    title: String,
    category: CustomCategory?,
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String, color: String) -> Unit
) {
    val strings = LocalStrings.current
    var name by remember { mutableStateOf(category?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(category?.icon ?: CustomCategory.DEFAULT_ICONS.first()) }
    var selectedColor by remember { mutableStateOf(category?.color ?: CustomCategory.DEFAULT_COLORS.first()) }
    val canSave = name.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(AppElevations.md, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.CardBackground)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp)
            )

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name input — filled style on SurfaceTint
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.SurfaceTint)
                        .border(1.dp, AppColors.Divider, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp)
                ) {
                    BasicCategoryNameField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = strings.categoryName
                    )
                }

                // Icon picker
                Column {
                    Text(
                        text = strings.categoryIcon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(CustomCategory.DEFAULT_ICONS) { icon ->
                            val isSelected = selectedIcon == icon
                            val accent = parseHexColorSafe(selectedColor)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) accent else AppColors.SurfaceTint)
                                    .clickable { selectedIcon = icon },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = icon,
                                    fontSize = 18.sp,
                                    color = if (isSelected) Color.White else AppColors.TextSecondary
                                )
                            }
                        }
                    }
                }

                // Color picker
                Column {
                    Text(
                        text = strings.categoryColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(CustomCategory.DEFAULT_COLORS) { hex ->
                            val isSelected = selectedColor == hex
                            val swatch = parseHexColorSafe(hex)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(swatch.copy(alpha = 0.30f))
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .clickable { selectedColor = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Text(
                                            text = "✓",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Live preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.SurfaceTint)
                        .border(1.dp, AppColors.Divider, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Preview:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextTertiary
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(parseHexColorSafe(selectedColor)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedIcon,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = name.ifBlank { "Category Name" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (name.isBlank()) AppColors.TextTertiary else AppColors.TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Primary)
                ) {
                    Text(text = strings.cancel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(AppSpacing.s1))
                TextButton(
                    onClick = {
                        if (canSave) onSave(name.trim(), selectedIcon, selectedColor)
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AppColors.Primary,
                        disabledContentColor = AppColors.TextTertiary
                    )
                ) {
                    Text(text = strings.save, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BasicCategoryNameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = AppColors.TextPrimary,
            fontSize = 16.sp
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(AppColors.Primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = AppColors.TextTertiary,
                    fontSize = 16.sp
                )
            }
            inner()
        }
    )
}

@Composable
private fun DeleteCategoryConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(AppElevations.md, RoundedCornerShape(18.dp), clip = false)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.CardBackground)
                .padding(20.dp)
        ) {
            Text(
                text = strings.deleteCategory,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.deleteCategoryConfirm,
                fontSize = 14.sp,
                color = AppColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextSecondary)
                ) {
                    Text(strings.cancel, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(AppSpacing.s1))
                TextButton(
                    onClick = onConfirm,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Expenses)
                ) {
                    Text(strings.delete, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun parseHexColorSafe(hexColor: String): Color {
    return try {
        val hex = hexColor.removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) {
        Color.Gray
    }
}
