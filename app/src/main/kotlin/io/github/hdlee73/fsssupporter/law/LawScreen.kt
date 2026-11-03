package io.github.hdlee73.fsssupporter.law

import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.hdlee73.everylaw.core.domain.HwpxExporter
import com.hdlee73.everylaw.core.domain.HwpxOptions
import com.hdlee73.everylaw.core.domain.TreeFilter
import com.hdlee73.everylaw.core.domain.withParagraphPieces
import com.hdlee73.everylaw.core.model.Article
import com.hdlee73.everylaw.core.model.DelegationTree
import com.hdlee73.everylaw.core.model.LawLevel
import com.hdlee73.everylaw.core.model.LawNode
import com.hdlee73.everylaw.core.model.formatYmd
import io.github.hdlee73.fsssupporter.BuildInfo
import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.SettingsRepo
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.EmptyState
import io.github.hdlee73.fsssupporter.ui.ErrorBox
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.SearchField
import io.github.hdlee73.fsssupporter.ui.Tone
import io.github.hdlee73.fsssupporter.ui.parseYmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 조문 인용 표기: 「법령명」 제N조 */
fun citationOf(sourceName: String, article: Article): String =
    "「$sourceName」 " + (article.number?.display ?: article.title)

private val EXAMPLES = listOf("자본시장법 제54조", "금소법 제19조", "지배구조법 제24조", "은행법 제34조", "내부통제", "설명의무")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LawScreen(
    model: LawSearchModel,
    settings: SettingsRepo,
    cache: LawCache,
    onInsertToOpinion: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val query by model.query.collectAsState()
    val asOf by model.asOf.collectAsState()
    val state by model.state.collectAsState()
    val offline by cache.offline.collectAsState()
    val app by settings.state.collectAsState()
    var asOfText by remember(asOf) { mutableStateOf(asOf?.let { formatYmd(it) }.orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "스마트 법령검색",
            subtitle = when {
                model.isSample -> "데모 데이터로 동작 중입니다. 설정에서 국가법령정보 OC를 입력하면 실제 법령을 조회합니다."
                offline -> "www.law.go.kr 연결이 안 돼 저장해 둔 자료를 보여 주고 있습니다."
                else -> "법령명 + 조문은 위임 트리(법률→시행령→시행규칙→감독규정→세칙)로, 단어만 넣으면 키워드 통합 검색으로 찾습니다."
            },
        ) {
            if (offline) Pill("오프라인 캐시", Tone.WARN)
            if (model.isSample) Pill("데모", Tone.WARN)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(query, model::onQueryChange, { model.submit() }, "예: 자본시장법 제54조, 금소법 19조 2항, 내부통제", Modifier.weight(1f))
            OutlinedTextField(
                value = asOfText,
                onValueChange = { asOfText = it },
                modifier = Modifier.width(170.dp),
                singleLine = true,
                label = { Text("기준일 (비우면 현행)", style = MaterialTheme.typography.labelSmall) },
                textStyle = MaterialTheme.typography.bodyMedium,
                isError = asOfText.isNotBlank() && parseYmd(asOfText) == null,
            )
            Button(onClick = {
                val ymd = asOfText.takeIf { it.isNotBlank() }?.let(::parseYmd)
                if (ymd != asOf) model.setAsOf(ymd)
                model.submit()
            }) { Text("검색") }
        }
        Spacer(Modifier.height(10.dp))

        when (val s = state) {
            LawUiState.Idle -> IdleView(app.recentLaw, app.favoriteLaw, onPick = { model.submit(it) }, onClearRecent = settings::clearRecentLaw)
            is LawUiState.Loading -> Column(Modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(10.dp))
                Text(s.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is LawUiState.Error -> Column(Modifier.padding(horizontal = 20.dp)) {
                ErrorBox(s.message) { if (s.canRetry) TextButton(onClick = model::retry) { Text("다시 시도") } }
            }
            is LawUiState.Tree -> TreeView(model, s, app.favoriteLaw, settings, onInsertToOpinion, onMessage)
            is LawUiState.Keyword -> KeywordView(model, s, onInsertToOpinion, onMessage)
        }
    }

    val versions by model.versions.collectAsState()
    VersionsDialog(versions, onClose = model::closeVersions, onPick = { v ->
        model.closeVersions()
        model.setAsOf(v.effectiveDate)
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleView(recent: List<String>, favorites: List<String>, onPick: (String) -> Unit, onClearRecent: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("예시로 찾아보기")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { EXAMPLES.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }) } }
        }
        if (favorites.isNotEmpty()) AppCard(Modifier.fillMaxWidth()) {
            CardTitle("즐겨찾기")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { favorites.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }, leadingIcon = { Icon(Icons.Outlined.Star, null, Modifier.size(14.dp)) }) } }
        }
        if (recent.isNotEmpty()) AppCard(Modifier.fillMaxWidth()) {
            CardTitle("최근 검색") { TextButton(onClick = onClearRecent) { Text("지우기") } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { recent.forEach { AssistChip(onClick = { onPick(it) }, label = { Text(it) }) } }
        }
        AppCard(Modifier.fillMaxWidth()) {
            CardTitle("검색 방법")
            Text(
                "• 법령명(약칭 가능) + 조문: 자본시장법 제54조, 금소법 19조 2항, 은행법 시행령 제20조의2\n" +
                    "• 키워드: 내부통제, 설명의무 → 조문 제목·본문, 법령, 행정규칙, 판례를 한꺼번에 찾습니다.\n" +
                    "• 법령명 + 키워드: 자본시장법 내부자 → 그 법령 안에서만 찾습니다.\n" +
                    "• 기준일을 넣으면 위반행위 당시 시행 중이던 조문으로 조회합니다(폐지된 구법령 포함).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 트리를 화면에 펼친 한 줄 */
private data class Row1(val node: LawNode, val depth: Int, val hasChildren: Boolean, val open: Boolean)

private fun visibleRows(root: LawNode, expanded: Set<String>): List<Row1> {
    val out = mutableListOf<Row1>()
    fun walk(n: LawNode, d: Int) {
        val open = n.id in expanded
        out += Row1(n, d, n.children.isNotEmpty(), open)
        if (open) n.children.forEach { walk(it, d + 1) }
    }
    walk(root, 0)
    return out
}

private fun levelTone(level: LawLevel): Tone = when (level) {
    LawLevel.LAW -> Tone.ACCENT
    LawLevel.DECREE, LawLevel.RULE -> Tone.OK
    else -> Tone.NEUTRAL
}

@Composable
private fun TreeView(
    model: LawSearchModel,
    s: LawUiState.Tree,
    favorites: List<String>,
    settings: SettingsRepo,
    onInsertToOpinion: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val filter by model.filter.collectAsState()
    val inline by model.inline.collectAsState()
    val detail by model.detail.collectAsState()
    val scope = rememberCoroutineScope()
    val tree = s.tree
    val baseRoot = if (inline) tree.root.withParagraphPieces() else tree.root
    val root = if (filter.isBlank()) baseRoot else TreeFilter.filter(baseRoot, filter)
    val expanded = if (filter.isBlank()) s.expanded else root.flatten().map { it.id }.toSet()
    val rows = visibleRows(root, expanded)
    val cited = tree.cited.flatMap { visibleRows(it, expanded) }
    val listState = rememberLazyListState()

    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 왼쪽: 위임 트리
        Column(Modifier.weight(0.46f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(tree.law.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (tree.version?.let { "${it.effectiveDateLabel} 시행 버전" } ?: "현행") + " · 하위 규정 ${tree.root.descendantCount}건",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val fav = tree.query.raw in favorites
                IconButton(onClick = { settings.toggleFavoriteLaw(tree.query.raw) }) {
                    Icon(if (fav) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (fav) "즐겨찾기 해제" else "즐겨찾기")
                }
                IconButton(onClick = model::openVersions) { Icon(Icons.Outlined.History, "시행 연혁") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SearchField(filter, model::setFilter, {}, "결과 안에서 찾기 (단어 또는 제12조)", Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { model.expandAll(true) }) { Text("모두 펼치기") }
                TextButton(onClick = { model.expandAll(false) }) { Text("접기") }
                Spacer(Modifier.weight(1f))
                Text("항별로 나누기", style = MaterialTheme.typography.labelSmall)
                Switch(checked = inline, onCheckedChange = model::setInline, modifier = Modifier.padding(start = 4.dp))
            }
            tree.warnings.forEach { InfoBox(it) }
            Box(Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                    items(rows, key = { "t:" + it.node.id }) { r -> NodeRow(r, r.node.id == s.selectedId, model) }
                    if (cited.isNotEmpty()) {
                        item { SectionLabel("이 조문을 인용한 하위 법규 (위임 아님)") }
                        items(cited, key = { "c:" + it.node.id }) { r -> NodeRow(r, r.node.id == s.selectedId, model) }
                    }
                    if (tree.precedents.isNotEmpty()) {
                        item { SectionLabel("관련 판례 ${tree.precedents.size}건" + tree.precedentBasis.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()) }
                        items(tree.precedents, key = { "p:" + it.serialNo }) { p ->
                            ListLine(p.caseName, listOfNotNull(p.court, p.caseNumber, p.decisionDate?.let(::formatYmd), p.matchNote).joinToString(" · ")) { model.openPrecedent(p) }
                        }
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val file = runCatching { exportHwpx(tree) }.getOrElse { onMessage("한글 파일을 만들지 못했어요: ${it.message}"); null }
                        if (file != null) { onMessage("한글 파일로 저장했어요: ${file.name}"); Os.open(file.parentFile) }
                    }
                }) { Text("한글(HWPX)로 저장") }
                OutlinedButton(onClick = { Os.copy(treeAsText(tree)); onMessage("트리 전체를 복사했어요.") }) { Text("트리 복사") }
            }
        }
        // ── 오른쪽: 상세
        Box(Modifier.weight(0.54f).fillMaxHeight()) {
            when (val d = detail) {
                LawDetail.None -> {
                    val node = tree.findNode(s.selectedId) ?: tree.root
                    NodeDetail(node, onInsertToOpinion, onMessage, onSearch = { q -> model.submit(q) })
                }
                else -> DetailView(d, onClose = model::closeDetail, onInsertToOpinion, onMessage)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
private fun NodeRow(r: Row1, selected: Boolean, model: LawSearchModel) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
    Row(
        Modifier.fillMaxWidth().background(bg, MaterialTheme.shapes.small).clickable { model.select(r.node.id) }
            .padding(start = (4 + r.depth * 18).dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (r.hasChildren) {
            Icon(
                if (r.open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                if (r.open) "접기" else "펼치기",
                Modifier.size(18.dp).clickable { model.toggle(r.node.id) },
            )
        } else Spacer(Modifier.width(18.dp))
        Pill(r.node.level.shortLabel, levelTone(r.node.level))
        Column(Modifier.weight(1f)) {
            Text(r.node.displayHeading.ifBlank { r.node.article.fullTitle }, style = MaterialTheme.typography.bodyMedium, fontWeight = if (r.depth == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.node.sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ListLine(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeDetail(node: LawNode, onInsertToOpinion: (String) -> Unit, onMessage: (String) -> Unit, onSearch: (String) -> Unit) {
    val cite = citationOf(node.sourceName, node.article)
    AppCard(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(node.level.label, levelTone(node.level))
            node.issuer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(node.sourceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(listOfNotNull(node.article.fullTitle, node.part).joinToString(" "), style = MaterialTheme.typography.titleMedium)
        if (node.delegatedBy.isNotEmpty()) {
            Text("위임한 상위 조문: " + node.delegatedBy.joinToString(", ") { it.heading }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { Os.copy(cite); onMessage("인용 표기를 복사했어요: $cite") }) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("인용 복사")
            }
            OutlinedButton(onClick = { Os.copy(cite + "\n" + node.article.body); onMessage("조문 원문을 복사했어요.") }) { Text("원문 복사") }
            OutlinedButton(onClick = { onInsertToOpinion(cite + "\n" + node.article.body); onMessage("의견서 도우미에 넣었어요.") }) { Text("의견서에 넣기") }
            if (node.level != LawLevel.LAW && node.article.number != null && node.level.ordinal <= LawLevel.RULE.ordinal) {
                OutlinedButton(onClick = { onSearch(node.sourceName + " " + node.article.number!!.display) }) { Text("이 조문으로 검색") }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
        val sc = rememberScrollState()
        Box(Modifier.weight(1f)) {
            SelectionContainer {
                Text(node.article.body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().verticalScroll(sc).padding(end = 10.dp))
            }
            VerticalScrollbar(rememberScrollbarAdapter(sc), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
fun DetailView(d: LawDetail, onClose: () -> Unit, onInsertToOpinion: (String) -> Unit, onMessage: (String) -> Unit) {
    AppCard(Modifier.fillMaxSize()) {
        when (d) {
            LawDetail.None -> Unit
            is LawDetail.Loading -> {
                CardTitle(d.title) { TextButton(onClick = onClose) { Text("닫기") } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("불러오는 중", style = MaterialTheme.typography.bodySmall)
                }
            }
            is LawDetail.Failed -> {
                CardTitle("불러오지 못했어요") { TextButton(onClick = onClose) { Text("닫기") } }
                ErrorBox(d.message)
            }
            is LawDetail.Document -> DocumentView(d, onClose, onInsertToOpinion, onMessage)
            is LawDetail.Precedent -> {
                val p = d.detail
                CardTitle(p.summary.caseName) { TextButton(onClick = onClose) { Text("닫기") } }
                Text(listOfNotNull(p.summary.court, p.summary.caseNumber, p.summary.decisionDate?.let(::formatYmd)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { Os.copy("${p.summary.court.orEmpty()} ${p.summary.decisionDate?.let(::formatYmd).orEmpty()} 선고 ${p.summary.caseNumber.orEmpty()} 판결".trim()); onMessage("판례 인용 표기를 복사했어요.") }) { Text("판례 인용 복사") }
                val sc = rememberScrollState()
                Box(Modifier.weight(1f)) {
                    SelectionContainer {
                        Column(Modifier.verticalScroll(sc).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Block("판시사항", p.holdings); Block("판결요지", p.gist); Block("참조조문", p.referencedArticles); Block("참조판례", p.referencedCases); Block("전문", p.fullText)
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(sc), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    if (body.isBlank()) return
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DocumentView(d: LawDetail.Document, onClose: () -> Unit, onInsertToOpinion: (String) -> Unit, onMessage: (String) -> Unit) {
    var find by remember(d.title) { mutableStateOf(d.focus.orEmpty()) }
    val shown = if (find.isBlank()) d.articles else d.articles.filter {
        val no = TreeFilter.articleNo(find)
        if (no != null) it.number?.let { n -> n.main == no.main && n.branch == no.branch } == true
        else it.fullTitle.contains(find) || it.content.contains(find)
    }
    CardTitle(d.title) { TextButton(onClick = onClose) { Text("닫기") } }
    if (d.subtitle.isNotBlank()) Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SearchField(find, { find = it }, {}, "조문 찾기 (단어 또는 제12조) · 전체 ${d.articles.size}개 조", Modifier.fillMaxWidth())
    val ls = rememberLazyListState()
    Box(Modifier.weight(1f)) {
        LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(shown, key = { (it.number?.display ?: "") + "|" + it.title + "|" + it.content.hashCode() }) { a ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.fullTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { val c = citationOf(d.title, a); Os.copy(c); onMessage("복사했어요: $c") }) { Text("인용") }
                        TextButton(onClick = { onInsertToOpinion(citationOf(d.title, a) + "\n" + a.body); onMessage("의견서 도우미에 넣었어요.") }) { Text("의견서로") }
                    }
                    SelectionContainer { Text(a.body, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun KeywordView(model: LawSearchModel, s: LawUiState.Keyword, onInsertToOpinion: (String) -> Unit, onMessage: (String) -> Unit) {
    val detail by model.detail.collectAsState()
    val r = s.result
    val ls = rememberLazyListState()
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(0.46f).fillMaxHeight()) {
            LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                item {
                    Text("'${r.keyword}' 검색 결과" + (r.scopeLaw?.let { " · ${it.name} 안에서" } ?: ""), style = MaterialTheme.typography.titleSmall)
                    if (r.scanNote.isNotBlank()) Text(r.scanNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.warnings.forEach { Spacer(Modifier.height(4.dp)); InfoBox(it) }
                }
                if (r.articleHits.isEmpty() && r.laws.isEmpty() && r.adminRules.isEmpty() && r.precedents.isEmpty()) {
                    item { EmptyState(Icons.Outlined.ManageSearch, "찾은 결과가 없어요", "다른 단어로 찾거나, 법령명과 조문 번호를 함께 넣어 보세요.") }
                }
                if (r.articleHits.isNotEmpty()) {
                    item { SectionLabel("조문 ${r.articleHits.size}건") }
                    items(r.articleHits, key = { "a:" + it.law.mst + it.article.heading }) { h ->
                        ListLine("${h.law.name} ${h.article.fullTitle}", h.snippet) {
                            val no = h.article.number
                            if (no != null) model.submit("${h.law.name} ${no.display}") else model.openLaw(h.law)
                        }
                    }
                }
                if (r.laws.isNotEmpty()) {
                    item { SectionLabel("법령 ${r.laws.size}건") }
                    items(r.laws, key = { "l:" + it.mst }) { l -> ListLine(l.name, listOfNotNull(l.kind, l.effectiveDate?.let { "시행 " + formatYmd(it) }).joinToString(" · ")) { model.openLaw(l, r.keyword) } }
                }
                if (r.adminRules.isNotEmpty()) {
                    item { SectionLabel("행정규칙 ${r.adminRules.size}건") }
                    items(r.adminRules, key = { "r:" + it.serialNo }) { a -> ListLine(a.name, listOfNotNull(a.kind, a.agency).joinToString(" · ")) { model.openAdminRule(a) } }
                }
                if (r.precedents.isNotEmpty()) {
                    item { SectionLabel("판례 ${r.precedents.size}건") }
                    items(r.precedents, key = { "p:" + it.serialNo }) { p -> ListLine(p.caseName, listOfNotNull(p.court, p.caseNumber, p.decisionDate?.let(::formatYmd)).joinToString(" · ")) { model.openPrecedent(p) } }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
        Box(Modifier.weight(0.54f).fillMaxHeight()) {
            if (detail == LawDetail.None) AppCard(Modifier.fillMaxSize()) {
                EmptyState(Icons.Outlined.ManageSearch, "왼쪽에서 항목을 고르세요", "조문을 누르면 위임 트리로, 법령·행정규칙·판례를 누르면 여기에서 본문을 보여 줍니다.")
            } else DetailView(detail, model::closeDetail, onInsertToOpinion, onMessage)
        }
    }
}

@Composable
private fun VersionsDialog(state: VersionsState, onClose: () -> Unit, onPick: (com.hdlee73.everylaw.core.model.LawVersion) -> Unit) {
    if (state == VersionsState.Hidden) return
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("닫기") } },
        title = { Text(if (state is VersionsState.Shown) "${state.lawName} 시행 연혁" else "시행 연혁") },
        text = {
            when (state) {
                VersionsState.Loading -> CircularProgressIndicator(Modifier.size(24.dp))
                is VersionsState.Failed -> Text(state.message)
                is VersionsState.Shown -> LazyColumn(Modifier.height(420.dp).width(520.dp)) {
                    items(state.versions, key = { it.mst + it.effectiveDate }) { v ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(v) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(v.effectiveDateLabel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                            Text(listOfNotNull(v.revisionType, v.promulgationDate?.let { "공포 " + formatYmd(it) }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            v.status?.let { Pill(it, if (it.contains("현행")) Tone.OK else if (it.contains("예정")) Tone.WARN else Tone.NEUTRAL) }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                VersionsState.Hidden -> Unit
            }
        },
    )
}

private fun treeAsText(tree: DelegationTree): String = buildString {
    fun walk(n: LawNode, d: Int) {
        append("  ".repeat(d)).append("[").append(n.level.shortLabel).append("] ").append(n.sourceName).append(" ").append(n.displayHeading).append('\n')
        n.article.body.lines().forEach { append("  ".repeat(d + 1)).append(it).append('\n') }
        n.children.forEach { walk(it, d + 1) }
    }
    walk(tree.root, 0)
}

private suspend fun exportHwpx(tree: DelegationTree): File = withContext(Dispatchers.IO) {
    val safe = (tree.law.name + " " + tree.query.article.display).replace(Regex("""[\\/:*?"<>|]"""), "_")
    val file = File(AppDirs.exports, "$safe.hwpx")
    file.outputStream().use { HwpxExporter.write(tree, HwpxOptions(footer = "FSS_Supporter ${BuildInfo.VERSION} · 이현덕 with Claude", heading = "FSS_Supporter 위임 법령 조회 결과"), it) }
    file
}
