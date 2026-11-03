package io.github.hdlee73.fsssupporter

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuOpen
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.hdlee73.fsssupporter.dart.DartModel
import io.github.hdlee73.fsssupporter.dart.DartScreen
import io.github.hdlee73.fsssupporter.data.SettingsRepo
import io.github.hdlee73.fsssupporter.home.AboutScreen
import io.github.hdlee73.fsssupporter.home.DashboardActions
import io.github.hdlee73.fsssupporter.home.DashboardScreen
import io.github.hdlee73.fsssupporter.home.PrefetchUi
import io.github.hdlee73.fsssupporter.home.SettingsScreen
import io.github.hdlee73.fsssupporter.inspection.FormRef
import io.github.hdlee73.fsssupporter.inspection.InspectionGuide
import io.github.hdlee73.fsssupporter.inspection.InspectionScreen
import io.github.hdlee73.fsssupporter.inspection.InspectionStore
import io.github.hdlee73.fsssupporter.inspection.InspectionUi
import io.github.hdlee73.fsssupporter.law.LawModule
import io.github.hdlee73.fsssupporter.law.LawScreen
import io.github.hdlee73.fsssupporter.law.LawSearchModel
import io.github.hdlee73.fsssupporter.opinion.CitationVerifier
import io.github.hdlee73.fsssupporter.opinion.OpinionModel
import io.github.hdlee73.fsssupporter.opinion.OpinionScreen
import io.github.hdlee73.fsssupporter.sanction.SanctionScreen
import io.github.hdlee73.fsssupporter.sanction.SanctionStore
import io.github.hdlee73.fsssupporter.sanction.SanctionUi
import io.github.hdlee73.fsssupporter.tasks.TaskStatus
import io.github.hdlee73.fsssupporter.tasks.TaskStore
import io.github.hdlee73.fsssupporter.tasks.TasksScreen
import io.github.hdlee73.fsssupporter.tasks.TasksUi
import io.github.hdlee73.fsssupporter.tasks.WorkTask
import io.github.hdlee73.fsssupporter.ui.AppTheme
import io.github.hdlee73.fsssupporter.ui.Dest
import io.github.hdlee73.fsssupporter.ui.Navigator
import io.github.hdlee73.fsssupporter.update.UpdateModel
import io.github.hdlee73.fsssupporter.update.UpdateState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.system.exitProcess

/** 앱 전체에서 쓰는 모델 묶음. 창이 열려 있는 동안 하나만 있다. */
class AppGraph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val settings = SettingsRepo()
    val nav = Navigator()
    val law = LawModule(settings)
    val lawSearch = LawSearchModel(law, settings, scope)
    val dart = DartModel(settings, scope)
    val tasks = TaskStore()
    val tasksUi = TasksUi()
    val inspection = InspectionStore()
    val inspectionUi = InspectionUi()
    val opinion = OpinionModel(CitationVerifier(law.repository), scope)
    val sanction = SanctionStore()
    val sanctionUi = SanctionUi()
    val update = UpdateModel(settings, scope)
    val prefetch = PrefetchUi()
    val messages = Channel<String>(Channel.BUFFERED)

    fun message(text: String) { messages.trySend(text) }

    init {
        nav.registerInnerBack(Dest.LAW) { lawSearch.back() }
        nav.registerInnerBack(Dest.DART) { dart.back() }
        nav.registerInnerBack(Dest.TASKS) { tasksUi.back() }
        nav.registerInnerBack(Dest.INSPECTION) { inspectionUi.back() }
        nav.registerInnerBack(Dest.OPINION) { if (opinion.selected != null) { opinion.select(null); true } else false }
    }

    fun searchLaw(q: String) {
        nav.go(Dest.LAW)
        lawSearch.onQueryChange(q)
        lawSearch.submit(q)
    }

    /** 검사 서식을 의견서 도우미 초안으로 연다: 기본 서식 → 가져온 원문 → 제목만 있는 빈 초안 순 */
    fun writeForm(form: FormRef, task: WorkTask?) {
        val tplId = InspectionGuide.TEMPLATE_FOR_FORM[form.no]
        val tpl = tplId?.let { id -> opinion.templates.firstOrNull { it.id == id } }
        val raw = inspection.library.value.forms[form.no]
        val body = when {
            tpl != null -> tpl.body
            raw != null -> raw
            else -> "${form.name}\n\n※ 관련 규정: ${form.basis.ifBlank { "-" }} · 대상처: ${form.target.ifBlank { "-" }}\n※ 검사 진행 관리 > 매뉴얼 탭에서 '검사관련 서식'을 가져오면 원문 서식으로 작성할 수 있습니다.\n"
        }
        val title = "제${form.no}호 ${form.name}" + (task?.let { " - ${it.title}" } ?: "")
        opinion.createFromText(title, body, tplId.orEmpty(), task?.id)
        nav.go(Dest.OPINION)
        message("'${form.name}' 초안을 만들었습니다.")
    }

    fun insertToOpinion(text: String) {
        opinion.insert(text)
        nav.go(Dest.OPINION)
        message("의견서 도우미의 초안 끝에 붙였습니다.")
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    System.setProperty("compose.interop.blending", "false")
    val graph = AppGraph()
    application {
        val s by graph.settings.state.collectAsState()
        val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = { graph.scope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); exitApplication() },
            state = windowState,
            title = "FSS_Supporter",
            icon = painterResource("icons/app.png"),
            onPreviewKeyEvent = { handleKey(it, graph) },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(1024, 640)
                if (java.awt.Toolkit.getDefaultToolkit().screenSize.width <= 1920 && windowState.placement == WindowPlacement.Floating) {
                    // 갤럭시북 같은 13~15형 화면은 처음부터 최대화해서 쓴다.
                    if (java.awt.Toolkit.getDefaultToolkit().screenSize.width < 1500) windowState.placement = WindowPlacement.Maximized
                }
                graph.update.autoCheck()
                graph.dart.ensureIndex()
            }
            AppTheme(s.theme) {
                Shell(graph)
            }
        }
    }
}

private fun handleKey(e: KeyEvent, g: AppGraph): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when {
        e.isAltPressed && e.key == Key.DirectionLeft -> g.nav.back()
        e.key == Key.Back -> g.nav.back()
        e.isCtrlPressed && e.key == Key.B -> { g.settings.update { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }; true }
        e.isCtrlPressed && e.key == Key.K -> { g.nav.go(Dest.LAW); true }
        e.isCtrlPressed && e.key in NUM_KEYS -> {
            Dest.entries.filter { !it.lower }.getOrNull(NUM_KEYS.indexOf(e.key))?.let { g.nav.go(it) }; true
        }
        else -> false
    }
}

private val NUM_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Shell(g: AppGraph) {
    val s by g.settings.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        for (m in g.messages) {
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(m) }
        }
    }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            // 마우스 '뒤로' 버튼
            .onPointerEvent(PointerEventType.Press) { ev -> if (ev.button == PointerButton.Back) g.nav.back() },
    ) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(g, s.sidebarCollapsed)
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(g)
                Box(Modifier.weight(1f).fillMaxWidth()) { Content(g) }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Snackbar(it) }
    }
}

@Composable
private fun TopBar(g: AppGraph) {
    val update by g.update.state.collectAsState()
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { g.nav.back() }, enabled = g.nav.canGoBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로 (Alt+←)", Modifier.size(18.dp))
        }
        Text(g.nav.current.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (update is UpdateState.Available || update is UpdateState.Ready) {
            TextButton(onClick = { g.nav.go(Dest.ABOUT) }) { Text("새 버전이 있습니다") }
        }
    }
}

@Composable
private fun Content(g: AppGraph) {
    when (g.nav.current) {
        Dest.DASHBOARD -> DashboardScreen(
            g.tasks, g.dart, g.settings, g.law.cache,
            DashboardActions(
                searchLaw = g::searchLaw,
                openTask = { id ->
                    val t = g.tasks.data.value.tasks.firstOrNull { it.id == id }
                    if (t != null && t.kind == io.github.hdlee73.fsssupporter.tasks.TaskKind.INSPECTION) { g.inspectionUi.tab = 0; g.inspectionUi.taskId = id; g.inspectionUi.stepId = null; g.nav.go(Dest.INSPECTION) }
                    else { g.tasksUi.tab = 0; g.tasksUi.filter = "open"; g.tasksUi.selectedId = id; g.nav.go(Dest.TASKS) }
                },
                newTask = {
                    val t = WorkTask(title = "새 작업", status = TaskStatus.IN_PROGRESS, start = LocalDate.now().toString())
                    g.tasks.upsert(t); g.tasksUi.tab = 0; g.tasksUi.selectedId = t.id; g.nav.go(Dest.TASKS)
                },
                openCalendar = { g.tasksUi.tab = 1; g.nav.go(Dest.TASKS) },
                openCorp = { code -> g.dart.saved.value.watch.firstOrNull { it.corp == code }?.let { g.dart.open(it) }; g.nav.go(Dest.DART) },
                openDart = { g.nav.go(Dest.DART) },
                openOpinion = { g.nav.go(Dest.OPINION) },
                openSanction = { g.nav.go(Dest.SANCTION) },
                openSettings = { g.nav.go(Dest.SETTINGS) },
            ),
            inspectionStep = { id -> g.inspection.of(id).current()?.title },
        )
        Dest.TASKS -> TasksScreen(g.tasks, g.tasksUi, onOpenLaw = g::searchLaw)
        Dest.INSPECTION -> InspectionScreen(
            g.inspection, g.tasks, g.inspectionUi, onWriteForm = g::writeForm,
            onOpenTasks = { id -> g.tasksUi.tab = 0; g.tasksUi.filter = "all"; g.tasksUi.selectedId = id; g.nav.go(Dest.TASKS) },
            onMessage = g::message,
        )
        Dest.LAW -> LawScreen(g.lawSearch, g.settings, g.law.cache, onInsertToOpinion = g::insertToOpinion, onMessage = g::message)
        Dest.DART -> DartScreen(g.dart, onOpenSettings = { g.nav.go(Dest.SETTINGS) })
        Dest.OPINION -> OpinionScreen(g.opinion, g.lawSearch.isSample, onMessage = g::message)
        Dest.SANCTION -> SanctionScreen(g.sanction, g.sanctionUi, onOpenLaw = g::searchLaw, onInsertToOpinion = g::insertToOpinion, onMessage = g::message)
        Dest.SETTINGS -> SettingsScreen(g.settings, g.law, g.dart, g.prefetch, g.scope, onMessage = g::message)
        Dest.ABOUT -> AboutScreen(g.update, onInstallAndExit = { exitProcess(0) }, onMessage = g::message)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sidebar(g: AppGraph, collapsed: Boolean) {
    val width by animateDpAsState(if (collapsed) 64.dp else 232.dp)
    val tasks by g.tasks.data.collectAsState()
    val saved by g.dart.saved.collectAsState()
    val update by g.update.state.collectAsState()
    val overdue = remember(tasks) { g.tasks.overdue(LocalDate.now()).size }
    val newFilings = saved.newCount.values.sum()
    Column(Modifier.width(width).fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(vertical = 10.dp, horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!collapsed) {
                Image(painterResource("icons/app.png"), null, Modifier.padding(start = 6.dp).size(26.dp))
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("FSS_Supporter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("검사·감독 업무 도우미", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = { g.settings.update { it.copy(sidebarCollapsed = !collapsed) } }, modifier = if (collapsed) Modifier.fillMaxWidth() else Modifier) {
                Icon(if (collapsed) Icons.Outlined.Menu else Icons.Outlined.MenuOpen, if (collapsed) "메뉴 펼치기 (Ctrl+B)" else "메뉴 접기 (Ctrl+B)")
            }
        }
        Spacer(Modifier.height(8.dp))
        Dest.entries.filter { !it.lower }.forEachIndexed { i, d ->
            val badge = when (d) {
                Dest.TASKS -> overdue.takeIf { it > 0 }?.toString()
                Dest.DART -> newFilings.takeIf { it > 0 }?.toString()
                else -> null
            }
            NavItem(d, g.nav.current == d, collapsed, badge, "Ctrl+${i + 1}") { g.nav.go(d) }
        }
        Spacer(Modifier.weight(1f))
        Dest.entries.filter { it.lower }.forEach { d ->
            val badge = if (d == Dest.ABOUT && (update is UpdateState.Available || update is UpdateState.Ready)) "N" else null
            NavItem(d, g.nav.current == d, collapsed, badge, null) { g.nav.go(d) }
        }
        if (!collapsed) Text("v${BuildInfo.VERSION}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavItem(d: Dest, selected: Boolean, collapsed: Boolean, badge: String?, shortcut: String?, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val item = @Composable {
        Row(
            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
        ) {
            Box {
                Icon(d.icon, d.label, Modifier.size(20.dp), tint = fg)
                if (collapsed && badge != null) Badge(Modifier.align(Alignment.TopEnd).padding(start = 12.dp)) { Text(badge) }
            }
            if (!collapsed) {
                Text(d.label, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (badge != null) Badge { Text(badge) }
            }
        }
    }
    Surface(color = androidx.compose.ui.graphics.Color.Transparent, modifier = Modifier.padding(vertical = 1.dp)) {
        if (collapsed) {
            TooltipBox(TooltipDefaults.rememberPlainTooltipPositionProvider(), { PlainTooltip { Text(if (shortcut != null) "${d.label} ($shortcut)" else d.label) } }, rememberTooltipState()) { item() }
        } else item()
    }
}
