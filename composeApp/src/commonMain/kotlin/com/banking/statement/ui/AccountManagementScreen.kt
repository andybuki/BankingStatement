package com.banking.statement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import com.banking.statement.LocalStrings
import com.banking.statement.ui.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource

/**
 * Data class for account display in management screen
 */
data class AccountManagementItem(
    val id: Long,
    val name: String,
    val bankName: String,
    val iban: String?,
    val color: String?,
    val transactionCount: Long,
    val statementCount: Long,
    val balance: Double?
)

/**
 * Account Management Screen for viewing, editing, and deleting accounts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    accounts: List<AccountManagementItem>,
    onBackClick: (() -> Unit)? = null,
    onDeleteAccount: (Long) -> Unit,
    onEditAccount: (Long, String) -> Unit,
    onClearAllData: () -> Unit,
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val strings = LocalStrings.current
    var showDeleteDialog by remember { mutableStateOf<AccountManagementItem?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<AccountManagementItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = strings.manageAccounts,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (onBackClick != null) {
                        IconButton(onClick = onBackClick) {
                            Image(
                                painter = painterResource(Res.drawable.back),
                                contentDescription = strings.back,
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (accounts.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.noAccounts,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = strings.noAccountsHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Account list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(accounts) { account ->
                    AccountManagementCard(
                        account = account,
                        onEdit = { showEditDialog = account },
                        onDelete = { showDeleteDialog = account }
                    )
                }
            }
        }

        // Settings Section
        Spacer(modifier = Modifier.height(16.dp))

        ThemeSettingsCard(
            currentThemeMode = currentThemeMode,
            onThemeModeChange = onThemeModeChange
        )

        // Clear all data button (only show if there's data)
        if (accounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { showClearAllDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error)
                )
            ) {
                Text(strings.clearAllData)
            }
        }
        }
    }

    // Delete account confirmation dialog
    showDeleteDialog?.let { account ->
        DeleteAccountDialog(
            accountName = account.name,
            onConfirm = {
                onDeleteAccount(account.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    // Clear all data confirmation dialog
    if (showClearAllDialog) {
        ClearAllDataDialog(
            onConfirm = {
                onClearAllData()
                showClearAllDialog = false
            },
            onDismiss = { showClearAllDialog = false }
        )
    }

    // Edit account dialog
    showEditDialog?.let { account ->
        EditAccountDialog(
            accountName = account.name,
            onSave = { newName ->
                onEditAccount(account.id, newName)
                showEditDialog = null
            },
            onDismiss = { showEditDialog = null }
        )
    }
}

@Composable
private fun AccountManagementCard(
    account: AccountManagementItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top row: Icon, Name, and Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color indicator with initials
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(parseAccountColor(account.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Account name and bank
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.bankName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onEdit,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = strings.edit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    FilledTonalButton(
                        onClick = onDelete,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = strings.delete,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats row: Transactions, Statements, Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Transactions count
                Column {
                    Text(
                        text = strings.transactions,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = account.transactionCount.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Statements count
                Column {
                    Text(
                        text = strings.statements,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = account.statementCount.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Balance
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = strings.netBalance,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    account.balance?.let { balance ->
                        Text(
                            text = formatBalance(balance),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (balance >= 0) Color(0xFF4CAF50) else Color(0xFFE57373)
                        )
                    } ?: Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // IBAN if available
            account.iban?.let { iban ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatIbanShort(iban),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    accountName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.deleteAccount,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(strings.deleteAccountConfirm.replace("%s", accountName))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(strings.delete)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun ClearAllDataDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.clearAllData,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(strings.clearAllDataConfirm)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(strings.clear)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun EditAccountDialog(
    accountName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    var editedName by remember { mutableStateOf(accountName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.editAccount,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                label = { Text(strings.accountName) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(editedName) },
                enabled = editedName.isNotBlank() && editedName != accountName
            ) {
                Text(strings.save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun ThemeSettingsCard(
    currentThemeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = strings.settings,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = strings.theme,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOption(
                    label = strings.themeLight,
                    selected = currentThemeMode == ThemeMode.LIGHT,
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeOption(
                    label = strings.themeDark,
                    selected = currentThemeMode == ThemeMode.DARK || currentThemeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Helper functions
private fun parseAccountColor(hexColor: String?): Color {
    if (hexColor == null) return Color(0xFF5C6BC0)

    return try {
        val hex = hexColor.removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (e: Exception) {
        Color(0xFF5C6BC0)
    }
}

private fun formatIbanShort(iban: String): String {
    return if (iban.length > 8) {
        "${iban.take(4)}...${iban.takeLast(4)}"
    } else {
        iban
    }
}

/*private fun formatBalance(amount: Double): String {
    val formatted = "%.2f".format(kotlin.math.abs(amount)).replace(".", ",")
    return if (amount >= 0) "+$formatted €" else "-$formatted €"
}*/

private fun formatBalance(amount: Double): String {
    val absAmount = kotlin.math.abs(amount)
    val rounded = kotlin.math.round(absAmount * 100) / 100
    val formatted = rounded.toString().replace(".", ",")
    return if (amount >= 0) "+$formatted €" else "-$formatted €"
}
