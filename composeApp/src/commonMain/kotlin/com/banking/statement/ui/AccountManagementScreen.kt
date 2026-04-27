package com.banking.statement.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bankingstatement.composeapp.generated.resources.Res
import bankingstatement.composeapp.generated.resources.back
import com.banking.statement.LocalStrings
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.components.MLSetGroup
import com.banking.statement.ui.components.MLSetRow
import com.banking.statement.ui.components.MoneyLupeChoiceDialog
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import com.banking.statement.ui.theme.ThemeMode
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

enum class StatementSortOrder {
    NEWEST_FIRST, OLDEST_FIRST, BY_PERIOD, BY_NAME
}

data class StatementDisplayItem(
    val id: Long,
    val fileName: String,
    val bankName: String,
    val period: String?,
    val importDate: Long,
    val sourceType: String
)

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
    val balance: Double?,
    val statements: List<StatementDisplayItem> = emptyList()
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
    onDeleteStatement: (Long) -> Unit = {},
    statementExpandedMap: Map<Long, Boolean> = emptyMap(),
    statementSortMap: Map<Long, StatementSortOrder> = emptyMap(),
    onStatementExpandedChange: (Long, Boolean) -> Unit = { _, _ -> },
    onStatementSortOrderChange: (Long, StatementSortOrder) -> Unit = { _, _ -> },
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    biometricLockEnabled: Boolean = false,
    biometricAvailable: Boolean = false,
    onBiometricLockChange: (Boolean) -> Unit = {},
    onEmailClick: (String) -> Unit = {},
    remindersEnabled: Boolean = true,
    onRemindersEnabledChange: (Boolean) -> Unit = {},
    pdfAccessEnabled: Boolean = true,
    onPdfAccessEnabledChange: (Boolean) -> Unit = {},
    onShareApp: () -> Unit = {}
) {
    val strings = LocalStrings.current
    var showDeleteDialog by remember { mutableStateOf<AccountManagementItem?>(null) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<AccountManagementItem?>(null) }
    var showThemePicker by remember { mutableStateOf(false) }

    // Settings tab: navy header is rendered by App.kt; this pane sits on
    // SurfaceTint like every other tab, with eyebrow-grouped sections that
    // mirror ui_kits/mobile/ScreensB.MLSettingsScreen.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.SurfaceTint)
            .padding(horizontal = AppSpacing.s4),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s3 + 2.dp)
    ) {
        item { Spacer(modifier = Modifier.height(AppSpacing.s2)) }

        if (accounts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
                        .clip(RoundedCornerShape(AppRadii.lg))
                        .background(AppColors.CardBackground)
                        .padding(vertical = AppSpacing.s10, horizontal = AppSpacing.s4),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.noAccounts,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.TextPrimary
                        )
                        Text(
                            text = strings.noAccountsHint,
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                }
            }
        } else {
            // Accounts section
            item(key = "accounts-eyebrow") {
                AccountsSectionHeader(
                    accountCount = accounts.size,
                    totalStatements = accounts.sumOf { it.statementCount }
                )
            }
            items(accounts, key = { "acc-${it.id}" }) { account ->
                AccountManagementCard(
                    account = account,
                    onEdit = { showEditDialog = account },
                    onDelete = { showDeleteDialog = account },
                    onDeleteStatement = onDeleteStatement,
                    expanded = statementExpandedMap[account.id] ?: false,
                    onExpandedChange = { onStatementExpandedChange(account.id, it) },
                    sortOrder = statementSortMap[account.id] ?: StatementSortOrder.NEWEST_FIRST,
                    onSortOrderChange = { onStatementSortOrderChange(account.id, it) }
                )
            }
        }

        // Appearance section — Theme + Reminders as MLSetRows
        item(key = "appearance") {
            MLSetGroup(eyebrow = "Appearance") {
                MLSetRow(
                    icon = Icons.Filled.AutoAwesome,
                    iconTint = Color(0xFFF59E0B),
                    title = strings.theme,
                    subtitle = when (currentThemeMode) {
                        ThemeMode.LIGHT -> strings.themeLight
                        ThemeMode.DARK -> strings.themeDark
                        ThemeMode.SYSTEM -> strings.themeSystem
                    },
                    onClick = { showThemePicker = true },
                    isFirst = true,
                    trailing = {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(AppColors.SurfaceTint)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = when (currentThemeMode) {
                                    ThemeMode.LIGHT -> strings.themeLight
                                    ThemeMode.DARK -> strings.themeDark
                                    ThemeMode.SYSTEM -> strings.themeSystem
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.TextSecondary
                            )
                        }
                    }
                )
                MLSetRow(
                    icon = Icons.Filled.NotificationsActive,
                    iconTint = Color(0xFFEC4899),
                    title = strings.reminders,
                    subtitle = strings.remindersDescription.take(60),
                    trailing = {
                        Switch(
                            checked = remindersEnabled,
                            onCheckedChange = onRemindersEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppColors.Primary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = AppColors.Disabled
                            )
                        )
                    }
                )
            }
        }

        // Security section — biometric + PDF retention live here together
        // since both control how sensitive statement data is kept on-device.
        item(key = "security") {
            MLSetGroup(eyebrow = strings.security) {
                if (biometricAvailable) {
                    MLSetRow(
                        icon = Icons.Filled.Lock,
                        iconTint = Color(0xFF6366F1),
                        title = strings.biometricLock,
                        subtitle = strings.biometricLockDescription,
                        isFirst = true,
                        trailing = {
                            Switch(
                                checked = biometricLockEnabled,
                                onCheckedChange = onBiometricLockChange,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AppColors.Primary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = AppColors.Disabled
                                )
                            )
                        }
                    )
                }
                MLSetRow(
                    icon = Icons.Filled.Description,
                    iconTint = Color(0xFFEF4444),
                    title = "Source PDF access",
                    subtitle = if (pdfAccessEnabled)
                        "Original statement PDFs are kept on-device so you can open them from a transaction."
                    else
                        "Imported PDFs are not retained. View source PDF is unavailable.",
                    isFirst = !biometricAvailable,
                    trailing = {
                        Switch(
                            checked = pdfAccessEnabled,
                            onCheckedChange = onPdfAccessEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppColors.Primary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = AppColors.Disabled
                            )
                        )
                    }
                )
            }
        }

        // About section
        item(key = "about") {
            MLSetGroup(eyebrow = strings.contactTitle) {
                MLSetRow(
                    icon = Icons.Filled.IosShare,
                    iconTint = AppColors.Primary,
                    title = strings.shareApp,
                    subtitle = "Tell a friend",
                    onClick = onShareApp,
                    showChevron = true,
                    isFirst = true
                )
                MLSetRow(
                    icon = Icons.Filled.Description,
                    iconTint = Color(0xFF64748B),
                    title = strings.contactTitle,
                    subtitle = strings.contactEmail,
                    onClick = { onEmailClick(strings.contactEmail) },
                    showChevron = true
                )
            }
        }

        // Danger Zone
        if (accounts.isNotEmpty()) {
            item(key = "danger-eyebrow") {
                EyebrowLabel(
                    text = strings.dangerZone,
                    color = AppColors.Expenses,
                    modifier = Modifier.padding(start = 4.dp, top = AppSpacing.s2)
                )
            }
            item(key = "danger") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
                        .clip(RoundedCornerShape(AppRadii.lg))
                        .background(AppColors.CardBackground)
                        .padding(AppSpacing.s4)
                ) {
                    Text(
                        text = strings.clearAllDataConfirm,
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(bottom = AppSpacing.s3)
                    )
                    Button(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppRadii.md),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Expenses,
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = strings.clearAllData,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Version footer
        item(key = "footer") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.s5),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${strings.appName} · v2.4.0",
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
                Text(
                    text = strings.contactEmail,
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
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

    // Theme picker dialog
    if (showThemePicker) {
        MoneyLupeChoiceDialog(
            eyebrow = "Appearance",
            title = strings.theme,
            options = listOf(
                ThemeMode.SYSTEM to strings.themeSystem,
                ThemeMode.LIGHT to strings.themeLight,
                ThemeMode.DARK to strings.themeDark
            ),
            selected = currentThemeMode,
            onSelect = {
                onThemeModeChange(it)
                showThemePicker = false
            },
            onDismiss = { showThemePicker = false }
        )
    }
}

@Composable
private fun AccountsSectionHeader(accountCount: Int, totalStatements: Long) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = AppSpacing.s2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        EyebrowLabel(text = strings.manageAccounts)
        Text(
            text = "$accountCount · $totalStatements ${strings.statements.lowercase()}",
            fontSize = 11.sp,
            color = AppColors.TextTertiary
        )
    }
}

@Composable
private fun AccountManagementCard(
    account: AccountManagementItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteStatement: (Long) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    sortOrder: StatementSortOrder,
    onSortOrderChange: (StatementSortOrder) -> Unit
) {
    val strings = LocalStrings.current
    var statementToDelete by remember { mutableStateOf<StatementDisplayItem?>(null) }

    val sortedStatements = remember(account.statements, sortOrder) {
        when (sortOrder) {
            StatementSortOrder.NEWEST_FIRST -> account.statements.sortedByDescending { it.importDate }
            StatementSortOrder.OLDEST_FIRST -> account.statements.sortedBy { it.importDate }
            StatementSortOrder.BY_PERIOD -> account.statements.sortedByDescending { it.period ?: "" }
            StatementSortOrder.BY_NAME -> account.statements.sortedBy { it.fileName.lowercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppElevations.xs, RoundedCornerShape(AppRadii.lg), clip = false)
            .clip(RoundedCornerShape(AppRadii.lg))
            .background(AppColors.CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.s4)
        ) {
            // Top row: Icon, Name, and Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand-style avatar — squircle (10.dp radius) tinted with the
                // account color, mirroring MLSetRow icons in the kit.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(parseAccountColor(account.color)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = account.name.take(2).uppercase(),
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Account name + IBAN preview as supporting line.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.bankName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    account.iban?.let { iban ->
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = formatIbanShort(iban),
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Compact icon actions instead of bulky tonal buttons
                AccountActionIcon(
                    icon = Icons.Filled.Edit,
                    contentDescription = strings.edit,
                    tint = AppColors.Primary,
                    onClick = onEdit
                )
                Spacer(modifier = Modifier.width(4.dp))
                AccountActionIcon(
                    icon = Icons.Filled.Delete,
                    contentDescription = strings.delete,
                    tint = AppColors.Expenses,
                    onClick = onDelete
                )
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
                        color = AppColors.TextSecondary
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
                        color = AppColors.TextSecondary
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
                        color = AppColors.TextSecondary
                    )
                    account.balance?.let { balance ->
                        Text(
                            text = formatBalance(balance),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (balance >= 0) AppColors.Income else AppColors.Expenses
                        )
                    } ?: Text(
                        text = "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppColors.TextTertiary
                    )
                }
            }

            // Expand/collapse button for statements list
            if (account.statements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = AppColors.Divider)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onExpandedChange(!expanded) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (expanded) strings.hideStatements else strings.showStatements,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = expanded) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Sort chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(StatementSortOrder.NEWEST_FIRST, StatementSortOrder.OLDEST_FIRST).forEach { order ->
                                val label = when (order) {
                                    StatementSortOrder.NEWEST_FIRST -> strings.sortNewestFirst
                                    StatementSortOrder.OLDEST_FIRST -> strings.sortOldestFirst
                                    StatementSortOrder.BY_PERIOD -> strings.sortByPeriod
                                    StatementSortOrder.BY_NAME -> strings.sortByName
                                }
                                FilterChip(
                                    selected = sortOrder == order,
                                    onClick = { onSortOrderChange(order) },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Statement rows
                        sortedStatements.forEach { statement ->
                            StatementRow(
                                statement = statement,
                                onDelete = { statementToDelete = statement }
                            )
                            HorizontalDivider(
                                color = AppColors.SurfaceTint,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    statementToDelete?.let { stmt ->
        DeleteStatementDialog(
            fileName = stmt.fileName,
            onConfirm = {
                onDeleteStatement(stmt.id)
                statementToDelete = null
            },
            onDismiss = { statementToDelete = null }
        )
    }
}

@Composable
private fun AccountActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun StatementRow(
    statement: StatementDisplayItem,
    onDelete: () -> Unit
) {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            tint = AppColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statement.fileName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                statement.period?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary
                    )
                }
                Text(
                    text = "${strings.importedOn} ${formatImportDate(statement.importDate)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextTertiary
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = strings.delete,
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// Helper functions
private fun formatImportDate(epochSeconds: Long): String {
    return try {
        val instant = Instant.fromEpochSeconds(epochSeconds)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "%02d.%02d.%d".format(local.dayOfMonth, local.monthNumber, local.year)
    } catch (_: Exception) {
        ""
    }
}

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
