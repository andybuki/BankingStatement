package com.bankwise.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bankwise.app.LocalStrings

/**
 * Dedicated screen for displaying top merchants and spending trends
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantsScreen(
    transactions: List<TransactionDisplay>,
    accounts: List<AccountFilterOption> = emptyList(),
    selectedAccountId: Long? = null,  // Controlled by App level
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current

    // Filter transactions by selected account
    val filteredTransactions = remember(transactions, selectedAccountId) {
        if (selectedAccountId == null) {
            transactions
        } else {
            transactions.filter { it.accountId == selectedAccountId }
        }
    }

    // Calculate merchant history from filtered transactions
    val merchantHistory = remember(filteredTransactions) {
        calculateMerchantHistoryFromTransactions(filteredTransactions)
    }

    // Calculate merchant trends
    val merchantTrends = remember(filteredTransactions) {
        calculateMerchantTrendsFromTransactions(filteredTransactions)
    }

    // Calculate total spending across all merchants
    val totalMerchantSpending = remember(merchantHistory) {
        merchantHistory.sumOf { it.totalSpending }
    }

    val totalMerchantTransactions = remember(merchantHistory) {
        merchantHistory.sumOf { it.totalTransactions }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        if (merchantHistory.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.noMerchantData,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Summary Card
                item {
                    MerchantSummaryCard(
                        merchantCount = merchantHistory.size,
                        totalSpending = totalMerchantSpending,
                        totalTransactions = totalMerchantTransactions
                    )
                }

                // Trends Section (if we have trend data)
                if (merchantTrends.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = strings.trend,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(merchantTrends.take(5)) { trend ->
                        MerchantTrendItem(trend)
                    }
                }

                // All Merchants Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = strings.topMerchants,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(merchantHistory) { history ->
                    MerchantHistoryItem(history)
                }
            }
        }
    }
}

/**
 * Summary card showing overall merchant statistics
 */
@Composable
private fun MerchantSummaryCard(
    merchantCount: Int,
    totalSpending: Double,
    totalTransactions: Int
) {
    val strings = LocalStrings.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryStatItem(
                value = merchantCount.toString(),
                label = strings.tabMerchants
            )
            SummaryStatItem(
                value = formatCurrency(totalSpending),
                label = strings.totalSpent
            )
            SummaryStatItem(
                value = totalTransactions.toString(),
                label = strings.transactions
            )
        }
    }
}

@Composable
private fun SummaryStatItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}
