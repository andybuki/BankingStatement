package com.banking.statement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banking.statement.LocalStrings
import com.banking.statement.pdf.PdfPageRenderer
import com.banking.statement.ui.components.EyebrowLabel
import com.banking.statement.ui.theme.AppColors
import com.banking.statement.ui.theme.AppElevations
import com.banking.statement.ui.theme.AppRadii
import com.banking.statement.ui.theme.AppSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Candidate transaction for the "link to this page" manual-assignment flow.
 * Lightweight so the viewer doesn't depend on TransactionDisplay.
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
 * Reliability notes:
 *  - We render the target page first so it's available when we scroll, then
 *    re-scroll once that bitmap arrives. Lazy heights of unrendered pages
 *    use an A4-ish aspect ratio so the placeholder height closely matches
 *    the real page height — this prevents the scroll position from "drifting"
 *    once images load.
 *  - The matched page gets a thick amber outline + amber-tinted card so
 *    the user can find it at a glance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    fileName: String,
    initialPage: Int = 0,
    highlightSnippet: String? = null,
    highlightTitle: String? = null,
    /** "x,y,w,h" fractional page coords (0..1) for the line overlay. */
    highlightBbox: String? = null,
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

    // Per-page bitmap cache. Capped implicitly by Compose's recomposition,
    // but we also clear it on dispose to free large allocations.
    val pageCache = remember { mutableStateMapOf<Int, ImageBitmap>() }
    val scope = rememberCoroutineScope()

    var showLinkDialog by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(initialPage) }
    // True until we've successfully landed on `initialPage` after the
    // target bitmap is loaded; used to perform a corrective re-scroll once
    // real heights are known.
    var targetPageLandedOn by remember(initialPage, filePath) { mutableStateOf(false) }

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

    // Eagerly render the target page first so the highlight is available
    // by the time we scroll, then keep neighbours warm.
    LaunchedEffect(isOpen, pageCount, initialPage) {
        if (isOpen && pageCount > 0) {
            val widthPx = with(density) { 600.dp.toPx() }.toInt()
            // Target page first.
            val target = initialPage.coerceIn(0, pageCount - 1)
            ensureRendered(target, widthPx, renderer, pageCache)
            // Then a small neighbourhood for smooth scroll.
            for (offset in 1..2) {
                ensureRendered(target - offset, widthPx, renderer, pageCache)
                ensureRendered(target + offset, widthPx, renderer, pageCache)
            }
        }
    }

    // Initial scroll attempt — happens when the LazyColumn knows page count.
    LaunchedEffect(pageCount, initialPage, filePath) {
        if (pageCount > 0 && initialPage in 0 until pageCount) {
            listState.scrollToItem(initialPage)
        }
    }

    // Corrective re-scroll: after the target page bitmap is loaded the real
    // height is known, so the previous scrollToItem may have landed off by a
    // page. Re-scroll once the target bitmap appears.
    val targetBitmap = pageCache[initialPage]
    LaunchedEffect(targetBitmap, filePath) {
        if (!targetPageLandedOn && targetBitmap != null && pageCount > 0) {
            listState.scrollToItem(initialPage.coerceIn(0, pageCount - 1))
            targetPageLandedOn = true
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { currentPage = it }
    }

    Scaffold(
        containerColor = AppColors.SurfaceTint,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.HeaderText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "Page ${currentPage + 1} of $pageCount",
                                fontSize = 11.sp,
                                color = AppColors.HeaderSecondaryText
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = strings.close,
                            tint = AppColors.HeaderIcons
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.HeaderBackground,
                    titleContentColor = AppColors.HeaderText,
                    navigationIconContentColor = AppColors.HeaderIcons
                )
            )
        },
        floatingActionButton = {
            if (onLinkTransaction != null && linkableTransactions.isNotEmpty() && isOpen) {
                ExtendedFloatingActionButton(
                    onClick = { showLinkDialog = true },
                    containerColor = AppColors.Primary,
                    contentColor = Color.White,
                    text = {
                        Text(
                            text = "Link to this page",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    },
                    icon = { Icon(Icons.Filled.Link, contentDescription = null) }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(AppColors.SurfaceTint)
        ) {
            when {
                openFailed -> PdfUnavailableState(
                    reason = "This PDF could not be opened. The source file may have been deleted, or PDF viewing isn't supported on this platform yet.",
                    modifier = Modifier.align(Alignment.Center)
                )
                !isOpen -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = AppColors.Primary) }
                pageCount == 0 -> PdfUnavailableState(
                    reason = "This PDF has no pages.",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = AppSpacing.s3,
                            vertical = AppSpacing.s3
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
                    ) {
                        items(count = pageCount) { index ->
                            val bitmap = pageCache[index]
                            val isHighlightPage = index == initialPage && !highlightSnippet.isNullOrBlank()

                            // Lazy-render any page that becomes visible.
                            if (bitmap == null) {
                                LaunchedEffect(index, filePath) {
                                    val widthPx = with(density) { 600.dp.toPx() }.toInt()
                                    val rendered = withContext(Dispatchers.Default) {
                                        renderer.renderPage(index, widthPx)
                                    }
                                    if (rendered != null) {
                                        pageCache[index] = rendered
                                    }
                                }
                            }

                            PageItem(
                                pageIndex = index,
                                pageCount = pageCount,
                                bitmap = bitmap,
                                isHighlight = isHighlightPage,
                                highlightTitle = highlightTitle,
                                highlightSnippet = highlightSnippet,
                                highlightBbox = if (isHighlightPage) highlightBbox else null
                            )
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

/** Render and cache the page if not already cached and within range. */
private suspend fun ensureRendered(
    pageIndex: Int,
    widthPx: Int,
    renderer: PdfPageRenderer,
    cache: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, ImageBitmap>
) {
    if (pageIndex < 0 || pageIndex >= renderer.pageCount()) return
    if (cache.containsKey(pageIndex)) return
    val rendered = withContext(Dispatchers.Default) {
        renderer.renderPage(pageIndex, widthPx)
    }
    if (rendered != null) {
        cache[pageIndex] = rendered
    }
}

@Composable
private fun PageItem(
    pageIndex: Int,
    pageCount: Int,
    bitmap: ImageBitmap?,
    isHighlight: Boolean,
    highlightTitle: String?,
    highlightSnippet: String?,
    highlightBbox: String?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (isHighlight && !highlightSnippet.isNullOrBlank()) {
            HighlightBanner(
                title = highlightTitle ?: "Matched transaction",
                snippet = highlightSnippet
            )
            Spacer(Modifier.height(AppSpacing.s2))
        }

        val cardShape = RoundedCornerShape(AppRadii.lg)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isHighlight) AppElevations.md else AppElevations.xs,
                    shape = cardShape,
                    clip = false
                )
                .clip(cardShape)
                .background(Color.White)
                .then(
                    if (isHighlight) Modifier.border(3.dp, HighlightAmber, cardShape)
                    else Modifier.border(1.dp, AppColors.Divider, cardShape)
                )
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
                // Draw the per-line highlight overlay on top of the page
                // image. Bbox values are fractions of the page; we map
                // them onto the rendered image's content size below.
                val parsed = remember(highlightBbox) { parseBbox(highlightBbox) }
                if (isHighlight && parsed != null) {
                    HighlightOverlay(
                        bitmap = bitmap,
                        bbox = parsed,
                        modifier = Modifier
                            .matchParentSize()
                    )
                }
            } else {
                // Aspect ratio close to letter/A4 — keeps the lazy list's
                // estimated heights stable so scroll positions don't shift
                // when bitmaps eventually load.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f / 1.41f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Primary
                    )
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.s1))
        Text(
            text = "Page ${pageIndex + 1} / $pageCount",
            fontSize = 11.sp,
            color = AppColors.TextTertiary,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

/**
 * Translucent amber rectangle drawn over the rendered page image at the
 * coordinates of the matched line.
 *
 * Implementation note: the page image is drawn with [ContentScale.FillWidth],
 * which means the image fills the box's width and its height is
 * proportional to the bitmap's aspect ratio. Both the rendered image and
 * the bbox use fractional coords, so we scale the bbox by the box's
 * width and by `width * (bitmap.height / bitmap.width)` for the height
 * — that lines the rectangle up exactly with the line on the page.
 */
@Composable
private fun HighlightOverlay(
    bitmap: ImageBitmap,
    bbox: BboxFrac,
    modifier: Modifier = Modifier
) {
    val ratio = bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f)
    Canvas(modifier = modifier) {
        val w = size.width
        val imageDrawnHeight = w * ratio
        val rectLeft = bbox.x * w
        val rectTop = bbox.y * imageDrawnHeight
        val rectWidth = bbox.w * w
        val rectHeight = bbox.h * imageDrawnHeight

        // Translucent fill so the underlying text remains readable.
        drawRect(
            color = HighlightAmber.copy(alpha = 0.30f),
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectWidth, rectHeight)
        )
        // Solid amber border for prominence.
        drawRect(
            color = HighlightAmber,
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectWidth, rectHeight),
            style = Stroke(width = 2.5f)
        )
    }
}

/** Parsed "x,y,w,h" fractional bbox. */
private data class BboxFrac(val x: Float, val y: Float, val w: Float, val h: Float)

private fun parseBbox(raw: String?): BboxFrac? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split(',')
    if (parts.size != 4) return null
    return try {
        val x = parts[0].toFloat().coerceIn(0f, 1f)
        val y = parts[1].toFloat().coerceIn(0f, 1f)
        val w = parts[2].toFloat().coerceIn(0f, 1f)
        val h = parts[3].toFloat().coerceIn(0f, 1f)
        if (w <= 0f || h <= 0f) null else BboxFrac(x, y, w, h)
    } catch (e: NumberFormatException) {
        null
    }
}

private val HighlightAmber = Color(0xFFF59E0B)        // amber-500
private val HighlightAmberTint = Color(0xFFFFF7ED)    // amber-50ish

@Composable
private fun HighlightBanner(title: String, snippet: String) {
    val shape = RoundedCornerShape(AppRadii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HighlightAmberTint)
            .border(1.dp, HighlightAmber.copy(alpha = 0.6f), shape)
            .padding(horizontal = AppSpacing.s3, vertical = AppSpacing.s3 - 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "▸",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = HighlightAmber
        )
        Spacer(Modifier.width(AppSpacing.s2))
        Column(modifier = Modifier.weight(1f)) {
            EyebrowLabel(
                text = title,
                color = Color(0xFF92400E) // amber-800
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = snippet,
                fontSize = 13.sp,
                color = Color(0xFF78350F), // amber-900
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PdfUnavailableState(reason: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(AppSpacing.s8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.s3)
    ) {
        Text(text = "📄", fontSize = 44.sp)
        Text(
            text = reason,
            fontSize = 13.sp,
            color = AppColors.TextSecondary
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
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(AppElevations.md, RoundedCornerShape(AppRadii.xl), clip = false)
                .clip(RoundedCornerShape(AppRadii.xl))
                .background(AppColors.CardBackground)
                .padding(AppSpacing.s5)
        ) {
            EyebrowLabel(text = "Manual link")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Link a transaction",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Pick the parsed transaction shown on page ${page + 1}. Next time you open this transaction the viewer will jump straight here.",
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )

            Spacer(Modifier.height(AppSpacing.s3))

            if (candidates.isEmpty()) {
                Text(
                    text = "All transactions from this statement are already linked to a page.",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.s2)
                ) {
                    items(count = candidates.size) { index ->
                        val tx = candidates[index]
                        LinkCandidateRow(tx = tx, onClick = { onPick(tx) })
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.s3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Primary)
                ) {
                    Text(strings.close, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun LinkCandidateRow(tx: LinkableTransaction, onClick: () -> Unit) {
    val shape = RoundedCornerShape(AppRadii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.SurfaceTint)
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.s3, vertical = AppSpacing.s3 - 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.description,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val status = if (tx.currentlyLinkedPage != null)
                "${tx.date}  ·  linked to page ${tx.currentlyLinkedPage + 1}"
            else
                "${tx.date}  ·  not yet linked"
            Text(
                text = status,
                fontSize = 11.sp,
                color = AppColors.TextSecondary
            )
        }
        Spacer(Modifier.width(AppSpacing.s2))
        Text(
            text = formatLinkAmount(tx.amount, tx.currency),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (tx.amount >= 0) AppColors.Income else AppColors.Expenses
        )
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
