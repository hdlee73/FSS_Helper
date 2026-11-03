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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import io.github.hdlee73.fsssupporter.cases.CaseActions
import io.github.hdlee73.fsssupporter.cases.CaseFiles
import io.github.hdlee73.fsssupporter.cases.CaseScreen
import io.github.hdlee73.fsssupporter.cases.CaseUi
import io.github.hdlee73.fsssupporter.cases.FindingStore
import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.Hancom
import io.github.hdlee73.fsssupporter.home.KeyGuideDialog
import io.github.hdlee73.fsssupporter.home.KeyKind
import io.github.hdlee73.fsssupporter.home.testDart
import io.github.hdlee73.fsssupporter.home.testLaw
import io.github.hdlee73.fsssupporter.inspection.FormRef
import io.github.hdlee73.fsssupporter.inspection.GuideScreen
import io.github.hdlee73.fsssupporter.inspection.GuideUi
import io.github.hdlee73.fsssupporter.inspection.InspectionGuide
import io.github.hdlee73.fsssupporter.inspection.InspectionStore
import io.github.hdlee73.fsssupporter.law.LawModule
import io.github.hdlee73.fsssupporter.law.LawScreen
import io.github.hdlee73.fsssupporter.law.LawSearchModel
import io.github.hdlee73.fsssupporter.opinion.CitationVerifier
import io.github.hdlee73.fsssupporter.opinion.OpinionModel
import io.github.hdlee73.fsssupporter.ui.NavGroup
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.verify.VerifyModel
import io.github.hdlee73.fsssupporter.verify.VerifyScreen
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
import kotlinx.coroutines.withContext
import java.io.File
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
    val caseUi = CaseUi()
    val guideUi = GuideUi()
    val findings = FindingStore()
    val verify = VerifyModel(CitationVerifier(law.repository), scope)
    val sanction = SanctionStore()
    val sanctionUi = SanctionUi()
    val update = UpdateModel(settings, scope)
    val prefetch = PrefetchUi()
    val messages = Channel<String>(Channel.BUFFERED)

    /** 이번 실행에서 이미 띄운 인증키 안내(법령·DART) */
    var keyGuideShown by mutableStateOf(setOf<String>())
    /** 지금 띄울 인증키 안내 */
    var keyGuide by mutableStateOf<KeyKind?>(null)

    fun message(text: String) { messages.trySend(text) }

    init {
        nav.registerInnerBack(Dest.LAW) { lawSearch.back() }
        nav.registerInnerBack(Dest.DART) { dart.back() }
        nav.registerInnerBack(Dest.SCHEDULE) { tasksUi.back() }
        nav.registerInnerBack(Dest.CASES) { caseUi.back() }
        nav.registerInnerBack(Dest.GUIDE) { guideUi.back() }
    }

    fun searchLaw(q: String) {
        nav.go(Dest.LAW)
        lawSearch.onQueryChange(q)
        lawSearch.submit(q)
    }

    fun openCase(id: String, tab: Int = 0) {
        caseUi.open(id, tab)
        nav.go(Dest.CASES)
    }

    /**
     * 서식을 원본 그대로 새 한글 파일로 만들어 연다. 검사건이 있으면 그 검사건 폴더에, 없으면 문서\FSS_Helper\서식에.
     * 1) 가져온 서식 원본이 있으면 한글 자동화로 그 서식 부분만 남긴 파일, 2) 안 되면 서식 전체 원본 사본,
     * 3) 원본이 없으면 기본 양식(HWPX) 또는 가져오기 안내.
     */
    fun writeForm(form: FormRef, task: WorkTask?) {
        scope.launch {
            val lib = inspection.library.value
            val dir = withContext(Dispatchers.IO) { task?.let { CaseFiles.dir(it) } ?: File(AppDirs.exports, "서식").apply { mkdirs() } }
            val name = CaseFiles.safe("제${form.no}호 ${form.name}")
            val src = File(lib.formsCopy)
            if (lib.formsCopy.isNotBlank() && src.isFile) {
                message("한글로 '${form.label}' 원본 서식을 만드는 중입니다…")
                when (val r = inspection.formOriginal(form.no, lib)) {
                    is Hancom.Result.Ok -> {
                        val dest = withContext(Dispatchers.IO) { CaseFiles.unique(dir, "$name.${r.file.extension}").also { r.file.copyTo(it) } }
                        Os.open(dest)
                        message("'${dest.name}'을(를) ${if (task != null) "검사건 폴더" else "서식 폴더"}에 만들어 열었습니다.")
                    }
                    is Hancom.Result.Failed -> {
                        val dest = withContext(Dispatchers.IO) { CaseFiles.unique(dir, "$name (서식 전체본).${src.extension}").also { src.copyTo(it) } }
                        Os.open(dest)
                        message(r.message + " 대신 서식 전체 원본의 사본을 열었습니다. Ctrl+F로 '제${form.no}호'를 찾아 쓰세요.")
                    }
                }
                return@launch
            }
            val tpl = InspectionGuide.TEMPLATE_FOR_FORM[form.no]?.let { id -> OpinionModel.BUILT_IN.firstOrNull { it.id == id } }
            if (tpl != null) {
                val dest = withContext(Dispatchers.IO) {
                    CaseFiles.unique(dir, "$name.hwpx").also { f -> f.outputStream().use { com.hdlee73.everylaw.core.domain.HwpxExporter.writeDocument(form.label, OpinionModel.toParagraphs(tpl.body), it) } }
                }
                Os.open(dest)
                message("기본 양식으로 만들었습니다. 원본 서식 그대로 쓰려면 서식·매뉴얼 > 가져오기에서 검사관련 서식 파일을 가져오세요.")
            } else {
                guideUi.tab = 2
                nav.go(Dest.GUIDE)
                message("서식 원본이 없습니다. '검사관련 서식' 한글 파일을 먼저 가져오세요.")
            }
        }
    }

    /** 서식 원본을 보기용 사본으로 한글에서 연다. */
    fun viewForm(form: FormRef) {
        scope.launch {
            val lib = inspection.library.value
            message("한글로 '${form.label}' 원본을 여는 중입니다…")
            val tmp = AppDirs.sub("tmp")
            when (val r = inspection.formOriginal(form.no, lib)) {
                is Hancom.Result.Ok -> Os.open(withContext(Dispatchers.IO) { File(tmp, "보기_제${form.no}호.${r.file.extension}").also { r.file.copyTo(it, overwrite = true) } })
                is Hancom.Result.Failed -> {
                    val src = File(lib.formsCopy)
                    if (src.isFile) Os.open(withContext(Dispatchers.IO) { File(tmp, "보기_서식전체.${src.extension}").also { src.copyTo(it, overwrite = true) } })
                    message(r.message + if (src.isFile) " 서식 전체 원본을 열었습니다." else "")
                }
            }
        }
    }

    fun deleteCase(task: WorkTask, withFiles: Boolean) {
        val dir = CaseFiles.dir(task, create = false)
        tasks.delete(task.id)
        inspection.remove(task.id)
        findings.removeCase(task.id)
        caseUi.caseId = null
        if (withFiles) scope.launch { withContext(Dispatchers.IO) { if (dir.isDirectory) dir.deleteRecursively() } }
        message("'${task.title}' 검사건을 삭제했습니다." + if (withFiles) " 문서 폴더도 지웠습니다." else " 문서 폴더는 남겨 두었습니다.")
    }

    /** 키 없이 법률 도우미·기업 개황을 처음 열면 발급 안내를 띄운다(다시 보지 않기 전까지 실행마다 한 번). */
    fun maybeKeyGuide(dest: Dest) {
        val kind = when (dest) {
            Dest.LAW, Dest.VERIFY -> KeyKind.LAW.takeIf { settings.lawOc.isBlank() }
            Dest.DART -> KeyKind.DART.takeIf { settings.dartKey.isBlank() }
            else -> null
        } ?: return
        if (kind.id in settings.state.value.keyGuideHidden || kind.id in keyGuideShown) return
        keyGuideShown = keyGuideShown + kind.id
        keyGuide = kind
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
            title = "FSS_Helper",
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

private val NUM_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight)

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
    val current = g.nav.current
    LaunchedEffect(current) { g.maybeKeyGuide(current) }
    when (current) {
        Dest.DASHBOARD -> DashboardScreen(
            g.tasks, g.dart, g.settings, g.law.cache,
            DashboardActions(
                searchLaw = g::searchLaw,
                openTask = { id ->
                    val t = g.tasks.data.value.tasks.firstOrNull { it.id == id }
                    if (t != null && t.kind == io.github.hdlee73.fsssupporter.tasks.TaskKind.INSPECTION) g.openCase(t.id)
                    else { g.tasksUi.tab = 0; g.tasksUi.filter = "open"; g.tasksUi.selectedId = id; g.nav.go(Dest.SCHEDULE) }
                },
                newTask = {
                    val t = WorkTask(title = "새 할 일", kind = io.github.hdlee73.fsssupporter.tasks.TaskKind.ETC, status = TaskStatus.IN_PROGRESS, start = LocalDate.now().toString())
                    g.tasks.upsert(t); g.tasksUi.tab = 0; g.tasksUi.selectedId = t.id; g.nav.go(Dest.SCHEDULE)
                },
                openCalendar = { g.tasksUi.tab = 1; g.nav.go(Dest.SCHEDULE) },
                openCorp = { code -> g.dart.saved.value.watch.firstOrNull { it.corp == code }?.let { g.dart.open(it) }; g.nav.go(Dest.DART) },
                openDart = { g.nav.go(Dest.DART) },
                newCase = {
                    val t = WorkTask(title = "새 검사건", kind = io.github.hdlee73.fsssupporter.tasks.TaskKind.INSPECTION, status = TaskStatus.PLANNED, start = LocalDate.now().plusDays(14).toString())
                    g.tasks.upsert(t); g.openCase(t.id)
                },
                openVerify = { g.nav.go(Dest.VERIFY) },
                openSettings = { g.nav.go(Dest.SETTINGS) },
            ),
            inspectionStep = { id -> g.inspection.of(id).current()?.title },
        )
        Dest.CASES -> CaseScreen(
            g.tasks, g.inspection, g.findings, g.sanction, g.caseUi,
            CaseActions(
                writeForm = g::writeForm,
                openLaw = g::searchLaw,
                verifyFile = { f, t -> g.verify.load(f, t.title); g.nav.go(Dest.VERIFY) },
                deleteCase = g::deleteCase,
                openGuide = { g.guideUi.tab = 0; g.nav.go(Dest.GUIDE) },
                message = g::message,
            ),
        )
        Dest.SCHEDULE -> TasksScreen(g.tasks, g.tasksUi, onOpenLaw = g::searchLaw)
        Dest.VERIFY -> VerifyScreen(g.verify, g.lawSearch.isSample, onOpenLaw = g::searchLaw, onMessage = g::message)
        Dest.GUIDE -> GuideScreen(g.inspection, g.guideUi, onWriteForm = g::writeForm, onViewOriginal = g::viewForm, onMessage = g::message)
        Dest.LAW -> LawScreen(g.lawSearch, g.settings, g.law.cache, onMessage = g::message, onKeyGuide = { g.keyGuide = KeyKind.LAW })
        Dest.DART -> DartScreen(g.dart, onOpenSettings = { g.keyGuide = KeyKind.DART }, onMessage = g::message)
        Dest.SANCTION -> SanctionScreen(g.sanction, g.sanctionUi, onOpenLaw = g::searchLaw, onMessage = g::message)
        Dest.SETTINGS -> SettingsScreen(g.settings, g.law, g.dart, g.prefetch, g.scope, onMessage = g::message)
        Dest.ABOUT -> AboutScreen(g.update, onInstallAndExit = { exitProcess(0) }, onMessage = g::message)
    }
    g.keyGuide?.let { kind ->
        when (kind) {
            KeyKind.LAW -> KeyGuideDialog(kind, g.settings, g.settings.lawOc,
                onSave = { g.settings.setLawOc(it); g.message("법령 OC를 저장했습니다.") }, onTest = { testLaw(g.law) }, onDismiss = { g.keyGuide = null })
            KeyKind.DART -> KeyGuideDialog(kind, g.settings, g.settings.dartKey,
                onSave = { g.settings.setDartKey(it); g.dart.ensureIndex(force = true); g.message("DART 인증키를 저장했습니다. 회사 목록을 받아 옵니다.") }, onTest = { testDart(g.dart) }, onDismiss = { g.keyGuide = null })
        }
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
                    Text("FSS_Helper", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("검사건별 검사업무 도우미", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            IconButton(onClick = { g.settings.update { it.copy(sidebarCollapsed = !collapsed) } }, modifier = if (collapsed) Modifier.fillMaxWidth() else Modifier) {
                Icon(if (collapsed) Icons.Outlined.Menu else Icons.Outlined.MenuOpen, if (collapsed) "메뉴 펼치기 (Ctrl+B)" else "메뉴 접기 (Ctrl+B)")
            }
        }
        Spacer(Modifier.height(8.dp))
        Dest.entries.filter { !it.lower }.forEachIndexed { i, d ->
            if (d.group == NavGroup.AUX && Dest.entries.indexOf(d) == Dest.entries.indexOfFirst { it.group == NavGroup.AUX }) {
                if (collapsed) androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                else Text(NavGroup.AUX.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp))
            }
            val badge = when (d) {
                Dest.CASES, Dest.SCHEDULE -> overdue.takeIf { it > 0 && d == Dest.SCHEDULE }?.toString()
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
