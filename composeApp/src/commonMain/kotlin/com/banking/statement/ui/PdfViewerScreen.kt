package com.banking.statement.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banking.statement.LocalStrings
import com.banking.statement.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Candidate transaction for the "link to this page" manual-assignment flow.
 * A lightweight display record so the PDF viewer doesn't need to depend on
 * TransactionDisplay's category machinery.
 */
data class LinkableTransaction(
    val id: Long,
    val date: String,
    val description: String,
    val amount: Double,
    val currency: String,
    val currentlyLinkedPage: Int?
)

/**
 * Full-screen in-app PDF viewer used by the "receipt view" feature.
 *
 * Opens the statement PDF at [filePath] and scrolls to [initialPage]; if
 * [highlightSnippet] is non-null, a banner above that page shows what line
 * we believe the transaction came from.
 *
 * When [linkableTransactions] is non-empty the viewer shows a "Link a
 * transaction to this page" FAB that lets the user manually assign the
 * current page to a parsed transaction — this is the fallback when
 * auto-extraction couldn't figure out which page a transaction belongs to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    fileName: String,
    initialPage: Int = 0,
    highlightSnippet: String? = null,
    highlightTitle: String? = null,
    linkableTransactions: List<LinkableTransaction> = emptyList(),
    onLinkTransaction: ((transactionId: Long, page: Int) -> Unit)? = null,
    onClose: () -> Unit
) {
    val strings = LocalStrings.current
    val renderer = remember { PdfPageRenderer() }
    var isOpen by remember { mutableStateOf(false) }
    var pageCount by remember { mutableStateOf(0) }
    var openFailed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Simple page-image cache, capped to keep memory in check on long PDFs.
    val pageCache = remember { mutableStateMapOf<Int, ImageBitmap>() }
    val scope = rememberCoroutineScope()

    var showLinkDialog by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(initialPage) }

    DisposableEffect(filePath) {
        val ok = renderer.open(filePath)
        isOpen = ok
        openFailed = !ok
        pageCount = if (ok) renderer.pageCount() else 0
        onDispose {
            renderer.close()
            pageCache.clear()
        }
    }

    // Scroll to the initial page once pages are known.
    LaunchedEffect(pageCount, initialPage) {
        if (pageCount > 0 && initialPage in 0 until pageCount) {
            listState.scrollToItem(initialPage)
        }
    }

    // Track the currently visible page for the "link to this page" button.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { currentPage = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "Page ${currentPage + 1} / $pageCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = strings.close)
                    }
                }
            )
        },
        floatingActionButton = {
            if (onLinkTransaction != null && linkableTransactions.isNotEmpty() && isOpen) {
                ExtendedFloatingActionButton(
                    onClick = { showLinkDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    text = { Text("Link to this page") },
                    icon = { Text("🔗") }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            when {
                openFailed -> PdfUnavailableState(
                    reason = "This PDF could not be opened. The source file may have been deleted or PDF viewing is not supported on this platform.",
                    modifier = Modifier.align(Alignment.Center)
                )
                !isOpen -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                pageCount == 0 -> PdfUnavailableState(
                    reason = "This PDF is empty.",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(count = pageCount) { index ->
                            val bitmap = pageCache[index]
                            val isHighlightPage = index == initialPage && highlightSnippet != null

                            if (bitmap == null) {
                                // Kick off a render for this page, sized to the viewport.
                                LaunchedEffect(index) {
                                    scope.launch {
                                        val widthPx = with(density) { 600.dp.toPx() }.toInt()
                                        val rendered = withContext(Dispatchers.Default) {
                                            renderer.renderPage(index, widthPx)
                                        }
                                        if (rendered != null) {
                                            pageCache[index] = rendered
                                        }
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                            ) {
                                if (isHighlightPage && !highlightSnippet.isNullOrBlank()) {
                                    HighlightBanner(
                                        title = highlightTitle ?: "Matched transaction",
                                        snippet = highlightSnippet
                                    )
                                    Spacer(Modifier.height(8.dp))
                                }

                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Page ${index + 1}",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(400.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Page ${index + 1} / $pageCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLinkDialog && onLinkTransaction != null) {
        LinkTransactionDialog(
            page = currentPage,
            candidates = linkableTransactions,
            onPick = { tx ->
                onLinkTransaction(tx.id, currentPage)
                showLinkDialog = false
            },
            onDismiss = { showLinkDialog = false }
        )
    }
}

@Composable
private fun HighlightBanner(title: String, snippet: String) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun PdfUnavailableState(reason: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "📄",
            style = MaterialTheme.typography.displayMedium
        )
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LinkTransactionDialog(
    page: Int,
    candidates: List<LinkableTransaction>,
    onPick: (LinkableTransaction) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Link a transaction",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Choose the parsed transaction that this page shows. The viewer will jump to page ${page + 1} next time you open it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = "All transactions from this statement are already linked to a page.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(count = candidates.size) { index ->
                        val tx = candidates[index]
                        LinkCandidateRow(
                            tx = tx,
                            onClick = { onPick(tx) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strings.close) }
        }
    )
}

@Composable
private fun LinkCandidateRow(tx: LinkableTransaction, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val status = when {
                    tx.currentlyLinkedPage != null -> "${tx.date}  ·  linked to page ${tx.currentlyLinkedPage + 1}"
                    else -> "${tx.date}  ·  not yet linked"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatLinkAmount(tx.amount, tx.currency),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (tx.amount >= 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatLinkAmount(amount: Double, currency: String): String {
    val symbol = when (currency) {
        "EUR" -> "€"
        "USD" -> "$"
        "GBP" -> "£"
        else -> currency
    }
    val abs = kotlin.math.abs(amount)
    val rounded = kotlin.math.round(abs * 100) / 100
    val formatted = rounded.toString().replace(".", ",")
    return if (amount >= 0) "+$formatted $symbol" else "-$formatted $symbol"
}
