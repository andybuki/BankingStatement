package com.banking.statement.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.unit.Dp
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
 * Full-screen in-app PDF viewer used by the "receipt view" feature.
 *
 *  - Opens the statement at [initialPage] and re-scrolls to it once the
 *    target page bitmap loads (lazy heights stabilise after the real
 *    image arrives).
 *  - Draws a translucent amber rectangle directly on the line a
 *    transaction was extracted from when [highlightBbox] is provided.
 *  - Pinch-style zoom isn't supported; instead the top bar exposes
 *    "+" / "−" buttons that re-render pages at higher resolution and
 *    let the user scroll horizontally inside each page card.
 *  - A prominent "Back to Activity" button at the bottom closes the
 *    viewer (same as the X in the top bar, but easier to thumb-reach).
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
    onClose: () -> Unit
) {
    val strings = LocalStrings.current
    val renderer = remember { PdfPageRenderer() }
    var isOpen by remember { mutableStateOf(false) }
    var pageCount by remember { mutableStateOf(0) }
    var openFailed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // Per-page bitmap cache. Keyed implicitly by `zoomLevel` because
    // changing zoom invalidates the cache (we render at a new resolution).
    val pageCache = remember { mutableStateMapOf<Int, ImageBitmap>() }
    val scope = rememberCoroutineScope()

    var currentPage by remember { mutableStateOf(initialPage) }
    var targetPageLandedOn by remember(initialPage, filePath) { mutableStateOf(false) }

    // Zoom level: 1.0 = page fills viewport width. Each step changes by
    // 25%, capped so very large PDFs don't run the device out of memory.
    var zoomLevel by remember { mutableStateOf(1f) }

    // When zoom changes, drop cached bitmaps so they re-render at the
    // new resolution.
    LaunchedEffect(zoomLevel) { pageCache.clear() }

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

    LaunchedEffect(pageCount, initialPage, filePath) {
        if (pageCount > 0 && initialPage in 0 until pageCount) {
            listState.scrollToItem(initialPage)
        }
    }

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
                actions = {
                    // Zoom out
                    IconButton(
                        onClick = {
                            zoomLevel = (zoomLevel - 0.25f).coerceAtLeast(MIN_ZOOM)
                        },
                        enabled = zoomLevel > MIN_ZOOM
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Zoom out",
                            tint = if (zoomLevel > MIN_ZOOM) AppColors.HeaderIcons
                            else AppColors.HeaderIcons.copy(alpha = 0.4f)
                        )
                    }
                    Text(
                        text = "${(zoomLevel * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.HeaderText,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    // Zoom in
                    IconButton(
                        onClick = {
                            zoomLevel = (zoomLevel + 0.25f).coerceAtMost(MAX_ZOOM)
                        },
                        enabled = zoomLevel < MAX_ZOOM
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Zoom in",
                            tint = if (zoomLevel < MAX_ZOOM) AppColors.HeaderIcons
                            else AppColors.HeaderIcons.copy(alpha = 0.4f)
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
            if (isOpen) {
                ExtendedFloatingActionButton(
                    onClick = onClose,
                    containerColor = AppColors.Primary,
                    contentColor = Color.White,
                    text = {
                        Text(
                            text = "Back to Activity",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    },
                    icon = { Icon(Icons.Filled.ArrowBack, contentDescription = null) }
                )
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(AppColors.SurfaceTint)
        ) {
            val viewportWidthDp: Dp = maxWidth - AppSpacing.s3 * 2
            val pageWidthDp: Dp = viewportWidthDp * zoomLevel

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

                            if (bitmap == null) {
                                LaunchedEffect(index, filePath, zoomLevel) {
                                    val widthPx = with(density) { pageWidthDp.toPx() }.toInt()
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
                                pageWidthDp = pageWidthDp,
                                viewportWidthDp = viewportWidthDp,
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
}

private const val MIN_ZOOM: Float = 1f
private const val MAX_ZOOM: Float = 3f

@Composable
private fun PageItem(
    pageIndex: Int,
    pageCount: Int,
    bitmap: ImageBitmap?,
    pageWidthDp: Dp,
    viewportWidthDp: Dp,
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
        // Outer scroller — when zoomed in the page is wider than the
        // viewport so the user can pan horizontally.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .width(pageWidthDp)
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
                        modifier = Modifier
                            .width(pageWidthDp)
                    )
                    val parsed = remember(highlightBbox) { parseBbox(highlightBbox) }
                    if (isHighlight && parsed != null) {
                        HighlightOverlay(
                            bitmap = bitmap,
                            bbox = parsed,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .width(pageWidthDp)
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

        drawRect(
            color = HighlightAmber.copy(alpha = 0.30f),
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectWidth, rectHeight)
        )
        drawRect(
            color = HighlightAmber,
            topLeft = Offset(rectLeft, rectTop),
            size = Size(rectWidth, rectHeight),
            style = Stroke(width = 2.5f)
        )
    }
}

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

private val HighlightAmber = Color(0xFFF59E0B)
private val HighlightAmberTint = Color(0xFFFFF7ED)

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
                color = Color(0xFF92400E)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = snippet,
                fontSize = 13.sp,
                color = Color(0xFF78350F),
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
