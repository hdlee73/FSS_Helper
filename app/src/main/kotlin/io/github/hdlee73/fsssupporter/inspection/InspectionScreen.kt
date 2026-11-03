package io.github.hdlee73.fsssupporter.inspection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.tasks.DateField
import io.github.hdlee73.fsssupporter.tasks.LocalField
import io.github.hdlee73.fsssupporter.tasks.TaskKind
import io.github.hdlee73.fsssupporter.tasks.TaskStatus
import io.github.hdlee73.fsssupporter.tasks.TaskStore
import io.github.hdlee73.fsssupporter.tasks.WorkTask
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.EmptyState
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.SearchField
import io.github.hdlee73.fsssupporter.ui.Tone
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 화면 상태(메뉴를 오가도 유지) */
class InspectionUi {
    var tab by mutableStateOf(0)
    var taskId by mutableStateOf<String?>(null)
    var stepId by mutableStateOf<String?>(null)
    var formFind by mutableStateOf("")
    var manualTitle by mutableStateOf<String?>(null)
    var manualFind by mutableStateOf("")

    fun back(): Boolean {
        if (tab != 0) { tab = 0; return true }
        if (stepId != null) { stepId = null; return true }
        if (taskId != null) { taskId = null; return true }
        return false
    }
}

/** 서식을 의견서 도우미 초안으로 연다. task는 없을 수 있다. */
typealias WriteForm = (FormRef, WorkTask?) -> Unit

@Composable
fun InspectionScreen(store: InspectionStore, tasks: TaskStore, ui: InspectionUi, onWriteForm: WriteForm, onOpenTasks: (String) -> Unit, onMessage: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("검사 진행 관리", "검사품질관리 매뉴얼의 단계(검사준비 → 현장검사 → 검사결과 처리 → 사후관리)대로 점검하고, 단계별 서식을 바로 작성합니다.")
        TabRow(selectedTabIndex = ui.tab, modifier = Modifier.padding(horizontal = 20.dp).width(420.dp), containerColor = MaterialTheme.colorScheme.background) {
            Tab(ui.tab == 0, { ui.tab = 0 }, text = { Text("진행 점검") })
            Tab(ui.tab == 1, { ui.tab = 1 }, text = { Text("서식 모음") })
            Tab(ui.tab == 2, { ui.tab = 2 }, text = { Text("매뉴얼") })
        }
        Spacer(Modifier.height(10.dp))
        when (ui.tab) {
            0 -> ProgressTab(store, tasks, ui, onWriteForm, onOpenTasks)
            1 -> FormsTab(store, ui, onWriteForm)
            else -> ManualTab(store, ui, onMessage)
        }
    }
}

// ---------------- 진행 점검 ----------------

@Composable
private fun ProgressTab(store: InspectionStore, tasks: TaskStore, ui: InspectionUi, onWriteForm: WriteForm, onOpenTasks: (String) -> Unit) {
    val taskData by tasks.data.collectAsState()
    val data by store.data.collectAsState()
    val inspections = taskData.tasks.filter { it.kind == TaskKind.INSPECTION }.sortedWith(compareBy({ !it.isOpen }, { it.startDate() ?: LocalDate.MAX }))
    val task = inspections.firstOrNull { it.id == ui.taskId } ?: inspections.firstOrNull { it.isOpen }
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val t = WorkTask(title = "새 검사", kind = TaskKind.INSPECTION, status = TaskStatus.PLANNED, start = LocalDate.now().plusDays(14).toString())
                tasks.upsert(t); ui.taskId = t.id; ui.stepId = null
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text(" 새 검사") }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (inspections.isEmpty()) item {
                    EmptyState(Icons.Outlined.FactCheck, "검사가 없어요", "'새 검사'를 누르거나 작업 관리에서 종류를 '검사'로 정한 작업이 여기에 나타납니다.")
                }
                items(inspections, key = { it.id }) { t ->
                    val p = data.items[t.id] ?: InspectionProgress(t.id)
                    val sel = t.id == task?.id
                    AppCard(Modifier.fillMaxWidth().clickable { ui.taskId = t.id; ui.stepId = null }) {
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Pill(if (p.regular) "정기" else "수시", Tone.ACCENT)
                            Text(p.current()?.title ?: "모든 단계 완료", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        LinearProgressIndicator(progress = { p.ratio() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (task == null) {
            AppCard(Modifier.weight(1f).fillMaxHeight()) {
                EmptyState(Icons.Outlined.FactCheck, "검사를 고르세요", "검사를 고르면 착수일·종료일 기준으로 단계별 기한과 점검 항목, 관련 서식을 보여 줍니다.")
            }
        } else {
            val p = data.items[task.id] ?: InspectionProgress(task.id)
            StepsPane(store, tasks, task, p, ui, onOpenTasks, Modifier.weight(1f).fillMaxHeight())
            val step = p.steps().firstOrNull { it.id == ui.stepId } ?: p.current() ?: p.steps().lastOrNull()
            if (step != null) StepDetail(store, task, p, step, onWriteForm, Modifier.width(400.dp).fillMaxHeight())
        }
    }
}

@Composable
private fun StepsPane(store: InspectionStore, tasks: TaskStore, task: WorkTask, p: InspectionProgress, ui: InspectionUi, onOpenTasks: (String) -> Unit, modifier: Modifier) {
    val today = LocalDate.now()
    AppCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocalField("insp-title-${task.id}", task.title, Modifier.weight(1f), "검사 이름(대상 기관·종류)", true) { v -> tasks.upsert(task.copy(title = v.ifBlank { "검사" }, updatedAt = System.currentTimeMillis())) }
                TextButton(onClick = { onOpenTasks(task.id) }) { Text("작업 상세") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!p.regular, { store.update(task.id) { it.copy(regular = false) } }, label = { Text("수시검사") })
                FilterChip(p.regular, { store.update(task.id) { it.copy(regular = true) } }, label = { Text("정기검사") })
                DateField("착수일", task.start, Modifier.width(150.dp)) { v -> tasks.upsert(task.copy(start = v, updatedAt = System.currentTimeMillis())) }
                DateField("현장검사 종료일", p.fieldEnd, Modifier.width(170.dp)) { v -> store.update(task.id) { it.copy(fieldEnd = v) } }
            }
            val steps = p.steps()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { p.ratio() }, modifier = Modifier.weight(1f))
                Text("${steps.count { it.id in p.done }}/${steps.size} 단계", style = MaterialTheme.typography.labelMedium)
            }
            Text("기한은 주말만 빼고 계산합니다(공휴일 미반영). 해당 없는 단계는 오른쪽에서 '해당 없음'으로 뺄 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            InspectionGuide.STAGES.forEach { stage ->
                val inStage = InspectionGuide.steps(p.regular).filter { s -> stage.steps.any { it.id == s.id } }
                if (inStage.isEmpty()) return@forEach
                HorizontalDivider(Modifier.padding(top = 4.dp))
                CardTitle(stage.title) { Pill("${inStage.count { it.id in p.done }}/${inStage.count { it.id !in p.skipped }}") }
                inStage.forEach { s ->
                    val skipped = s.id in p.skipped
                    val done = s.id in p.done
                    val due = InspectionStore.dueOf(s, p, task.startDate())
                    val selected = s.id == (ui.stepId ?: p.current()?.id)
                    Row(
                        Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .clickable { ui.stepId = s.id }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(done, { store.toggleDone(task.id, s.id) }, enabled = !skipped)
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            color = if (skipped) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        if (s.optional) Pill("선택")
                        if (skipped) Pill("해당 없음")
                        else if (due != null) {
                            val left = ChronoUnit.DAYS.between(today, due)
                            val tone = when { done -> Tone.OK; left < 0 -> Tone.BAD; left <= 3 -> Tone.WARN; else -> Tone.NEUTRAL }
                            Spacer(Modifier.width(4.dp))
                            Pill("${due.monthValue}/${due.dayOfMonth}" + if (!done) (if (left < 0) " 지남" else if (left == 0L) " 오늘" else " D-$left") else "", tone)
                        }
                        if (s.forms.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Pill("서식 ${s.forms.size}") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDetail(store: InspectionStore, task: WorkTask, p: InspectionProgress, step: Step, onWriteForm: WriteForm, modifier: Modifier) {
    val lib by store.library.collectAsState()
    var showManual by remember(step.id) { mutableStateOf<Pair<String, String>?>(null) }
    AppCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTitle(step.title) {
                TextButton(onClick = { store.toggleSkip(task.id, step.id) }) { Text(if (step.id in p.skipped) "다시 포함" else "해당 없음") }
            }
            step.due?.let { r ->
                val d = InspectionStore.dueOf(step, p, task.startDate())
                Text("기한: ${r.label(p.regular)}" + (d?.let { " → $it" } ?: " (날짜를 넣으면 계산)"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("점검 항목", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            step.checks.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth().clickable { store.toggleCheck(task.id, step.id, i) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox("${step.id}#$i" in p.checks, { store.toggleCheck(task.id, step.id, i) })
                    Text(c, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (step.forms.isNotEmpty()) {
                HorizontalDivider()
                Text("관련 서식", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                step.forms.mapNotNull(InspectionGuide::form).forEach { f -> FormRow(f, lib) { onWriteForm(f, task) } }
            }
            HorizontalDivider()
            Text("매뉴얼", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val manual = store.manualFor(step, lib)
            if (manual.isEmpty()) {
                InfoBox(if (lib.manual.isEmpty()) "매뉴얼 탭에서 검사품질관리 매뉴얼(HWP)을 가져오면 이 단계의 매뉴얼 원문을 여기서 바로 볼 수 있습니다." else "이 단계와 짝이 되는 매뉴얼 절을 찾지 못했습니다.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    manual.forEach { m -> OutlinedButton(onClick = { showManual = m }) { Icon(Icons.Outlined.MenuBook, null, Modifier.size(16.dp)); Text(" ${m.first}", maxLines = 1) } }
                }
            }
            HorizontalDivider()
            LocalField("note-${task.id}-${step.id}", p.notes[step.id].orEmpty(), Modifier.fillMaxWidth().heightIn(min = 90.dp), "메모(담당·결과·특이사항)", false) { store.setNote(task.id, step.id, it) }
        }
    }
    showManual?.let { (title, text) -> TextDialog(title, text) { showManual = null } }
}

@Composable
private fun FormRow(f: FormRef, lib: GuideLibraryData, onWrite: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Description, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(f.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOf(f.basis, f.target).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            f.no in InspectionGuide.TEMPLATE_FOR_FORM -> Pill("기본 서식", Tone.OK)
            f.no in lib.forms -> Pill("원문", Tone.OK)
        }
        TextButton(onClick = onWrite) { Text("작성") }
    }
}

@Composable
private fun TextDialog(title: String, text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Box(Modifier.width(720.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
    )
}

// ---------------- 서식 모음 ----------------

@Composable
private fun FormsTab(store: InspectionStore, ui: InspectionUi, onWriteForm: WriteForm) {
    val lib by store.library.collectAsState()
    var preview by remember { mutableStateOf<FormRef?>(null) }
    val q = ui.formFind.trim()
    val list = InspectionGuide.FORMS.filter { q.isEmpty() || it.label.contains(q) || it.basis.contains(q) || it.group.contains(q) || it.no == q }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SearchField(ui.formFind, { ui.formFind = it }, {}, "서식 이름·번호·관련 규정으로 찾기 (예: 문답서, 5-2, 제24조)", Modifier.weight(1f))
            Pill(if (lib.forms.isEmpty()) "서식 원문 없음" else "서식 원문 ${lib.forms.size}건", if (lib.forms.isEmpty()) Tone.WARN else Tone.OK)
        }
        if (lib.forms.isEmpty()) InfoBox("서식 목록은 늘 보입니다. 매뉴얼 탭에서 '검사관련 서식' HWP를 가져오면 각 서식의 원문으로 바로 작성할 수 있습니다(가져오기 전에는 제목과 근거만 채운 빈 초안).")
        AppCard(Modifier.fillMaxSize()) {
            LazyColumn {
                list.groupBy { it.group }.forEach { (group, forms) ->
                    item(key = "g-$group") { Text(group, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                    items(forms, key = { it.no }) { f ->
                        Row(Modifier.fillMaxWidth().clickable { preview = f }) { FormRow(f, lib) { onWriteForm(f, null) } }
                    }
                }
            }
        }
    }
    preview?.let { f ->
        val text = lib.forms[f.no]
        TextDialog(f.label, text ?: "서식 원문을 아직 가져오지 않았습니다.\n관련 규정: ${f.basis.ifBlank { "-" }}\n대상처: ${f.target.ifBlank { "-" }}") { preview = null }
    }
}

// ---------------- 매뉴얼 ----------------

@Composable
private fun ManualTab(store: InspectionStore, ui: InspectionUi, onMessage: (String) -> Unit) {
    val lib by store.library.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun import(kind: String, block: suspend (File) -> Int) {
        val f = pickHwp(if (kind == "manual") "검사품질관리 매뉴얼 고르기" else "검사관련 서식 고르기") ?: return
        busy = true
        scope.launch {
            runCatching { block(f) }
                .onSuccess { n -> onMessage(if (kind == "manual") "매뉴얼 ${n}개 절을 가져왔습니다." else "서식 ${n}건을 가져왔습니다.") }
                .onFailure { onMessage("가져오지 못했습니다: ${it.message}") }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("매뉴얼·서식 가져오기") { if (busy) Pill("읽는 중…", Tone.WARN) }
            Text("한글 파일(HWP·HWPX)에서 글자만 읽어 이 PC에만 저장합니다. 외부로 보내지 않습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }
        if (lib.manual.isEmpty()) {
            AppCard(Modifier.fillMaxSize()) {
                EmptyState(Icons.Outlined.MenuBook, "매뉴얼을 가져오세요", "가져오면 절별로 나눠 보여 주고, 검사 진행 점검의 각 단계에서 해당 절을 바로 열 수 있습니다.")
            }
            return@Column
        }
        val q = ui.manualFind.trim()
        val titles = lib.manual.keys.filter { q.isEmpty() || it.contains(q) || lib.manual[it]!!.contains(q) }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    CardTitle(t)
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SelectionContainer { Text(lib.manual[t].orEmpty(), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

private fun pickHwp(title: String): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".hwp") || it.endsWith(".hwpx") } }
    dialog.file = "*.hwp;*.hwpx"
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
