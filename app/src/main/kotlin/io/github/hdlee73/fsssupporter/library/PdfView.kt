package io.github.hdlee73.fsssupporter.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

/**
 * 화면에 띄운 PDF 한 개. 쪽 그림은 보일 때만 그리고, 최근 몇 쪽만 기억해 메모리를 아낀다.
 * PDFBox 그리기는 한 번에 한 쪽씩만(스레드 하나) 한다.
 */
class PdfDoc private constructor(val file: File, private val doc: PDDocument) {
    private val renderer = PDFRenderer(doc).apply { isSubsamplingAllowed = true }
    private val lock = Mutex()
    private val cache = object : LinkedHashMap<Pair<Int, Int>, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, ImageBitmap>?) = size > 14
    }

    val pageCount: Int = doc.numberOfPages

    /** 쪽의 가로/세로 비율 */
    fun ratio(page: Int): Float = runCatching {
        val p = doc.getPage(page)
        val box = p.cropBox
        val rotated = p.rotation % 180 != 0
        if (rotated) box.height / box.width else box.width / box.height
    }.getOrDefault(0.707f)

    /** [widthPx] 너비에 맞춰 쪽을 그린다(같은 크기는 기억해 둔 그림을 쓴다). */
    suspend fun render(page: Int, widthPx: Int): ImageBitmap? {
        val bucket = (widthPx / 100 + 1) * 100
        synchronized(cache) { cache[page to bucket] }?.let { return it }
        return withContext(Dispatchers.IO) {
            lock.withLock {
                synchronized(cache) { cache[page to bucket] }?.let { return@withLock it }
                runCatching {
                    val ptWidth = doc.getPage(page).let { if (it.rotation % 180 != 0) it.cropBox.height else it.cropBox.width }
                    val scale = (bucket / ptWidth).coerceIn(0.5f, 4f)
                    renderer.renderImage(page, scale, ImageType.RGB).toComposeImageBitmap()
                }.getOrNull()?.also { img -> synchronized(cache) { cache[page to bucket] = img } }
            }
        }
    }

    fun close() { runCatching { doc.close() } }

    companion object {
        suspend fun open(file: File): PdfDoc? = withContext(Dispatchers.IO) {
            runCatching { PdfDoc(file, Loader.loadPDF(file)) }.getOrNull()
        }
    }
}

/** PDF 파일을 열어 두고 화면을 떠나면 닫는다. 파일이 바뀌면 다시 연다. */
@Composable
fun rememberPdf(path: String, version: Long): PdfDoc? {
    val state = produceState<PdfDoc?>(null, path, version) {
        value = if (path.isBlank()) null else PdfDoc.open(File(path))
    }
    val doc = state.value
    DisposableEffect(doc) { onDispose { doc?.close() } }
    return doc
}

@Stable
class PdfViewerState {
    val list = LazyListState()
    /** 1 = 화면 너비에 맞춤(최대 960dp) */
    var zoom by mutableFloatStateOf(1f)
}

/**
 * 쪽을 위에서 아래로 이어 보여 주는 PDF 보기. [pages]가 비면 모든 쪽.
 * 회색 바탕 위 흰 종이처럼 보이게 한다.
 */
@Composable
fun PdfPages(
    doc: PdfDoc,
    pages: List<Int>,
    state: PdfViewerState,
    modifier: Modifier = Modifier,
    toolbarExtra: @Composable () -> Unit = {},
) {
    val shown = remember(doc, pages) { pages.ifEmpty { (0 until doc.pageCount).toList() } }
    val scope = rememberCoroutineScope()
    val current by remember(shown) { derivedStateOf { shown.getOrNull(state.list.firstVisibleItemIndex) ?: 0 } }
    Column(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("${current + 1} / ${doc.pageCount}쪽" + if (pages.isNotEmpty()) "  (이 부분 ${shown.size}쪽)" else "",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            toolbarExtra()
            IconButton(onClick = { state.zoom = (state.zoom - 0.15f).coerceAtLeast(0.4f) }) { Icon(Icons.Outlined.Remove, "축소", Modifier.size(18.dp)) }
            Text("${(state.zoom * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp))
            IconButton(onClick = { state.zoom = (state.zoom + 0.15f).coerceAtMost(2.5f) }) { Icon(Icons.Outlined.Add, "확대", Modifier.size(18.dp)) }
            IconButton(onClick = { state.zoom = 1f }) { Icon(Icons.Outlined.FitScreen, "너비에 맞춤", Modifier.size(18.dp)) }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageWidth = (minOf(maxWidth - 48.dp, 960.dp) * state.zoom).coerceAtLeast(200.dp)
            val px = with(LocalDensity.current) { pageWidth.toPx().toInt() }
            LazyColumn(
                state = state.list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(shown, key = { it }) { p ->
                    PdfPage(doc, p, px, Modifier.width(pageWidth))
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(state.list), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
    // 보여 줄 쪽이 바뀌면 맨 위로
    LaunchedEffect(shown) { scope.launch { runCatching { state.list.scrollToItem(0) } } }
}

@Composable
private fun PdfPage(doc: PdfDoc, page: Int, widthPx: Int, modifier: Modifier) {
    val ratio = remember(doc, page) { doc.ratio(page) }
    val img by produceState<ImageBitmap?>(null, doc, page, widthPx) { value = doc.render(page, widthPx) }
    Surface(modifier.aspectRatio(ratio).shadow(2.dp), color = Color.White) {
        val bmp = img
        if (bmp != null) Image(bmp, "${page + 1}쪽", Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) }
    }
}

/** 보기 안에서 [page]쪽으로 옮긴다(보여 주는 쪽 목록 기준). */
suspend fun PdfViewerState.goTo(page: Int, shown: List<Int>) {
    val i = shown.indexOf(page).takeIf { it >= 0 } ?: page
    runCatching { list.scrollToItem(i) }
}

@Composable
fun rememberPdfViewerState(): PdfViewerState = remember { PdfViewerState() }
