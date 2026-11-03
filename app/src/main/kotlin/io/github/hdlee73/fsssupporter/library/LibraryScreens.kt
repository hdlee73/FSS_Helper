package io.github.hdlee73.fsssupporter.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.SearchField
import io.github.hdlee73.fsssupporter.ui.Tone
import io.github.hdlee73.fsssupporter.ui.pickFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date

/** 매뉴얼·서식 화면 상태(메뉴를 오가도 남는다). */
class LibraryUi {
    var manualQuery by mutableStateOf("")
    /** 홈 화면 등에서 열라고 한 매뉴얼 쪽 */
    var manualJump by mutableStateOf<Int?>(null)
    var formNo by mutableStateOf<String?>(null)
    var formFilter by mutableStateOf("")
    /** 가져오는 중이면 안내 글 */
    var busy by mutableStateOf<String?>(null)
    val manualViewer = PdfViewerState()
    val formViewer = PdfViewerState()

    fun backManual(): Boolean = if (manualQuery.isNotBlank()) { manualQuery = ""; true } else false
    fun backForms(): Boolean = when {
        formNo != null -> { formNo = null; true }
        formFilter.isNotBlank() -> { formFilter = ""; true }
        else -> false
    }
}

/** 가져오기·서식 작성처럼 화면 밖(앱 전체)에서 하는 일 */
class LibraryActions(
    val importFile: (kind: String, file: File) -> Unit,
    val writeForm: (no: String) -> Unit,
    val openedManual: (title: String, page: Int) -> Unit,
    val openedForm: (no: String) -> Unit,
    val message: (String) -> Unit,
)

private val HWP_OR_PDF: (String) -> Boolean = { it.endsWith(".hwp") || it.endsWith(".hwpx") || it.endsWith(".pdf") }
private fun dateLabel(t: Long) = if (t <= 0) "" else SimpleDateFormat("yyyy.MM.dd HH:mm").format(Date(t))

// ───────────────────────────── 검사품질관리 매뉴얼

@Composable
fun ManualScreen(store: LibraryStore, ui: LibraryUi, actions: LibraryActions) {
    val lib by store.data.collectAsState()
    val doc = rememberPdf(if (lib.hasManual) lib.manualPdf else "", lib.importedAt)
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "검사품질관리 매뉴얼",
            subtitle = if (lib.hasManual) "${lib.manualName} · ${doc?.pageCount ?: 0}쪽 · ${dateLabel(lib.importedAt)} 가져옴" else "가져온 매뉴얼을 원본 모양(PDF) 그대로 보여 줍니다.",
        ) {
            ui.busy?.let { BusyLabel(it) }
            if (lib.manualSrc.isNotBlank() && File(lib.manualSrc).isFile) {
                TextButton(onClick = { Os.open(File(lib.manualSrc)) }) { Text("원본 열기") }
            }
            if (lib.manualSrc.isNotBlank() && !lib.hasManual) {
                TextButton(onClick = { pickFile("매뉴얼 PDF 지정") { it.endsWith(".pdf") }?.let { actions.importFile("manual", it) } }) { Text("PDF 지정") }
            }
            OutlinedButton(onClick = { pickFile("검사품질관리 매뉴얼 가져오기(HWP·HWPX·PDF)", HWP_OR_PDF)?.let { actions.importFile("manual", it) } }) {
                Text(if (lib.hasManual) "다시 가져오기" else "가져오기")
            }
        }
        if (!lib.hasManual || doc == null) {
            ImportGuide(
                icon = Icons.Outlined.MenuBook,
                title = if (lib.manualSrc.isNotBlank()) "매뉴얼 PDF가 아직 없습니다" else "검사품질관리 매뉴얼을 가져오세요",
                body = "검사품질관리 매뉴얼 한글 파일(HWP·HWPX)을 고르면 이 PC의 한글이 PDF로 바꿔 원본 모양 그대로 보여 줍니다. " +
                    "한글이 없거나 변환이 안 되면 한글이나 이음오피스에서 'PDF로 저장'한 파일을 가져오세요. 파일은 이 PC의 앱 데이터 폴더에만 둡니다.",
                busy = ui.busy,
                onPick = { pickFile("검사품질관리 매뉴얼 가져오기(HWP·HWPX·PDF)", HWP_OR_PDF)?.let { actions.importFile("manual", it) } },
            )
            return@Column
        }
        LaunchedEffect(doc, ui.manualJump) {
            ui.manualJump?.let { p -> ui.manualViewer.goTo(p, emptyList()); ui.manualJump = null }
        }
        Row(Modifier.fillMaxSize()) {
            SidePanel {
                SearchField(ui.manualQuery, { ui.manualQuery = it }, {}, "매뉴얼에서 찾기", Modifier.fillMaxWidth().padding(10.dp))
                val q = ui.manualQuery
                if (q.isBlank()) {
                    Text("목차 ${lib.manualToc.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
                    val listState = rememberLazyListState()
                    Box(Modifier.weight(1f)) {
                        LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                            if (lib.manualToc.isEmpty()) item { Text("목차를 찾지 못했습니다. 위 찾기로 쪽을 찾으세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                            items(lib.manualToc, key = { it.title + it.page }) { t ->
                                ListRow(t.title, "${t.page + 1}쪽") {
                                    scope.launch { ui.manualViewer.goTo(t.page, emptyList()) }
                                    actions.openedManual(t.title, t.page)
                                }
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                } else {
                    val hits by produceState(emptyList<LibraryStore.Hit>(), q, lib.importedAt) { value = withContext(Dispatchers.IO) { store.search("manual", q) } }
                    Text("찾은 쪽 ${hits.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        items(hits, key = { it.page }) { h ->
                            ListRow("${h.page + 1}쪽", h.snippet, twoLine = true) {
                                scope.launch { ui.manualViewer.goTo(h.page, emptyList()) }
                                actions.openedManual(lib.manualToc.lastOrNull { it.page <= h.page }?.title ?: "${h.page + 1}쪽", h.page)
                            }
                        }
                    }
                }
            }
            PdfPages(doc, emptyList(), ui.manualViewer, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

// ───────────────────────────── 검사관련 서식

@Composable
fun FormsScreen(store: LibraryStore, ui: LibraryUi, actions: LibraryActions) {
    val lib by store.data.collectAsState()
    val doc = rememberPdf(if (lib.hasForms) lib.formsPdf else "", lib.importedAt)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "검사관련 서식",
            subtitle = if (lib.formNos.isNotEmpty()) "${lib.formsName} · 서식 ${lib.formNos.size}종 · 원본 보기는 PDF, 작성은 HWP·HWPX" else "원본 모양 그대로 보고, 필요한 서식만 떼어 작성합니다.",
        ) {
            ui.busy?.let { BusyLabel(it) }
            if (lib.formsSrc.isNotBlank() && !lib.hasForms) {
                TextButton(onClick = { pickFile("서식 PDF 지정") { it.endsWith(".pdf") }?.let { actions.importFile("forms", it) } }) { Text("PDF 지정") }
            }
            if (lib.formsSrc.isNotBlank() && File(lib.formsSrc).isFile) {
                TextButton(onClick = { Os.open(File(lib.formsSrc)) }) { Text("서식 전체 원본 열기") }
            }
            OutlinedButton(onClick = { pickFile("검사관련 서식 가져오기(HWP·HWPX·PDF)", HWP_OR_PDF)?.let { actions.importFile("forms", it) } }) {
                Text(if (lib.formNos.isNotEmpty()) "다시 가져오기" else "가져오기")
            }
        }
        if (lib.formNos.isEmpty()) {
            ImportGuide(
                icon = Icons.Outlined.Description,
                title = "검사관련 서식을 가져오세요",
                body = "'< 제1-1호 서식 >' 표시가 들어 있는 서식 한글 파일(HWP·HWPX)을 고르면, 원본 보기용 PDF를 만들고 " +
                    "서식마다 원본에서 그 부분만 떼어 한글 또는 이음오피스로 작성할 수 있게 합니다(설정에서 고름). PDF만 넣으면 보기만 됩니다.",
                busy = ui.busy,
                onPick = { pickFile("검사관련 서식 가져오기(HWP·HWPX·PDF)", HWP_OR_PDF)?.let { actions.importFile("forms", it) } },
            )
            return@Column
        }
        Row(Modifier.fillMaxSize()) {
            SidePanel(width = 340) {
                SearchField(ui.formFilter, { ui.formFilter = it }, {}, "서식 번호·이름 찾기", Modifier.fillMaxWidth().padding(10.dp))
                val f = LibraryStore.squash(ui.formFilter)
                val forms = remember(lib.formNos, f) {
                    lib.formNos.map { FormCatalog.form(it) }.filter { r -> f.isEmpty() || LibraryStore.squash(r.no + r.name + r.basis).contains(f) || LibraryStore.squash("제${r.no}호").contains(f) }
                }
                val listState = rememberLazyListState()
                Box(Modifier.weight(1f)) {
                    LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        var lastGroup = ""
                        fun groupOf(r: FormRef) = r.group.ifBlank { "제${r.no.substringBefore('-')}장" }
                        val counts = forms.groupingBy(::groupOf).eachCount()
                        forms.forEach { r ->
                            val g = groupOf(r)
                            if (g != lastGroup) {
                                val first = lastGroup.isEmpty()
                                lastGroup = g
                                item(key = "g:$g:${r.no}") { GroupHeader(g, counts[g] ?: 0, first) }
                            }
                            item(key = r.no) {
                                FormRow(r, ui.formNo == r.no) { ui.formNo = r.no; actions.openedForm(r.no) }
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
            val no = ui.formNo
            if (no == null) {
                Box(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("왼쪽에서 서식을 고르세요", style = MaterialTheme.typography.titleSmall)
                        Text("원본 모양을 보고 '서식 작성'을 누르면 그 서식만 떼어 한글 또는 이음오피스로 엽니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!lib.hasForms) InfoBox("원본 보기용 PDF가 없습니다. 위 'PDF 지정'으로 넣어 주세요.", Modifier.widthIn(max = 420.dp).padding(top = 8.dp))
                    }
                }
            } else {
                val r = FormCatalog.form(no)
                val span = lib.formPages[no]
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label.trim(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(r.basis, r.target.takeIf { it.isNotBlank() }?.let { "받는 곳 $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "가져온 서식" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { actions.writeForm(no) }, enabled = lib.canWriteForms) {
                            Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("서식 작성")
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    when {
                        doc != null && span != null -> PdfPages(doc, span.pages, ui.formViewer, Modifier.fillMaxSize())
                        doc != null -> Box(Modifier.fillMaxSize().padding(24.dp)) { InfoBox("PDF에서 '< 제${no}호 서식 >' 표시를 찾지 못했습니다. 서식 전체 원본에서 찾아보세요.") }
                        else -> Box(Modifier.fillMaxSize().padding(24.dp)) { InfoBox("원본 보기용 PDF가 없습니다. 위 'PDF 지정'으로 한글이나 이음오피스에서 PDF로 저장한 서식 파일을 넣어 주세요. 서식 작성은 지금도 됩니다.") }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── 공통

@Composable
private fun SidePanel(width: Int = 300, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.width(width.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

@Composable
private fun ListRow(title: String, sub: String, twoLine: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip10().clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (twoLine) 3 else 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 서식 묶음 제목(상시감시·검사준비·현장검사 …): 크고 굵게, 앞에 색 막대 */
@Composable
private fun GroupHeader(title: String, count: Int, first: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = 6.dp, end = 8.dp, top = if (first) 6.dp else 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FormRow(r: FormRef, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent
    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp).clip10().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(r.no, if (selected) Tone.ACCENT else Tone.NEUTRAL)
            Column(Modifier.weight(1f)) {
                Text(r.name.ifBlank { "제${r.no}호 서식" }, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.basis.isNotBlank()) Text(r.basis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ImportGuide(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, busy: String?, onPick: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.TopCenter) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPick, enabled = busy == null) {
                        Icon(Icons.Outlined.FileOpen, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("파일 고르기")
                    }
                    Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("HWP · HWPX · PDF", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (busy != null) BusyLabel(busy)
                Spacer(Modifier.height(4.dp))
                Text("한글을 쓰는 PC에서 처음 '파일 접근 허용' 창이 뜨면 '모두 허용'을 눌러 주세요. 한글 대신 이음오피스를 쓰려면 설정에서 고르세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun BusyLabel(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Modifier.clip10(): Modifier = this.clip(RoundedCornerShape(10.dp))
