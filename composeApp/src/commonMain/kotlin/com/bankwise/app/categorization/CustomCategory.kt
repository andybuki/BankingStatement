package com.bankwise.app.categorization

/**
 * Represents a user-defined custom category
 */
data class CustomCategory(
    val id: Long,
    val name: String,
    val icon: String,
    val color: String,
    val parentId: Long? = null
) {
    companion object {
        // Default colors for new custom categories
        val DEFAULT_COLORS = listOf(
            "#FF6B6B", // Red
            "#4ECDC4", // Teal
            "#45B7D1", // Blue
            "#96CEB4", // Sage
            "#FFEAA7", // Yellow
            "#DDA0DD", // Plum
            "#98D8C8", // Mint
            "#F7DC6F", // Gold
            "#BB8FCE", // Purple
            "#85C1E9", // Sky Blue
            "#F8B500", // Orange
            "#58D68D"  // Green
        )

        // Default icons for custom categories
        val DEFAULT_ICONS = listOf(
            "🏷️", // Tag
            "📦", // Package
            "🎯", // Target
            "⭐", // Star
            "💎", // Diamond
            "🔖", // Bookmark
            "📌", // Pin
            "🎨", // Art
            "🔧", // Tools
            "📱", // Phone
            "🏠", // Home
            "🚗"  // Car
        )
    }
}

/**
 * Unified category representation that can be either predefined or custom
 */
sealed class Category {
    abstract val displayName: String
    abstract val icon: String
    abstract val color: String
    abstract val identifier: String

    data class Predefined(val category: TransactionCategory) : Category() {
        override val displayName: String get() = category.displayName
        override val icon: String get() = category.icon
        override val color: String get() = category.color
        override val identifier: String get() = "predefined:${category.name}"
    }

    data class Custom(val customCategory: CustomCategory) : Category() {
        override val displayName: String get() = customCategory.name
        override val icon: String get() = customCategory.icon
        override val color: String get() = customCategory.color
        override val identifier: String get() = "custom:${customCategory.id}"
    }

    companion object {
        fun fromIdentifier(identifier: String, customCategories: List<CustomCategory>): Category? {
            return when {
                identifier.startsWith("predefined:") -> {
                    val name = identifier.removePrefix("predefined:")
                    TransactionCategory.entries.find { it.name == name }?.let { Predefined(it) }
                }
                identifier.startsWith("custom:") -> {
                    val id = identifier.removePrefix("custom:").toLongOrNull()
                    customCategories.find { it.id == id }?.let { Custom(it) }
                }
                else -> {
                    // Legacy: try to match by name
                    TransactionCategory.entries.find { it.name == identifier }?.let { Predefined(it) }
                        ?: customCategories.find { it.name == identifier }?.let { Custom(it) }
                }
            }
        }
    }
}
