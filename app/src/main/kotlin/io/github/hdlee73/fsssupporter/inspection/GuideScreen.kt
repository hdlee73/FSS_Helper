package io.github.hdlee73.fsssupporter.inspection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.cases.DocDialog
import io.github.hdlee73.fsssupporter.cases.FormLine
import io.github.hdlee73.fsssupporter.cases.pickFile
import io.github.hdlee73.fsssupporter.tasks.WorkTask
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.EmptyState
import io.github.hdlee73.fsssupporter.ui.HwpBlocks
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.SearchField
import io.github.hdlee73.fsssupporter.ui.Tone
import kotlinx.coroutines.launch
import java.io.File

/** 서식을 원본 그대로 만들어 연다. 검사건은 없을 수 있다. */
typealias WriteForm = (FormRef, WorkTask?) -> Unit

/** 화면 상태(메뉴를 오가도 유지) */
class GuideUi {
    var tab by mutableStateOf(0)
    var formFind by mutableStateOf("")
    var manualTitle by mutableStateOf<String?>(null)
    var manualFind by mutableStateOf("")

    fun back(): Boolean {
        if (tab != 0) { tab = 0; return true }
        return false
    }
}

@Composable
fun GuideScreen(store: InspectionStore, ui: GuideUi, onWriteForm: WriteForm, onViewOriginal: (FormRef) -> Unit, onMessage: (String) -> Unit) {
    val lib by store.library.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("서식·매뉴얼", "가져온 검사관련 서식과 검사품질관리 매뉴얼을 봅니다. 서식 '작성'은 원본 한글 서식을 그대로 잘라 새 파일로 엽니다.") {
            Pill(if (lib.formsCopy.isBlank()) "서식 원본 없음" else "서식 ${lib.formDocs.size.takeIf { it > 0 } ?: lib.forms.size}건", if (lib.formsCopy.isBlank()) Tone.WARN else Tone.OK)
            Spacer(Modifier.width(6.dp))
            Pill(if (lib.manual.isEmpty()) "매뉴얼 없음" else "매뉴얼 ${lib.manual.size}개 절", if (lib.manual.isEmpty()) Tone.WARN else Tone.OK)
        }
        TabRow(selectedTabIndex = ui.tab, modifier = Modifier.padding(horizontal = 20.dp).width(420.dp), containerColor = MaterialTheme.colorScheme.background) {
            Tab(ui.tab == 0, { ui.tab = 0 }, text = { Text("서식") })
            Tab(ui.tab == 1, { ui.tab = 1 }, text = { Text("매뉴얼") })
            Tab(ui.tab == 2, { ui.tab = 2 }, text = { Text("가져오기") })
        }
        Spacer(Modifier.height(10.dp))
        when (ui.tab) {
            0 -> FormsTab(store, ui, onWriteForm, onViewOriginal)
            1 -> ManualTab(store, ui)
            else -> ImportTab(store, onMessage)
        }
    }
}

@Composable
private fun FormsTab(store: InspectionStore, ui: GuideUi, onWriteForm: WriteForm, onViewOriginal: (FormRef) -> Unit) {
    val lib by store.library.collectAsState()
    var preview by remember { mutableStateOf<FormRef?>(null) }
    val q = ui.formFind.trim()
    val list = InspectionGuide.FORMS.filter { q.isEmpty() || it.label.contains(q) || it.basis.contains(q) || it.group.contains(q) || it.no == q }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(ui.formFind, { ui.formFind = it }, {}, "서식 이름·번호·관련 규정으로 찾기 (예: 문답서, 5-2, 제24조)", Modifier.fillMaxWidth())
        if (lib.formsCopy.isBlank()) InfoBox("'가져오기' 탭에서 검사관련 서식 한글 파일(HWP·HWPX)을 한 번 가져오면, 각 서식을 원본 모양(표·글꼴·쪽 설정) 그대로 작성할 수 있습니다.")
        else InfoBox("서식을 누르면 미리 보기, '작성'을 누르면 원본 서식 부분만 새 한글 파일로 만들어 엽니다(검사건 화면에서 누르면 그 검사건 폴더에 만듭니다). 처음 한 번 한글이 '접근 허용'을 물으면 '모두 허용'을 누르세요.")
        AppCard(Modifier.fillMaxSize()) {
            LazyColumn {
                list.groupBy { it.group }.forEach { (group, forms) ->
                    item(key = "g-$group") { Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                    items(forms, key = { it.no }) { f ->
                        Row(Modifier.fillMaxWidth().clickable { preview = f }) { FormLine(f, lib.formsCopy.isNotBlank() && f.no in lib.formDocs) { onWriteForm(f, null) } }
                    }
                }
            }
        }
    }
    preview?.let { f ->
        val blocks = store.formDoc(f.no, lib)
        DocDialog(
            f.label,
            blocks.ifEmpty { listOf(io.github.hdlee73.fsssupporter.data.HPara("서식 원문을 아직 가져오지 않았습니다. 관련 규정: ${f.basis.ifBlank { "-" }} · 대상처: ${f.target.ifBlank { "-" }}")) },
            onClose = { preview = null },
            extra = {
                if (lib.formsCopy.isNotBlank()) TextButton(onClick = { onViewOriginal(f) }) { Text("한글로 원본 보기") }
                TextButton(onClick = { preview = null; onWriteForm(f, null) }) { Text("작성") }
            },
        )
    }
}

@Composable
private fun ManualTab(store: InspectionStore, ui: GuideUi) {
    val lib by store.library.collectAsState()
    if (lib.manual.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            AppCard(Modifier.fillMaxSize()) {
                EmptyState(Icons.Outlined.MenuBook, "매뉴얼을 가져오세요", "'가져오기' 탭에서 검사품질관리 매뉴얼 한글 파일을 고르면 절별로 나눠 보여 주고, 검사건의 각 단계에서 해당 절을 바로 열 수 있습니다.")
            }
        }
        return
    }
    val q = ui.manualFind.trim()
    val titles = lib.manual.keys.filter { q.isEmpty() || it.contains(q) || lib.manual[it]!!.contains(q) }
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(ui.manualFind, { ui.manualFind = it }, {}, "매뉴얼에서 찾기", Modifier.fillMaxWidth())
            AppCard(Modifier.fillMaxSize()) {
                LazyColumn {
                    val refs = InspectionGuide.REFERENCES.map(InspectionStore::norm).toSet()
                    items(titles, key = { it }) { t ->
                        val sel = t == ui.manualTitle
                        Text(t, style = MaterialTheme.typography.bodyMedium, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.primary else if (InspectionStore.norm(t) in refs) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { ui.manualTitle = t }.padding(vertical = 5.dp))
                    }
                }
            }
        }
        AppCard(Modifier.weight(1f).fillMaxHeight()) {
            val t = ui.manualTitle?.takeIf { it in lib.manual } ?: titles.firstOrNull()
            if (t == null) Text("찾는 내용이 없습니다.")
            else {
                CardTitle(t) {
                    if (File(lib.manualCopy).isFile) TextButton(onClick = { Os.open(File(lib.manualCopy)) }) { Text("원본 열기(한글)") }
                }
                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    SelectionContainer { HwpBlocks(store.manualDoc(t, lib), Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun ImportTab(store: InspectionStore, onMessage: (String) -> Unit) {
    val lib by store.library.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun import(kind: String, block: suspend (File) -> Int) {
        val f = pickFile(if (kind == "manual") "검사품질관리 매뉴얼 고르기" else "검사관련 서식 고르기") { it.endsWith(".hwp") || it.endsWith(".hwpx") } ?: return
        busy = true
        scope.launch {
            runCatching { block(f) }
                .onSuccess { n -> onMessage(if (kind == "manual") "매뉴얼 ${n}개 절을 가져왔습니다." else "서식 ${n}건을 가져왔습니다.") }
                .onFailure { onMessage("가져오지 못했습니다: ${it.message}") }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("매뉴얼·서식 가져오기") { if (busy) Pill("읽는 중…", Tone.WARN) }
            Text("한글 파일(HWP·HWPX)을 고르면 원본 사본을 이 PC의 앱 데이터 폴더에 두고, 표 구조까지 읽어 절·서식별로 나눕니다. 외부로 보내지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(enabled = !busy, onClick = { import("manual") { store.importManual(it) } }) { Icon(Icons.Outlined.FileOpen, null, Modifier.size(16.dp)); Text(" 검사품질관리 매뉴얼") }
                Button(enabled = !busy, onClick = { import("forms") { store.importForms(it) } }) { Icon(Icons.Outlined.FileOpen, null, Modifier.size(16.dp)); Text(" 검사관련 서식") }
                if (lib.manual.isNotEmpty() || lib.forms.isNotEmpty()) TextButton(onClick = { store.clearLibrary(); onMessage("가져온 원문을 지웠습니다.") }) { Text("가져온 원문 지우기") }
            }
            Text(
                listOf(
                    if (lib.manual.isEmpty()) "매뉴얼: 없음" else "매뉴얼: ${lib.manualFile} (${lib.manual.size}개 절)",
                    if (lib.forms.isEmpty()) "서식: 없음" else "서식: ${lib.formsFile} (${lib.forms.size}건)",
                ).joinToString("   ·   "), style = MaterialTheme.typography.bodySmall,
            )
            if (lib.forms.isNotEmpty() && lib.formsCopy.isBlank()) InfoBox("예전 버전에서 가져온 서식은 글자만 있어 원본 그대로 작성할 수 없습니다. 서식 파일을 한 번 더 가져와 주세요.")
        }
        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("원본 그대로 작성하는 방식")
            listOf(
                "1. '작성'을 누르면 이 PC의 한글(한컴오피스)을 보이지 않게 실행해, 서식 원본 사본에서 '< 제X-Y호 서식 >'부터 다음 서식 앞까지만 남기고 저장합니다.",
                "2. 한글이 직접 지우고 저장하므로 표·글꼴·크기·쪽 설정이 원본과 같습니다. 만든 파일은 검사건 폴더(문서\\FSS_Helper\\검사건\\…)에 모입니다.",
                "3. 처음 한 번 한글이 '한글 문서에 접근하려는 시도' 창을 띄우면 '모두 허용'을 누르세요.",
                "4. 한글 자동화를 쓸 수 없는 PC에서는 서식 전체 원본의 사본을 열어 드립니다(Ctrl+F로 서식 번호를 찾아 쓰면 됩니다).",
            ).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { Os.open(File(io.github.hdlee73.fsssupporter.data.AppDirs.root, "library").apply { mkdirs() }) }) { Text("원본 사본 폴더 열기") }
        }
    }
}
