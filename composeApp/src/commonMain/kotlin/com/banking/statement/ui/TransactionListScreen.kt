package com.banking.statement.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banking.statement.LocalStrings
import com.banking.statement.TransactionDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    transactions: List<TransactionDisplay>,
    accounts: List<AccountFilterOption> = emptyList(),
    selectedAccountId: Long? = null,                        // Controlled by parent
    onAccountSelected: (Long?) -> Unit = {},                // Parent callback
    customCategories: List<com.banking.statement.categorization.CustomCategory> = emptyList(),
    onBackClick: (() -> Unit)? = null,
    onShare: ((ExportFormat, List<TransactionDisplay>, String?) -> Unit)? = null,
    onCategoryChange: ((TransactionDisplay, TransactionCategory) -> Unit)? = null,
    onCustomCategoryChange: ((TransactionDisplay, Long) -> Unit)? = null,
    onManageCategories: (() -> Unit)? = null
) {
    val strings = LocalStrings.current

    // Local UI state for dropdown / search etc.
    var dropdownExpanded by remember { mutableStateOf(false) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showCategoryPicker by remember { mutableStateOf<TransactionDisplay?>(null) }

    // Filter transactions based on selected account and search query
    val filteredTransactions = remember(transactions, selectedAccountId, searchQuery) {
        var result = if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase().trim()
            result = result.filter {
                (it.description?.lowercase()?.contains(query) ?: false)
                        || (it.counterparty?.lowercase()?.contains(query) ?: false)
            }
        }
        result
    }

    // Account dropdown UI — call onAccountSelected when user picks an account
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
        ) {
            TextField(
                value = accounts.firstOrNull { it.id == selectedAccountId }?.name ?: strings.allAccounts,
                onValueChange = {},
                readOnly = true,
                label = { Text(strings.account) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) }
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(strings.allAccounts) },
                    onClick = {
                        dropdownExpanded = false
                        onAccountSelected(null)
                    }
                )
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            dropdownExpanded = false
                            onAccountSelected(account.id)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredTransactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "${strings.noTransactions}.\n${strings.importFirst}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredTransactions) { transaction ->
                    TransactionItem(
                        transaction = transaction,
                        onClick = if (onCategoryChange != null) {
                            { showCategoryPicker = transaction }
                        } else null
                    )
                }
            }
        }

        // Category picker dialog
        showCategoryPicker?.let { transaction ->
            CategoryPickerDialog(
                currentCategory = transaction.category,
                customCategories = customCategories,
                onDismiss = { showCategoryPicker = null },
                onCategorySelected = { category ->
                    onCategoryChange?.invoke(transaction, category)
                    showCategoryPicker = null
                },
                onCustomCategorySelected = { customId ->
                    onCustomCategoryChange?.invoke(transaction, customId)
                    showCategoryPicker = null
                },
                onManageCategories = onManageCategories
            )
        }
    }
}
