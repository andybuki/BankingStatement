package com.bankwise.app.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bankwise.app.LocalStrings
import com.bankwise.app.categorization.CustomCategory
import com.bankwise.app.categorization.TransactionCategory
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import org.jetbrains.compose.resources.painterResource

/**
 * Screen for managing custom categories
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Image(
                        painter = painterResource(Res.drawable.back),
                        contentDescription = strings.back,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                }
                Text(
                    text = strings.manageCategories,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Add button in header instead of FAB
                FilledTonalButton(
                    onClick = { showAddDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "+ ${strings.add}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Custom Categories Section
                item {
                    Text(
                        text = strings.customCategories,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                if (customCategories.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = strings.noCategoriesYet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = strings.createFirstCategory,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(customCategories) { category ->
                        CustomCategoryItem(
                            category = category,
                            onEdit = { editingCategory = category },
                            onDelete = { deletingCategory = category }
                        )
                    }
                }

                // Predefined Categories Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.predefinedCategories,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(TransactionCategory.entries.toList()) { category ->
                    PredefinedCategoryItem(category = category)
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Add Category Dialog
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

    // Edit Category Dialog
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

    // Delete Confirmation Dialog
    deletingCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text(strings.deleteCategory) },
            text = { Text(strings.deleteCategoryConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCategory(category.id)
                        deletingCategory = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun CustomCategoryItem(
    category: CustomCategory,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Color indicator
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(parseHexColorSafe(category.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.icon,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Text(text = "✏️", style = MaterialTheme.typography.bodyLarge)
                }
                IconButton(onClick = onDelete) {
                    Text(text = "🗑️", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PredefinedCategoryItem(category: TransactionCategory) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parseHexColorSafe(category.color)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getCategoryEmojiForPicker(category),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = category.getLocalizedName(strings),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

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
    var nameError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text(strings.categoryName) },
                    placeholder = { Text("e.g., Bakery, Car, Pets") },
                    isError = nameError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Icon picker
                Text(
                    text = strings.categoryIcon,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CustomCategory.DEFAULT_ICONS) { icon ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selectedIcon == icon)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (selectedIcon == icon) 2.dp else 0.dp,
                                    color = if (selectedIcon == icon)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = icon,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color picker
                Text(
                    text = strings.categoryColor,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CustomCategory.DEFAULT_COLORS) { color ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(parseHexColorSafe(color))
                                .border(
                                    width = if (selectedColor == color) 3.dp else 0.dp,
                                    color = if (selectedColor == color)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preview: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColorSafe(selectedColor)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = selectedIcon,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = name.ifBlank { "Category Name" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (name.isBlank())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.cancel)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onSave(name.trim(), selectedIcon, selectedColor)
                            } else {
                                nameError = true
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text(strings.save)
                    }
                }
            }
        }
    }
}

private fun getCategoryEmojiForPicker(category: TransactionCategory): String {
    return when (category) {
        TransactionCategory.RENT -> "🏠"
        TransactionCategory.TRANSPORT -> "🚌"
        TransactionCategory.SUPERMARKET -> "🛒"
        TransactionCategory.RESTAURANT -> "🍽️"
        TransactionCategory.SHOPPING -> "🛍️"
        TransactionCategory.HEALTH -> "💊"
        TransactionCategory.INSURANCE -> "🛡️"
        TransactionCategory.ENTERTAINMENT -> "🎬"
        TransactionCategory.SUBSCRIPTIONS -> "📱"
        TransactionCategory.INVESTMENT -> "📈"
        TransactionCategory.TRAVEL -> "✈️"
        TransactionCategory.SALARY -> "💰"
        TransactionCategory.REFUND -> "↩️"
        TransactionCategory.TRANSFER -> "↔️"
        TransactionCategory.EDUCATION -> "🎓"
        TransactionCategory.TAXES -> "📋"
        TransactionCategory.OTHER -> "❓"
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
