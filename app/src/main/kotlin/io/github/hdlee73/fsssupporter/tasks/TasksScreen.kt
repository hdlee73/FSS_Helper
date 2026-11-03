package io.github.hdlee73.fsssupporter.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.EmptyState
import io.github.hdlee73.fsssupporter.ui.LocalStatus
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.SearchField
import io.github.hdlee73.fsssupporter.ui.Tone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun statusTone(s: TaskStatus): Tone = when (s) {
    TaskStatus.PLANNED -> Tone.NEUTRAL
    TaskStatus.IN_PROGRESS -> Tone.ACCENT
    TaskStatus.REVIEW -> Tone.WARN
    TaskStatus.DONE -> Tone.OK
    TaskStatus.ON_HOLD -> Tone.NEUTRAL
}

/** 마감까지 남은 날: "D-3", "D-day", "3일 지남" */
fun dueLabel(due: LocalDate?, today: LocalDate = LocalDate.now()): String? {
    due ?: return null
    val d = ChronoUnit.DAYS.between(today, due)
    return when {
        d == 0L -> "D-day"
        d > 0 -> "D-$d"
        else -> "${-d}일 지남"
    }
}

/** 화면 상태(선택된 작업·탭·달력 월). 메뉴를 오가도 유지되게 화면 밖에 둔다. */
class TasksUi {
    var tab by mutableStateOf(0)
    var selectedId by mutableStateOf<String?>(null)
    var month by mutableStateOf(YearMonth.now())
    var day by mutableStateOf(LocalDate.now())
    var filter by mutableStateOf("open")
    var find by mutableStateOf("")

    fun back(): Boolean {
        if (selectedId != null) { selectedId = null; return true }
        if (tab != 0) { tab = 0; return true }
        return false
    }
}

@Composable
fun TasksScreen(store: TaskStore, ui: TasksUi, onOpenLaw: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("작업 관리", "진행 중인 검사·리포트·의견서와 일정을 한곳에서 관리합니다. 모든 내용은 이 PC에만 저장됩니다.") {
            Button(onClick = {
                val t = WorkTask(title = "새 작업", status = TaskStatus.IN_PROGRESS, start = LocalDate.now().toString())
                store.upsert(t); ui.tab = 0; ui.selectedId = t.id
            }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("새 작업") }
        }
        TabRow(selectedTabIndex = ui.tab, modifier = Modifier.padding(horizontal = 20.dp).width(320.dp), containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = ui.tab == 0, onClick = { ui.tab = 0 }, text = { Text("작업 목록") })
            Tab(selected = ui.tab == 1, onClick = { ui.tab = 1 }, text = { Text("일정 달력") })
        }
        Spacer(Modifier.height(10.dp))
        if (ui.tab == 0) TaskListTab(store, ui, onOpenLaw) else CalendarTab(store, ui)
    }
}

@Composable
private fun TaskListTab(store: TaskStore, ui: TasksUi, onOpenLaw: (String) -> Unit) {
    val data by store.data.collectAsState()
    val today = LocalDate.now()
    val list = data.tasks
        .filter { when (ui.filter) { "open" -> it.isOpen; "done" -> !it.isOpen; else -> true } }
        .filter { ui.find.isBlank() || it.title.contains(ui.find) || it.target.contains(ui.find) || it.memo.contains(ui.find) }
        .sortedWith(compareBy({ !it.isOpen }, { it.dueDate() ?: LocalDate.MAX }, { -it.updatedAt }))
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(360.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(ui.find, { ui.find = it }, {}, "제목·대상기관·메모에서 찾기", Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("open" to "진행 중", "all" to "전체", "done" to "완료").forEach { (k, l) -> FilterChip(selected = ui.filter == k, onClick = { ui.filter = k }, label = { Text(l) }) }
            }
            val ls = rememberLazyListState()
            Box(Modifier.weight(1f)) {
                LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (list.isEmpty()) item {
                        EmptyState(Icons.Outlined.Assignment, "작업이 없어요", "오른쪽 위 '새 작업'으로 검사, 리포트, 의견서 작성 같은 일을 등록하세요. 마감일을 넣으면 대시보드와 달력에 나타납니다.")
                    }
                    items(list, key = { it.id }) { t -> TaskCard(t, t.id == ui.selectedId, today) { ui.selectedId = t.id } }
                }
                VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val t = data.tasks.firstOrNull { it.id == ui.selectedId }
            if (t == null) AppCard(Modifier.fillMaxSize()) {
                EmptyState(Icons.Outlined.Assignment, "작업을 고르세요", "왼쪽 목록에서 작업을 누르면 여기에서 내용, 체크리스트, 관련 법령, 일정을 고칠 수 있습니다.")
            } else TaskEditor(t, store, data.schedules.filter { it.taskId == t.id }, onOpenLaw, onDeleted = { ui.selectedId = null })
        }
    }
}

@Composable
fun TaskCard(t: WorkTask, selected: Boolean, today: LocalDate, onClick: () -> Unit) {
    val status = LocalStatus.current
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(t.kind.label)
                Text(t.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Pill(t.status.label, statusTone(t.status))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(listOf(t.target, t.due.takeIf { it.isNotBlank() }?.let { "마감 $it" }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                if (t.isOpen) dueLabel(t.dueDate(), today)?.let { label ->
                    val overdue = t.dueDate()!!.isBefore(today)
                    val soon = !overdue && ChronoUnit.DAYS.between(today, t.dueDate()) <= 3
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (overdue) status.bad else if (soon) status.warn else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                }
            }
            if (t.checklist.isNotEmpty()) LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

@Composable
private fun <E : Enum<E>> EnumMenu(label: String, value: E, values: List<E>, text: (E) -> String, onPick: (E) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label: ${text(value)}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { v -> DropdownMenuItem(text = { Text(text(v)) }, onClick = { onPick(v); open = false }) }
        }
    }
}

/**
 * 입력 중인 글은 화면 안에 두고 저장만 따로 한다. 저장된 값을 그대로 다시 받아 그리면
 * 한글 조합 중인 글자가 끊길 수 있어서다.
 */
@Composable
fun LocalField(key: String, initial: String, modifier: Modifier, label: String, singleLine: Boolean, onSave: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onSave(it) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun DateField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    val bad = normalizeDate(text) == null
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; normalizeDate(it)?.let(onChange) },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { Text("2026-11-30") },
        singleLine = true,
        isError = bad,
        supportingText = if (bad) ({ Text("예: 2026-11-30") }) else null,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditor(t: WorkTask, store: TaskStore, schedules: List<Schedule>, onOpenLaw: (String) -> Unit, onDeleted: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    var newCheck by remember(t.id) { mutableStateOf("") }
    var newLaw by remember(t.id) { mutableStateOf("") }
    var addSchedule by remember { mutableStateOf(false) }
    val sc = rememberScrollState()
    AppCard(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth().verticalScroll(sc).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocalField(t.id + "title", t.title, Modifier.weight(1f), "제목", singleLine = true) { store.upsert(t.copy(title = it)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "작업 삭제") }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EnumMenu("종류", t.kind, TaskKind.entries, { it.label }) { store.upsert(t.copy(kind = it)) }
                    EnumMenu("상태", t.status, TaskStatus.entries, { it.label }) { store.upsert(t.copy(status = it)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LocalField(t.id + "target", t.target, Modifier.weight(1.4f), "대상 기관", singleLine = true) { store.upsert(t.copy(target = it)) }
                    DateField("시작일", t.start, Modifier.weight(1f)) { store.upsert(t.copy(start = it)) }
                    DateField("마감일", t.due, Modifier.weight(1f)) { store.upsert(t.copy(due = it)) }
                }
                LocalField(t.id + "memo", t.memo, Modifier.fillMaxWidth().height(120.dp), "메모", singleLine = false) { store.upsert(t.copy(memo = it)) }

                CardTitle("체크리스트 ${t.checklist.count { it.done }}/${t.checklist.size}")
                t.checklist.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(c.done, { v -> store.upsert(t.copy(checklist = t.checklist.map { if (it.id == c.id) it.copy(done = v) else it })) })
                        Text(c.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { store.upsert(t.copy(checklist = t.checklist.filter { it.id != c.id })) }) { Icon(Icons.Outlined.Close, "항목 삭제", Modifier.size(16.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newCheck, { newCheck = it }, Modifier.weight(1f), placeholder = { Text("할 일 추가 (예: 자료요구서 발송)") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(enabled = newCheck.isNotBlank(), onClick = { store.upsert(t.copy(checklist = t.checklist + CheckItem(text = newCheck.trim()))); newCheck = "" }) { Text("추가") }
                }
                if (t.kind == TaskKind.INSPECTION && t.checklist.isEmpty()) {
                    TextButton(onClick = { store.upsert(t.copy(checklist = INSPECTION_STEPS.map { CheckItem(text = it) })) }) { Text("검사 기본 단계 넣기") }
                }

                CardTitle("관련 법령")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    t.laws.forEach { q ->
                        AssistChip(onClick = { onOpenLaw(q) }, label = { Text(q) }, trailingIcon = {
                            Icon(Icons.Outlined.Close, "삭제", Modifier.size(14.dp).clickable { store.upsert(t.copy(laws = t.laws - q)) })
                        })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(newLaw, { newLaw = it }, Modifier.weight(1f), placeholder = { Text("예: 자본시장법 제54조") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(enabled = newLaw.isNotBlank(), onClick = { store.upsert(t.copy(laws = (t.laws + newLaw.trim()).distinct())); newLaw = "" }) { Text("추가") }
                }

                CardTitle("일정 ${schedules.size}건") { TextButton(onClick = { addSchedule = true }) { Text("일정 추가") } }
                schedules.sortedBy { it.date + it.time }.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Event, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("${s.date} ${s.time}".trim(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(120.dp))
                        Text(s.title + if (s.place.isNotBlank()) " · ${s.place}" else "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { store.deleteSchedule(s.id) }) { Icon(Icons.Outlined.Close, "일정 삭제", Modifier.size(16.dp)) }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(sc), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("작업을 삭제할까요?") },
        text = { Text("'${t.title}'과 체크리스트가 지워집니다. 연결된 일정은 남습니다.") },
        confirmButton = { TextButton(onClick = { store.delete(t.id); confirmDelete = false; onDeleted() }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
    )
    if (addSchedule) ScheduleDialog(Schedule(title = "", date = LocalDate.now().toString(), taskId = t.id), onDismiss = { addSchedule = false }) { store.upsertSchedule(it); addSchedule = false }
}

private val INSPECTION_STEPS = listOf(
    "사전 자료요구서 발송", "사전 분석 및 검사 계획 수립", "현장검사 착수", "면담·확인서 징구", "검사 종료 및 결과 정리",
    "검사의견서 작성", "조치안(제재 양정) 작성", "검사서 작성·결재",
)

@Composable
fun ScheduleDialog(initial: Schedule, onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(initial.date) }
    var time by remember { mutableStateOf(initial.time) }
    var place by remember { mutableStateOf(initial.place) }
    val dateOk = !normalizeDate(date).isNullOrBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.title.isBlank()) "일정 추가" else "일정 고치기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("제목") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("날짜") }, singleLine = true, isError = !dateOk, placeholder = { Text("2026-11-30") })
                OutlinedTextField(time, { time = it }, label = { Text("시간 (선택)") }, singleLine = true, placeholder = { Text("14:00") })
                OutlinedTextField(place, { place = it }, label = { Text("장소 (선택)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && dateOk, onClick = { onSave(initial.copy(title = title.trim(), date = normalizeDate(date)!!, time = time.trim(), place = place.trim())) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun CalendarTab(store: TaskStore, ui: TasksUi) {
    val data by store.data.collectAsState()
    val month = ui.month
    val first = month.atDay(1)
    val agenda = remember(data, month) { store.agenda(first.minusDays(7), 50) }
    val byDay = agenda.groupBy { it.date }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Schedule?>(null) }
    val today = LocalDate.now()
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ui.month = month.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "이전 달") }
                Text("${month.year}년 ${month.monthValue}월", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { ui.month = month.plusMonths(1) }) { Icon(Icons.Outlined.ChevronRight, "다음 달") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { ui.month = YearMonth.now(); ui.day = today }) { Text("오늘") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, d ->
                    Text(d, Modifier.weight(1f).padding(4.dp), style = MaterialTheme.typography.labelMedium, color = if (i == 0) LocalStatus.current.bad else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val offset = first.dayOfWeek.value % 7 // 일요일 시작
            val start = first.minusDays(offset.toLong())
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (w in 0 until 6) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (d in 0 until 7) {
                            val date = start.plusDays((w * 7 + d).toLong())
                            DayCell(date, date.month == month.month, date == today, date == ui.day, byDay[date].orEmpty(), Modifier.weight(1f).fillMaxHeight()) { ui.day = date }
                        }
                    }
                }
            }
        }
        AppCard(Modifier.width(320.dp).fillMaxHeight()) {
            CardTitle("${ui.day.monthValue}월 ${ui.day.dayOfMonth}일 (${"일월화수목금토"[ui.day.dayOfWeek.value % 7]})") { TextButton(onClick = { adding = true }) { Text("일정 추가") } }
            val items = byDay[ui.day].orEmpty()
            if (items.isEmpty()) Text("일정이 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEach { a ->
                Row(Modifier.fillMaxWidth().clickable {
                    if (a.kind == AgendaKind.SCHEDULE) editing = data.schedules.firstOrNull { it.id == a.refId }
                    else { ui.tab = 0; ui.selectedId = a.taskId }
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(when (a.kind) { AgendaKind.SCHEDULE -> "일정"; AgendaKind.DUE -> "마감"; AgendaKind.START -> "시작" }, when (a.kind) { AgendaKind.SCHEDULE -> Tone.ACCENT; AgendaKind.DUE -> Tone.WARN; AgendaKind.START -> Tone.NEUTRAL })
                    Text((if (a.time.isNotBlank()) a.time + " " else "") + a.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(8.dp))
            CardTitle("앞으로 14일")
            store.agenda(today, 14).take(12).forEach { a ->
                Text("${a.date.monthValue}/${a.date.dayOfMonth} ${a.time} ${a.title}".replace("  ", " "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { ui.month = YearMonth.from(a.date); ui.day = a.date }.padding(vertical = 2.dp))
            }
        }
    }
    if (adding) ScheduleDialog(Schedule(title = "", date = ui.day.toString()), onDismiss = { adding = false }) { store.upsertSchedule(it); adding = false }
    editing?.let { s ->
        ScheduleDialog(s, onDismiss = { editing = null }) { store.upsertSchedule(it); editing = null }
    }
}

@Composable
private fun DayCell(date: LocalDate, inMonth: Boolean, isToday: Boolean, selected: Boolean, items: List<AgendaItem>, modifier: Modifier, onClick: () -> Unit) {
    val status = LocalStatus.current
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier.border(if (selected) 1.5.dp else 1.dp, border, MaterialTheme.shapes.small)
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "${date.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
            color = when {
                !inMonth -> MaterialTheme.colorScheme.outline
                date.dayOfWeek == DayOfWeek.SUNDAY -> status.bad
                else -> MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        items.take(3).forEach { a ->
            val c = when (a.kind) { AgendaKind.DUE -> status.warn; AgendaKind.SCHEDULE -> MaterialTheme.colorScheme.primary; AgendaKind.START -> MaterialTheme.colorScheme.onSurfaceVariant }
            Text(a.title, style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (items.size > 3) Text("+${items.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
