package io.github.hdlee73.fsssupporter.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.hdlee73.fsssupporter.dart.DartModel
import io.github.hdlee73.fsssupporter.dart.Load
import io.github.hdlee73.fsssupporter.data.SettingsRepo
import io.github.hdlee73.fsssupporter.law.LawCache
import io.github.hdlee73.fsssupporter.tasks.AgendaKind
import io.github.hdlee73.fsssupporter.tasks.TaskStore
import io.github.hdlee73.fsssupporter.tasks.statusTone
import io.github.hdlee73.fsssupporter.tasks.dueLabel
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.SearchField
import io.github.hdlee73.fsssupporter.ui.Tone
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** 대시보드에서 다른 화면으로 가는 길 */
class DashboardActions(
    val searchLaw: (String) -> Unit,
    val openTask: (String?) -> Unit,
    val newTask: () -> Unit,
    val openCalendar: () -> Unit,
    val openCorp: (String) -> Unit,
    val openDart: () -> Unit,
    val openOpinion: () -> Unit,
    val openSanction: () -> Unit,
    val openSettings: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(store: TaskStore, dart: DartModel, settings: SettingsRepo, cache: LawCache, a: DashboardActions) {
    val data by store.data.collectAsState()
    val saved by dart.saved.collectAsState()
    val newCheck by dart.newCheck.collectAsState()
    val s by settings.state.collectAsState()
    val offline by cache.offline.collectAsState()
    val today = LocalDate.now()
    val open = remember(data) { store.openTasks() }
    val overdue = remember(data) { store.overdue(today) }
    val agenda = remember(data) { store.agenda(today, 14) }
    val weekCount = agenda.count { it.date.isBefore(today.plusDays(7)) }
    val newFilings = saved.newCount.values.sum()
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // 관심기업 새 공시는 하루에 한 번만 자동으로 센다(내부망 부하·한도 고려).
        if (System.currentTimeMillis() - saved.lastNewCheck > 6 * 3600 * 1000L) dart.checkNewFilings()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(today.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")) + " " + today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("오늘의 업무", style = MaterialTheme.typography.titleLarge)
            }
            if (offline) Pill("법령 저장본 사용 중", Tone.WARN)
        }
        SearchField(query, { query = it }, { if (query.isNotBlank()) a.searchLaw(query.trim()) }, "법령·조문 바로 검색 (예: 자본시장법 제54조, 지배구조법 내부통제)", Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kpi("진행 중 작업", open.size.toString(), Tone.ACCENT, Modifier.weight(1f)) { a.openTask(null) }
            Kpi("기한 지남", overdue.size.toString(), if (overdue.isEmpty()) Tone.OK else Tone.BAD, Modifier.weight(1f)) { a.openTask(overdue.firstOrNull()?.id) }
            Kpi("7일 내 일정·마감", weekCount.toString(), Tone.NEUTRAL, Modifier.weight(1f)) { a.openCalendar() }
            Kpi("관심기업 새 공시", newFilings.toString(), if (newFilings > 0) Tone.WARN else Tone.NEUTRAL, Modifier.weight(1f)) { a.openDart() }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.weight(1.2f)) {
                CardTitle("진행 중인 작업") {
                    TextButton(onClick = a.newTask) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text(" 새 작업") }
                }
                if (open.isEmpty()) {
                    Text("진행 중인 검사·리포트·의견서 작업을 등록하면 여기에 마감순으로 보입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                open.take(7).forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { a.openTask(t.id) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(t.kind.label)
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val sub = listOfNotNull(t.target.ifBlank { null }, if (t.checklist.isNotEmpty()) "${t.checklist.count { it.done }}/${t.checklist.size} 완료" else null).joinToString(" · ")
                            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        dueLabel(t.dueDate(), today)?.let { label ->
                            val due = t.dueDate()
                            Pill(label, if (due != null && due.isBefore(today)) Tone.BAD else if (due != null && !due.isAfter(today.plusDays(3))) Tone.WARN else Tone.NEUTRAL)
                        }
                        Pill(t.status.label, statusTone(t.status))
                    }
                }
                if (open.size > 7) TextButton(onClick = { a.openTask(null) }) { Text("전체 ${open.size}건 보기") }
            }
            AppCard(Modifier.weight(1f)) {
                CardTitle("다가오는 일정 (14일)") {
                    IconButton(onClick = a.openCalendar) { Icon(Icons.Outlined.Event, "달력") }
                }
                if (agenda.isEmpty()) Text("예정된 일정이나 마감이 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                agenda.take(9).forEach { item ->
                    Row(Modifier.fillMaxWidth().clickable { if (item.taskId != null) a.openTask(item.taskId) else a.openCalendar() }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val d = item.date
                        Text(if (d == today) "오늘" else if (d == today.plusDays(1)) "내일" else "${d.monthValue}/${d.dayOfMonth}(${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})",
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp),
                            color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (item.time.isNotBlank()) Text(item.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        when (item.kind) {
                            AgendaKind.DUE -> Pill("마감", Tone.WARN)
                            AgendaKind.START -> Pill("시작", Tone.ACCENT)
                            AgendaKind.SCHEDULE -> {}
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard(Modifier.weight(1f)) {
                CardTitle("관심기업 공시") {
                    IconButton(onClick = { dart.checkNewFilings() }, enabled = dart.hasKey && newCheck !is Load.Loading) { Icon(Icons.Outlined.Refresh, "새 공시 확인") }
                }
                when {
                    !dart.hasKey -> {
                        Text("OpenDART 인증키를 넣으면 관심기업의 새 공시를 알려 드립니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = a.openSettings) { Text("설정 열기") }
                    }
                    saved.watch.isEmpty() -> {
                        Text("기업 개황·공시 화면에서 ☆를 눌러 관심기업을 추가하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = a.openDart) { Icon(Icons.Outlined.Business, null, Modifier.size(16.dp)); Text(" 기업 찾기") }
                    }
                    else -> {
                        if (newCheck is Load.Loading) Text("새 공시를 확인하는 중…", style = MaterialTheme.typography.bodySmall)
                        if (newCheck is Load.Failed) Text((newCheck as Load.Failed).message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        saved.watch.sortedByDescending { saved.newCount[it.corp] ?: 0 }.take(6).forEach { c ->
                            Row(Modifier.fillMaxWidth().clickable { a.openCorp(c.corp) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = saved.newCount[c.corp] ?: 0
                                if (n > 0) Pill("새 공시 $n", Tone.WARN) else Pill("변동 없음")
                            }
                        }
                    }
                }
            }
            AppCard(Modifier.weight(1f)) {
                CardTitle("최근 법령 검색")
                if (s.recentLaw.isEmpty() && s.favoriteLaw.isEmpty()) Text("검색한 법령이 여기에 남습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    s.favoriteLaw.take(6).forEach { q -> AssistChip(onClick = { a.searchLaw(q) }, label = { Text("★ $q", maxLines = 1) }) }
                    s.recentLaw.filter { it !in s.favoriteLaw }.take(8).forEach { q -> AssistChip(onClick = { a.searchLaw(q) }, label = { Text(q, maxLines = 1) }) }
                }
            }
            AppCard(Modifier.width(260.dp)) {
                CardTitle("바로 가기")
                QuickLink(Icons.Outlined.Assignment, "새 검사의견서·조치안", a.openOpinion)
                QuickLink(Icons.Outlined.Assignment, "제재 양정 계산", a.openSanction)
                QuickLink(Icons.Outlined.Event, "일정 추가", a.openCalendar)
                val (files, bytes) = remember(offline) { cache.stats() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("법령 저장본 ${files}건 · ${bytes / 1024 / 1024}MB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (settings.lawOc.isNotBlank()) "법령 연결" else "법령 데모", if (settings.lawOc.isNotBlank()) Tone.OK else Tone.WARN)
                    Pill(if (dart.hasKey) "DART 연결" else "DART 키 없음", if (dart.hasKey) Tone.OK else Tone.WARN)
                }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, tone: Tone, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier.clickable(onClick = onClick)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (tone == Tone.BAD && value != "0") Pill("확인 필요", Tone.BAD)
        }
    }
}

@Composable
private fun QuickLink(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
