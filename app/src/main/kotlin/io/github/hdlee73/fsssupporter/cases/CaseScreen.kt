package io.github.hdlee73.fsssupporter.cases

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.hdlee73.fsssupporter.inspection.FormRef
import io.github.hdlee73.fsssupporter.inspection.InspectionGuide
import io.github.hdlee73.fsssupporter.inspection.InspectionProgress
import io.github.hdlee73.fsssupporter.inspection.InspectionStore
import io.github.hdlee73.fsssupporter.inspection.Step
import io.github.hdlee73.fsssupporter.inspection.StepState
import io.github.hdlee73.fsssupporter.sanction.SanctionStore
import io.github.hdlee73.fsssupporter.tasks.CheckItem
import io.github.hdlee73.fsssupporter.tasks.DateField
import io.github.hdlee73.fsssupporter.tasks.LocalField
import io.github.hdlee73.fsssupporter.tasks.TaskKind
import io.github.hdlee73.fsssupporter.tasks.TaskStatus
import io.github.hdlee73.fsssupporter.tasks.TaskStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 화면 상태(메뉴를 오가도 유지) */
class CaseUi {
    var caseId by mutableStateOf<String?>(null)
    var tab by mutableStateOf(0)
    var stepId by mutableStateOf<String?>(null)
    var findingId by mutableStateOf<String?>(null)
    var find by mutableStateOf("")
    var showDone by mutableStateOf(false)

    fun open(id: String, tab: Int = 0) { caseId = id; this.tab = tab; stepId = null; findingId = null }

    fun back(): Boolean {
        if (findingId != null) { findingId = null; return true }
        if (stepId != null) { stepId = null; return true }
        if (tab != 0) { tab = 0; return true }
        if (caseId != null) { caseId = null; return true }
        return false
    }
}

/** 검사건 화면이 밖에 부탁하는 일 */
class CaseActions(
    /** 서식을 이 검사건 폴더에 원본 서식으로 만들어 연다 */
    val writeForm: (FormRef, WorkTask?) -> Unit,
    val openLaw: (String) -> Unit,
    /** 문서 법규 검증 화면에서 이 파일을 검증한다 */
    val verifyFile: (File, WorkTask) -> Unit,
    val deleteCase: (WorkTask, Boolean) -> Unit,
    val openGuide: () -> Unit,
    val message: (String) -> Unit,
)

@Composable
fun CaseScreen(tasks: TaskStore, inspection: InspectionStore, findings: FindingStore, sanction: SanctionStore, ui: CaseUi, actions: CaseActions) {
    val taskData by tasks.data.collectAsState()
    val progress by inspection.data.collectAsState()
    val all = taskData.tasks.filter { it.kind == TaskKind.INSPECTION }
        .sortedWith(compareBy({ !it.isOpen }, { it.startDate() ?: LocalDate.MAX }))
    val q = ui.find.trim()
    val list = all.filter { (ui.showDone || it.isOpen || it.id == ui.caseId) && (q.isEmpty() || it.title.contains(q) || it.target.contains(q)) }
    val task = all.firstOrNull { it.id == ui.caseId }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("검사건", "검사건마다 진행 단계, 지적사항·행위자별 제재 양정, 서식·문서, 메모를 한곳에서 관리합니다.")
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val t = WorkTask(title = "새 검사건", kind = TaskKind.INSPECTION, status = TaskStatus.PLANNED, start = LocalDate.now().plusDays(14).toString())
                    tasks.upsert(t); ui.open(t.id)
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text(" 새 검사건") }
                SearchField(ui.find, { ui.find = it }, {}, "검사건·대상 기관 찾기", Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(ui.showDone, { ui.showDone = it })
                    Text("끝난 검사건도 보기", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (list.isEmpty()) item {
                        EmptyState(Icons.Outlined.FactCheck, "검사건이 없어요", "'새 검사건'을 눌러 시작하세요.")
                    }
                    items(list, key = { it.id }) { t ->
                        val p = progress.items[t.id] ?: InspectionProgress(t.id)
                        val sel = t.id == task?.id
                        AppCard(Modifier.fillMaxWidth().clickable { ui.open(t.id) }) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Pill(if (p.regular) "정기" else "수시", Tone.ACCENT)
                                if (!t.isOpen) Pill("끝남", Tone.OK)
                                Text(p.current()?.title ?: "모든 단계 완료", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            LinearProgressIndicator(progress = { p.ratio() }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            if (task == null) {
                AppCard(Modifier.weight(1f).fillMaxHeight()) {
                    EmptyState(Icons.Outlined.FactCheck, "검사건을 고르세요", "왼쪽에서 검사건을 고르거나 새로 만드세요. 착수일·현장검사 종료일을 넣으면 단계별 기한을 계산하고, 지적사항마다 행위자별 양정과 과징금·과태료를 산정합니다.")
                }
            } else {
                val p = progress.items[task.id] ?: InspectionProgress(task.id)
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaseHeader(tasks, inspection, task, p, actions)
                    TabRow(selectedTabIndex = ui.tab, modifier = Modifier.width(560.dp), containerColor = MaterialTheme.colorScheme.background) {
                        Tab(ui.tab == 0, { ui.tab = 0 }, text = { Text("진행 단계") })
                        Tab(ui.tab == 1, { ui.tab = 1 }, text = { Text("지적사항·제재") })
                        Tab(ui.tab == 2, { ui.tab = 2 }, text = { Text("문서·서식") })
                        Tab(ui.tab == 3, { ui.tab = 3 }, text = { Text("메모·할 일") })
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (ui.tab) {
                            0 -> StepsTab(inspection, task, p, ui, actions)
                            1 -> FindingsTab(findings, sanction, task, ui, actions)
                            2 -> DocsTab(inspection, task, p, actions)
                            else -> MemoTab(tasks, task, actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaseHeader(tasks: TaskStore, store: InspectionStore, task: WorkTask, p: InspectionProgress, actions: CaseActions) {
    var confirm by remember(task.id) { mutableStateOf(false) }
    var status by remember { mutableStateOf(false) }
    fun save(t: WorkTask) = tasks.upsert(t.copy(updatedAt = System.currentTimeMillis()))
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocalField("case-title-${task.id}", task.title, Modifier.weight(1f), "검사건 이름", true) { v -> save(task.copy(title = v.ifBlank { "검사건" })) }
            LocalField("case-target-${task.id}", task.target, Modifier.width(200.dp), "대상 기관", true) { v -> save(task.copy(target = v)) }
            Box {
                OutlinedButton(onClick = { status = true }) { Text(task.status.label) }
                DropdownMenu(status, { status = false }) {
                    TaskStatus.entries.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { status = false; save(task.copy(status = s)) }) }
                }
            }
            IconButton(onClick = { Os.open(CaseFiles.dir(task)) }) { Icon(Icons.Outlined.FolderOpen, "검사건 문서 폴더 열기") }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Outlined.DeleteOutline, "검사건 삭제", tint = MaterialTheme.colorScheme.error) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!p.regular, { store.update(task.id) { it.copy(regular = false) } }, label = { Text("수시검사") })
            FilterChip(p.regular, { store.update(task.id) { it.copy(regular = true) } }, label = { Text("정기검사") })
            DateField("착수일", task.start, Modifier.width(150.dp)) { v -> save(task.copy(start = v)) }
            DateField("현장검사 종료일", p.fieldEnd, Modifier.width(160.dp)) { v -> store.update(task.id) { it.copy(fieldEnd = v) } }
            DateField("결과 마감일", task.due, Modifier.width(150.dp)) { v -> save(task.copy(due = v)) }
            Spacer(Modifier.weight(1f))
            val steps = p.steps()
            Column(Modifier.width(150.dp)) {
                Text("내 단계 ${steps.count { it.id in p.done }}/${steps.size}", style = MaterialTheme.typography.labelMedium)
                LinearProgressIndicator(progress = { p.ratio() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirm) {
        var withFiles by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("검사건을 삭제할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("'${task.title}'의 진행 기록, 지적사항·제재 양정, 메모와 일정 연결이 모두 지워집니다. 되돌릴 수 없습니다.")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { withFiles = !withFiles }) {
                        Checkbox(withFiles, { withFiles = it })
                        Text("검사건 문서 폴더(작성한 한글 파일 등)도 함께 지우기", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = { Button(onClick = { confirm = false; actions.deleteCase(task, withFiles) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
        )
    }
}

// ---------------- 진행 단계 ----------------

@Composable
private fun StepsTab(store: InspectionStore, task: WorkTask, p: InspectionProgress, ui: CaseUi, actions: CaseActions) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StepsPane(store, task, p, ui, Modifier.weight(1f).fillMaxHeight())
        val all = InspectionGuide.steps(p.regular)
        val step = all.firstOrNull { it.id == ui.stepId } ?: p.current() ?: all.lastOrNull()
        if (step != null) StepDetail(store, task, p, step, actions, Modifier.width(400.dp).fillMaxHeight())
    }
}

private fun stateTone(s: StepState) = when (s) {
    StepState.DONE -> Tone.OK
    StepState.TODO -> Tone.NEUTRAL
    StepState.SKIPPED, StepState.OTHER -> Tone.NEUTRAL
}

@Composable
private fun StepsPane(store: InspectionStore, task: WorkTask, p: InspectionProgress, ui: CaseUi, modifier: Modifier) {
    val today = LocalDate.now()
    AppCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("체크 = 완료. 내가 하지 않는 단계는 오른쪽 '생략'·'다른 팀 담당'으로 넘기면 진행률과 다음 단계에서 빠집니다. 기한은 주말만 빼고 계산합니다(공휴일 미반영).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            InspectionGuide.STAGES.forEach { stage ->
                val inStage = InspectionGuide.steps(p.regular).filter { s -> stage.steps.any { it.id == s.id } }
                if (inStage.isEmpty()) return@forEach
                HorizontalDivider(Modifier.padding(top = 4.dp))
                val mine = inStage.filter { p.stateOf(it.id).active }
                CardTitle(stage.title) { Pill("${mine.count { it.id in p.done }}/${mine.size}") }
                inStage.forEach { s ->
                    val state = p.stateOf(s.id)
                    val done = state == StepState.DONE
                    val due = InspectionStore.dueOf(s, p, task.startDate())
                    val selected = s.id == (ui.stepId ?: p.current()?.id)
                    Row(
                        Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .clickable { ui.stepId = s.id }.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(done, { store.setState(task.id, s.id, if (done) StepState.TODO else StepState.DONE) }, enabled = state.active)
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                            color = if (!state.active) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface)
                        if (s.optional) Pill("선택")
                        when (state) {
                            StepState.SKIPPED -> Pill("생략")
                            StepState.OTHER -> Pill("다른 팀" + p.others[s.id]?.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty())
                            else -> if (due != null) {
                                val left = ChronoUnit.DAYS.between(today, due)
                                val tone = when { done -> Tone.OK; left < 0 -> Tone.BAD; left <= 3 -> Tone.WARN; else -> Tone.NEUTRAL }
                                Spacer(Modifier.width(4.dp))
                                Pill("${due.monthValue}/${due.dayOfMonth}" + if (!done) (if (left < 0) " 지남" else if (left == 0L) " 오늘" else " D-$left") else "", tone)
                            }
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
private fun StepDetail(store: InspectionStore, task: WorkTask, p: InspectionProgress, step: Step, actions: CaseActions, modifier: Modifier) {
    val lib by store.library.collectAsState()
    var showManual by remember(step.id) { mutableStateOf<String?>(null) }
    val state = p.stateOf(step.id)
    AppCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTitle(step.title) { Pill(state.label, stateTone(state)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StepState.entries.forEach { s -> FilterChip(state == s, { store.setState(task.id, step.id, s) }, label = { Text(s.label) }) }
            }
            if (state == StepState.OTHER) {
                LocalField("owner-${task.id}-${step.id}", p.others[step.id].orEmpty(), Modifier.fillMaxWidth(), "맡은 팀·담당자(예: 검사총괄팀)", true) { store.setOwner(task.id, step.id, it) }
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
                Text("관련 서식 · '작성'은 원본 서식 그대로 이 검사건 폴더에 만들어 한글로 엽니다", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (lib.formsCopy.isBlank()) InfoBox("서식·매뉴얼 메뉴에서 '검사관련 서식' 한글 파일을 한 번 가져오면 원본 서식으로 작성할 수 있습니다.")
                step.forms.mapNotNull(InspectionGuide::form).forEach { f -> FormLine(f, lib.formsCopy.isNotBlank() && f.no in lib.formDocs) { actions.writeForm(f, task) } }
            }
            HorizontalDivider()
            Text("매뉴얼", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            val manual = store.manualFor(step, lib)
            if (manual.isEmpty()) {
                InfoBox(if (lib.manual.isEmpty()) "서식·매뉴얼 메뉴에서 검사품질관리 매뉴얼(한글 파일)을 가져오면 이 단계의 매뉴얼을 여기서 바로 볼 수 있습니다." else "이 단계와 짝이 되는 매뉴얼 절을 찾지 못했습니다.")
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    manual.forEach { m -> OutlinedButton(onClick = { showManual = m }) { Icon(Icons.Outlined.MenuBook, null, Modifier.size(16.dp)); Text(" $m", maxLines = 1) } }
                }
            }
            HorizontalDivider()
            LocalField("note-${task.id}-${step.id}", p.notes[step.id].orEmpty(), Modifier.fillMaxWidth().heightIn(min = 90.dp), "메모(담당·결과·특이사항)", false) { store.setNote(task.id, step.id, it) }
        }
    }
    showManual?.let { key ->
        val original = File(lib.manualCopy).takeIf { lib.manualCopy.isNotBlank() && it.isFile }
        DocDialog(key, store.manualDoc(key, lib), onClose = { showManual = null }, extra = {
            if (original != null) TextButton(onClick = { Os.open(original) }) { Text("매뉴얼 원본 열기(한글)") }
        })
    }
}

@Composable
fun FormLine(f: FormRef, original: Boolean, onWrite: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.Description, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(f.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = listOf(f.basis, f.target).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (original) Pill("원본", Tone.OK)
        TextButton(onClick = onWrite) { Text("작성") }
    }
}

/** 한글 문서 구조(문단·표)를 보여 주는 창 */
@Composable
fun DocDialog(title: String, blocks: List<io.github.hdlee73.fsssupporter.data.HBlock>, onClose: () -> Unit, extra: (@Composable () -> Unit)? = null) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Box(Modifier.width(820.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                androidx.compose.foundation.text.selection.SelectionContainer { HwpBlocks(blocks, Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = { Row { extra?.invoke(); TextButton(onClick = onClose) { Text("닫기") } } },
    )
}

// ---------------- 문서·서식 ----------------

@Composable
private fun DocsTab(store: InspectionStore, task: WorkTask, p: InspectionProgress, actions: CaseActions) {
    val lib by store.library.collectAsState()
    var files by remember(task.id) { mutableStateOf(CaseFiles.list(task)) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(task.id, tick) {
        // 한글에서 저장하고 돌아오면 바뀐 목록을 보여 주도록 몇 초마다 다시 읽는다.
        while (true) {
            files = withContext(Dispatchers.IO) { CaseFiles.list(task) }
            kotlinx.coroutines.delay(4000)
        }
    }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard(Modifier.weight(1f).fillMaxHeight()) {
            CardTitle("이 검사건의 문서 ${files.size}건") {
                Row {
                    TextButton(onClick = {
                        pickFile("검사건 폴더에 넣을 파일 고르기")?.let { f ->
                            scope.launch {
                                withContext(Dispatchers.IO) { f.copyTo(CaseFiles.unique(CaseFiles.dir(task), f.name)) }
                                tick++; actions.message("'${f.name}'을(를) 검사건 폴더에 복사했습니다.")
                            }
                        }
                    }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text(" 파일 넣기") }
                    TextButton(onClick = { Os.open(CaseFiles.dir(task)) }) { Icon(Icons.Outlined.FolderOpen, null, Modifier.size(16.dp)); Text(" 폴더 열기") }
                }
            }
            Text(CaseFiles.dir(task, create = false).absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (files.isEmpty()) InfoBox("오른쪽에서 서식을 '작성'하면 원본 서식 그대로 이 폴더에 한글 파일을 만들고 엽니다. 다른 파일은 '파일 넣기'로 모을 수 있습니다.")
            LazyColumn {
                items(files, key = { it.absolutePath }) { f ->
                    Row(Modifier.fillMaxWidth().clickable { Os.open(f) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' ').take(16) + " · " + sizeLabel(f.length()),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val ext = f.extension.lowercase()
                        if (ext == "hwp" || ext == "hwpx") TextButton(onClick = { actions.verifyFile(f, task) }) { Icon(Icons.Outlined.Rule, null, Modifier.size(16.dp)); Text(" 법규 검증") }
                        TextButton(onClick = { Os.open(f) }) { Text("열기") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        AppCard(Modifier.width(420.dp).fillMaxHeight()) {
            CardTitle("서식으로 새 문서") { TextButton(onClick = actions.openGuide) { Text("서식 전체 보기") } }
            if (lib.formsCopy.isBlank()) InfoBox("서식·매뉴얼 메뉴에서 '검사관련 서식' 한글 파일을 한 번 가져오면, 여기서 원본 서식 그대로 작성할 수 있습니다.")
            val steps = InspectionGuide.steps(p.regular).filter { p.stateOf(it.id).active && it.forms.isNotEmpty() }
            LazyColumn {
                steps.forEach { s ->
                    item(key = "s-" + s.id) { Text(s.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                    items(s.forms.mapNotNull(InspectionGuide::form), key = { s.id + it.no }) { f ->
                        FormLine(f, lib.formsCopy.isNotBlank() && f.no in lib.formDocs) { actions.writeForm(f, task) }
                    }
                }
            }
        }
    }
}

private fun sizeLabel(n: Long) = when {
    n >= 1_048_576 -> "%.1fMB".format(n / 1_048_576.0)
    n >= 1024 -> "${n / 1024}KB"
    else -> "${n}B"
}

fun pickFile(title: String, filter: ((String) -> Boolean)? = null): File? {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, title, java.awt.FileDialog.LOAD)
    if (filter != null) dialog.setFilenameFilter { _, name -> filter(name.lowercase()) }
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}

// ---------------- 메모·할 일 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoTab(tasks: TaskStore, task: WorkTask, actions: CaseActions) {
    fun save(t: WorkTask) = tasks.upsert(t.copy(updatedAt = System.currentTimeMillis()))
    var newItem by remember(task.id) { mutableStateOf("") }
    var newLaw by remember(task.id) { mutableStateOf("") }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard(Modifier.weight(1f).fillMaxHeight()) {
            CardTitle("메모")
            LocalField("case-memo-${task.id}", task.memo, Modifier.fillMaxSize(), "검사 중 메모(이 PC에만 저장)", false) { save(task.copy(memo = it)) }
        }
        AppCard(Modifier.width(380.dp).fillMaxHeight()) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CardTitle("할 일") { Pill("${task.checklist.count { it.done }}/${task.checklist.size}") }
                task.checklist.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.done, { v -> save(task.copy(checklist = task.checklist.map { if (it.id == c.id) it.copy(done = v) else it })) })
                        Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { save(task.copy(checklist = task.checklist.filter { it.id != c.id })) }) { Icon(Icons.Outlined.Close, "지우기", Modifier.size(16.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(newItem, { newItem = it }, Modifier.weight(1f), singleLine = true, label = { Text("할 일 추가") })
                    IconButton(onClick = { if (newItem.isNotBlank()) { save(task.copy(checklist = task.checklist + CheckItem(text = newItem.trim()))); newItem = "" } }) { Icon(Icons.Outlined.Add, "추가") }
                }
                HorizontalDivider()
                CardTitle("관련 법령")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.laws.forEach { l ->
                        androidx.compose.material3.InputChip(selected = false, onClick = { actions.openLaw(l) }, label = { Text(l) },
                            trailingIcon = { Icon(Icons.Outlined.Close, "빼기", Modifier.size(14.dp).clickable { save(task.copy(laws = task.laws - l)) }) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(newLaw, { newLaw = it }, Modifier.weight(1f), singleLine = true, label = { Text("예: 자본시장법 제54조") })
                    IconButton(onClick = { if (newLaw.isNotBlank()) { save(task.copy(laws = (task.laws + newLaw.trim()).distinct())); newLaw = "" } }) { Icon(Icons.Outlined.Add, "추가") }
                }
            }
        }
    }
}
