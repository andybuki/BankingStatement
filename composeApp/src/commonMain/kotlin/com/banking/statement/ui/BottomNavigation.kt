package com.banking.statement.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.banking.statement.LocalStrings
import com.banking.statement.ui.theme.AppColors

/**
 * Navigation tabs for the bottom navigation bar
 */
enum class NavigationTab(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    TRANSACTIONS(
        selectedIcon = Icons.Filled.Receipt,
        unselectedIcon = Icons.Outlined.Receipt
    ),
    SPENDING(
        selectedIcon = Icons.Filled.PieChart,
        unselectedIcon = Icons.Outlined.PieChart
    ),
    MERCHANTS(
        selectedIcon = Icons.Filled.Store,
        unselectedIcon = Icons.Outlined.Store
    ),
    SETTINGS(
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

/**
 * Bottom navigation bar component - white footer style
 */
@Composable
fun AppBottomNavigation(
    currentTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    NavigationBar(
        modifier = modifier.height(88.dp),
        containerColor = AppColors.FooterBackground,
        contentColor = AppColors.FooterInactiveText,
        tonalElevation = 0.dp
    ) {
        NavigationTab.entries.forEach { tab ->
            val selected = currentTab == tab
            val label = when (tab) {
                NavigationTab.HOME -> strings.tabHome
                NavigationTab.TRANSACTIONS -> strings.tabTransactions
                NavigationTab.SPENDING -> strings.tabSpending
                NavigationTab.MERCHANTS -> strings.tabMerchants
                NavigationTab.SETTINGS -> strings.tabSettings
            }

            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = label,
                        modifier = Modifier.size(24.dp)
                    )
                },
                label = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.FooterActiveIcon,
                    selectedTextColor = AppColors.FooterActiveText,
                    indicatorColor = AppColors.FooterBackground,  // No visible indicator
                    unselectedIconColor = AppColors.FooterInactiveIcon,
                    unselectedTextColor = AppColors.FooterInactiveText
                )
            )
        }
    }
}
