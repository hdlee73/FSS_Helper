package io.github.hdlee73.fsssupporter

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.HorizontalDivider
import io.github.hdlee73.fsssupporter.data.DocApps
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.hdlee73.fsssupporter.criteria.CriteriaEnv
import io.github.hdlee73.fsssupporter.criteria.CriteriaStore
import io.github.hdlee73.fsssupporter.criteria.FineScreen
import io.github.hdlee73.fsssupporter.criteria.LevelScreen
import io.github.hdlee73.fsssupporter.criteria.PenaltyScreen
import io.github.hdlee73.fsssupporter.criteria.RegulationModel
import io.github.hdlee73.fsssupporter.dart.DartCorp
import io.github.hdlee73.fsssupporter.dart.DartModel
import io.github.hdlee73.fsssupporter.dart.DartScreen
import io.github.hdlee73.fsssupporter.data.Hancom
import io.github.hdlee73.fsssupporter.data.Recent
import io.github.hdlee73.fsssupporter.data.RecentKind
import io.github.hdlee73.fsssupporter.data.RecentStore
import io.github.hdlee73.fsssupporter.data.SettingsRepo
import io.github.hdlee73.fsssupporter.home.AboutScreen
import io.github.hdlee73.fsssupporter.home.HomeBadges
import io.github.hdlee73.fsssupporter.home.HomeScreen
import io.github.hdlee73.fsssupporter.home.KeyGuideDialog
import io.github.hdlee73.fsssupporter.home.KeyKind
import io.github.hdlee73.fsssupporter.home.PrefetchUi
import io.github.hdlee73.fsssupporter.home.SettingsScreen
import io.github.hdlee73.fsssupporter.home.testDart
import io.github.hdlee73.fsssupporter.home.testLaw
import io.github.hdlee73.fsssupporter.law.LawModule
import io.github.hdlee73.fsssupporter.law.LawScreen
import io.github.hdlee73.fsssupporter.law.LawSearchModel
import io.github.hdlee73.fsssupporter.law.LawUiState
import io.github.hdlee73.fsssupporter.library.DocFiles
import io.github.hdlee73.fsssupporter.library.FormCatalog
import io.github.hdlee73.fsssupporter.library.FormsScreen
import io.github.hdlee73.fsssupporter.library.LibraryActions
import io.github.hdlee73.fsssupporter.library.LibraryStore
import io.github.hdlee73.fsssupporter.library.LibraryUi
import io.github.hdlee73.fsssupporter.library.ManualScreen
import io.github.hdlee73.fsssupporter.todo.TodoScreen
import io.github.hdlee73.fsssupporter.todo.TodoStore
import io.github.hdlee73.fsssupporter.ui.AppTheme
import io.github.hdlee73.fsssupporter.ui.Dest
import io.github.hdlee73.fsssupporter.ui.NavGroup
import io.github.hdlee73.fsssupporter.ui.Navigator
import io.github.hdlee73.fsssupporter.ui.Os
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
    val library = LibraryStore()
    val libraryUi = LibraryUi()
    val criteria = CriteriaStore()
    val regulation = RegulationModel(law.repository, scope)
    val todo = TodoStore()
    val recents = RecentStore()
    val update = UpdateModel(settings, scope)
    val prefetch = PrefetchUi()
    val messages = Channel<String>(Channel.BUFFERED)

    /** 이번 실행에서 이미 띄운 인증키 안내(법령·DART) */
    var keyGuideShown by mutableStateOf(setOf<String>())
    /** 지금 띄울 인증키 안내 */
    var keyGuide by mutableStateOf<KeyKind?>(null)

    fun message(text: String) { messages.trySend(text) }

    val criteriaEnv = CriteriaEnv(criteria, regulation, openLaw = { q -> searchLaw(q) }, message = { message(it) })

    val libraryActions = LibraryActions(
        importFile = this::importFile,
        writeForm = this::writeForm,
        openedManual = { title, page -> recents.touch(RecentKind.MANUAL, title, page.toString(), "검사품질관리 매뉴얼 ${page + 1}쪽") },
        openedForm = { no -> FormCatalog.form(no).let { f -> recents.touch(RecentKind.FORM, f.label.trim(), no, "검사관련 서식") } },
        message = this::message,
    )

    init {
        Os.hwpApp = { settings.state.value.let { it.hwpApp to it.ieumPath } }
        Os.onNote = { message(it) }
        // 한글·이음오피스 위치는 처음 한 번 찾아 둔다(레지스트리 조회라 화면을 멈추지 않게).
        scope.launch(Dispatchers.IO) { DocApps.hancom() }
        nav.registerInnerBack(Dest.LAW) { lawSearch.back() }
        nav.registerInnerBack(Dest.DART) { dart.back() }
        nav.registerInnerBack(Dest.MANUAL) { libraryUi.backManual() }
        nav.registerInnerBack(Dest.FORMS) { libraryUi.backForms() }
        // 법령 조회·기업 열람을 최근 사용에 남긴다.
        scope.launch {
            lawSearch.state.collect { st ->
                if (st is LawUiState.Tree) recents.touch(RecentKind.LAW, st.tree.query.raw, st.tree.query.raw, st.tree.law.name)
            }
        }
        scope.launch {
            dart.selected.collect { c -> if (c != null) recents.touch(RecentKind.CORP, c.name, listOf(c.corp, c.name, c.stock, c.cls).joinToString("|"), "기업 개황") }
        }
    }

    fun searchLaw(q: String) {
        nav.go(Dest.LAW)
        lawSearch.onQueryChange(q)
        lawSearch.submit(q)
    }

    /** 매뉴얼·서식 파일 가져오기(한글이면 PDF 변환까지라 시간이 걸린다) */
    fun importFile(kind: String, file: File) {
        if (libraryUi.busy != null) return
        libraryUi.busy = if (file.extension.lowercase() == "pdf") "PDF를 읽는 중…" else "원본을 읽고 PDF를 만드는 중…(1~3분)"
        scope.launch {
            val r = runCatching { if (kind == "manual") library.importManual(file) else library.importForms(file) }
            libraryUi.busy = null
            r.onSuccess { message(it.message) }.onFailure { message(it.message ?: "가져오지 못했습니다.") }
        }
    }

    /**
     * 서식을 원본 그대로 새 한글 파일로 만들어 연다(문서\FSS_Helper\서식). 여는 프로그램은 설정(한글/이음오피스)을 따른다.
     * 한글이 있으면 한글 자동화로, 없으면 HWPX는 앱이 직접 그 서식 부분만 남기고, 안 되면 서식 전체 원본 사본을 연다.
     */
    fun writeForm(no: String) {
        scope.launch {
            val lib = library.data.value
            val form = FormCatalog.form(no)
            val src = File(lib.formsSrc)
            if (lib.formsSrc.isBlank() || !src.isFile) { message("서식 원본(HWP·HWPX)이 없습니다. '가져오기'로 서식 한글 파일을 넣어 주세요."); return@launch }
            val dir = DocFiles.formsDir
            val name = DocFiles.safe("제${no}호 ${form.name}".trim())
            message("'${form.label.trim()}' 서식 파일을 만드는 중입니다…")
            val dest = when (val r = library.formOriginal(no, lib)) {
                is Hancom.Result.Ok -> withContext(Dispatchers.IO) { DocFiles.unique(dir, "$name.${r.file.extension}").also { r.file.copyTo(it) } }
                    .also { message("'${it.name}'을(를) 문서\\FSS_Helper\\서식 폴더에 만들어 열었습니다.") }
                is Hancom.Result.Failed -> withContext(Dispatchers.IO) { DocFiles.unique(dir, "$name (서식 전체본).${src.extension}").also { src.copyTo(it) } }
                    .also { message(r.message + " 대신 서식 전체 원본의 사본을 열었습니다. Ctrl+F로 '제${no}호'를 찾아 쓰세요.") }
            }
            Os.open(dest)
            recents.touch(RecentKind.FILE, dest.name, dest.absolutePath, "문서\\FSS_Helper\\서식")
        }
    }

    /** 홈 화면 최근 목록에서 다시 열기 */
    fun openRecent(r: Recent) {
        when (r.type) {
            RecentKind.FORM -> { libraryUi.formNo = r.ref; nav.go(Dest.FORMS) }
            RecentKind.MANUAL -> { libraryUi.manualJump = r.ref.toIntOrNull(); nav.go(Dest.MANUAL) }
            RecentKind.LAW -> searchLaw(r.ref)
            RecentKind.CORP -> {
                val p = r.ref.split('|')
                dart.open(DartCorp(p[0], p.getOrElse(1) { r.title }, p.getOrElse(2) { "" }, p.getOrElse(3) { "" }))
                nav.go(Dest.DART)
            }
            RecentKind.FILE -> {
                val f = File(r.ref)
                if (f.isFile) Os.open(f) else { message("파일이 없습니다(옮기거나 지웠을 수 있습니다)."); recents.remove(r.key) }
            }
        }
    }

    /** 키 없이 법률 검색·기업 개황·기준 원문을 처음 열면 발급 안내를 띄운다(다시 보지 않기 전까지 실행마다 한 번). */
    fun maybeKeyGuide(dest: Dest) {
        val kind = when (dest) {
            Dest.LAW, Dest.LEVEL, Dest.PENALTY, Dest.FINE -> KeyKind.LAW.takeIf { settings.lawOc.isBlank() }
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
        e.isCtrlPressed && e.key == Key.K -> { g.nav.go(Dest.LAW); true }
        e.isCtrlPressed && e.key in NUM_KEYS -> {
            Dest.entries.filter { !it.lower }.getOrNull(NUM_KEYS.indexOf(e.key))?.let { g.nav.go(it) }; true
        }
        else -> false
    }
}

private val NUM_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Shell(g: AppGraph) {
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
        Column(Modifier.fillMaxSize()) {
            TopBar(g)
            Box(Modifier.weight(1f).fillMaxWidth()) { Content(g) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Snackbar(it) }
    }
}

/**
 * 창 맨 위 한 줄: 뒤로 · 앱 이름 · 모든 메뉴 탭 · 설정/앱 정보.
 * 탭은 글자만 두고 고른 탭만 색과 밑줄로 표시해 차분하게 보이게 한다. 창이 좁으면 짧은 이름으로 바꾸고 옆으로 밀어 볼 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(g: AppGraph) {
    val update by g.update.state.collectAsState()
    val todo by g.todo.data.collectAsState()
    val saved by g.dart.saved.collectAsState()
    val hasUpdate = update is UpdateState.Available || update is UpdateState.Ready
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 6.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { g.nav.back() }, enabled = g.nav.canGoBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "뒤로 (Alt+←)", Modifier.size(18.dp))
            }
            Image(painterResource("icons/app.png"), null, Modifier.size(22.dp).clip(RoundedCornerShape(5.dp)))
            Text("FSS_Helper", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, end = 18.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                val compact = maxWidth < 1040.dp
                Row(Modifier.fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    var last: NavGroup? = null
                    Dest.entries.filter { !it.lower }.forEachIndexed { i, d ->
                        if (last != null && d.group != last) VerticalDivider(Modifier.height(14.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        last = d.group
                        val count = when (d) {
                            Dest.TODO -> todo.items.count { !it.done }.takeIf { it > 0 }
                            Dest.DART -> saved.newCount.values.sum().takeIf { it > 0 }
                            else -> null
                        }
                        TopTab(if (compact) d.short else d.label, g.nav.current == d, count, "${d.label} (Ctrl+${i + 1})") { g.nav.go(d) }
                    }
                }
            }
            if (hasUpdate) TextButton(onClick = { g.nav.go(Dest.ABOUT) }) { Text("새 버전") }
            Dest.entries.filter { it.lower }.forEach { d ->
                val on = g.nav.current == d
                TooltipBox(TooltipDefaults.rememberPlainTooltipPositionProvider(), { PlainTooltip { Text(d.label) } }, rememberTooltipState()) {
                    IconButton(onClick = { g.nav.go(d) }) {
                        Icon(d.icon, d.label, Modifier.size(20.dp), tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopTab(label: String, selected: Boolean, count: Int?, tip: String, onClick: () -> Unit) {
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    TooltipBox(TooltipDefaults.rememberPlainTooltipPositionProvider(), { PlainTooltip { Text(tip) } }, rememberTooltipState()) {
        Column(
            Modifier.fillMaxHeight().width(IntrinsicSize.Max).clickable(onClick = onClick).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = fg, maxLines = 1)
                if (count != null) Text(" $count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent))
        }
    }
}

@Composable
private fun Content(g: AppGraph) {
    val current = g.nav.current
    LaunchedEffect(current) { g.maybeKeyGuide(current) }
    when (current) {
        Dest.HOME -> {
            val todo by g.todo.data.collectAsState()
            val saved by g.dart.saved.collectAsState()
            // 카드에는 할 일·새 공시처럼 '지금 볼 것'만 짧게 표시한다.
            val badges = HomeBadges(buildMap {
                todo.items.count { !it.done }.takeIf { it > 0 }?.let { put(Dest.TODO, "남은 일 ${it}개") }
                saved.newCount.values.sum().takeIf { it > 0 }?.let { put(Dest.DART, "새 공시 ${it}건") }
            })
            HomeScreen(g.recents, badges, onTool = { g.nav.go(it) }, onRecent = g::openRecent)
        }
        Dest.MANUAL -> ManualScreen(g.library, g.libraryUi, g.libraryActions)
        Dest.FORMS -> FormsScreen(g.library, g.libraryUi, g.libraryActions)
        Dest.LAW -> LawScreen(g.lawSearch, g.settings, g.law.cache, onMessage = g::message, onKeyGuide = { g.keyGuide = KeyKind.LAW })
        Dest.DART -> DartScreen(g.dart, onOpenSettings = { g.keyGuide = KeyKind.DART }, onMessage = g::message)
        Dest.LEVEL -> LevelScreen(g.criteriaEnv)
        Dest.PENALTY -> PenaltyScreen(g.criteriaEnv)
        Dest.FINE -> FineScreen(g.criteriaEnv)
        Dest.TODO -> TodoScreen(g.todo)
        Dest.SETTINGS -> SettingsScreen(g.settings, g.law, g.dart, g.prefetch, g.scope, onMessage = g::message)
        Dest.ABOUT -> AboutScreen(g.update, onInstallAndExit = { exitProcess(0) }, onMessage = g::message)
    }
    g.keyGuide?.let { kind ->
        when (kind) {
            KeyKind.LAW -> KeyGuideDialog(kind, g.settings, g.settings.lawOc,
                onSave = { g.settings.setLawOc(it); g.regulation.ensure(force = true); g.message("법령 OC를 저장했습니다.") }, onTest = { testLaw(g.law) }, onDismiss = { g.keyGuide = null })
            KeyKind.DART -> KeyGuideDialog(kind, g.settings, g.settings.dartKey,
                onSave = { g.settings.setDartKey(it); g.dart.ensureIndex(force = true); g.message("DART 인증키를 저장했습니다. 회사 목록을 받아 옵니다.") }, onTest = { testDart(g.dart) }, onDismiss = { g.keyGuide = null })
        }
    }
}
