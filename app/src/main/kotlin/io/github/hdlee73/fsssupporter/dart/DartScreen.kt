package io.github.hdlee73.fsssupporter.dart

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.EmptyState
import io.github.hdlee73.fsssupporter.ui.ErrorBox
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.LabelValue
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.SearchField
import io.github.hdlee73.fsssupporter.ui.Tone
import io.github.hdlee73.fsssupporter.ui.ymdLabel
import java.text.DecimalFormat

private val EOK = DecimalFormat("#,##0.##")
private val INT = DecimalFormat("#,##0")
private fun eok(v: Double?) = v?.let { EOK.format(it) } ?: "-"

@Composable
fun DartScreen(model: DartModel, onOpenSettings: () -> Unit) {
    val selected by model.selected.collectAsState()
    LaunchedEffect(Unit) { model.ensureIndex() }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("기업 개황·공시", "금융감독원 전자공시(OpenDART)에서 기업개황, 최대주주, 재무, 공시 목록을 바로 조회합니다.")
        if (!model.hasKey) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                InfoBox("OpenDART 인증키가 필요합니다. opendart.fss.or.kr 에서 무료로 발급받아 설정에 입력해 주세요.")
                TextButton(onClick = onOpenSettings) { Text("설정 열기") }
            }
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SearchPane(model, Modifier.width(330.dp).fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val c = selected
                if (c == null) AppCard(Modifier.fillMaxSize()) {
                    EmptyState(Icons.Outlined.Business, "회사를 고르세요", "왼쪽에서 회사명이나 종목코드(6자리)로 찾거나 관심기업을 누르면 여기에서 보여 줍니다.")
                } else CompanyPane(model, c)
            }
        }
    }
}

@Composable
private fun SearchPane(model: DartModel, modifier: Modifier) {
    val q by model.query.collectAsState()
    val results by model.results.collectAsState()
    val saved by model.saved.collectAsState()
    val idx by model.indexState.collectAsState()
    val selected by model.selected.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchField(q, model::onQuery, model::search, "회사명 또는 종목코드", Modifier.fillMaxWidth())
        when (val s = idx) {
            Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Text("회사 목록 준비 중 (처음 한 번 1~2분)", style = MaterialTheme.typography.bodySmall)
            }
            is Load.Failed -> Text(s.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            is Load.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("회사 ${INT.format(s.value)}곳", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { model.ensureIndex(force = true) }) { Text("목록 새로 받기") }
            }
            Load.Idle -> Unit
        }
        val list = results
        val ls = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                if (list != null) {
                    item { Label(if (list.isEmpty()) "찾은 회사가 없어요" else "검색 결과 ${list.size}곳") }
                    items(list, key = { "r" + it.corp }) { CorpRow(it, it.corp == selected?.corp, model.isWatched(it.corp), null) { model.open(it) } }
                }
                item { Label("관심기업 ${saved.watch.size}곳") }
                if (saved.watch.isEmpty()) item {
                    Text("회사 화면의 별표를 누르면 여기에 모이고, 새 공시가 올라오면 대시보드에 알려 줍니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(6.dp))
                }
                items(saved.watch, key = { "w" + it.corp }) { CorpRow(it, it.corp == selected?.corp, true, saved.newCount[it.corp]) { model.open(it) } }
                if (list == null && saved.recent.isNotEmpty()) {
                    item { Label("최근 검색") }
                    items(saved.recent, key = { "q$it" }) { r ->
                        Text(r, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth().clickable { model.onQuery(r); model.search() }.padding(6.dp))
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 4.dp))
}

@Composable
private fun CorpRow(c: DartCorp, selected: Boolean, watched: Boolean, newCount: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background, MaterialTheme.shapes.small)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(c.stock.ifBlank { "비상장" }, if (c.cls.isNotBlank()) dartMarketLabel(c.cls) else "").filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (newCount != null && newCount > 0) Pill("새 공시 $newCount", Tone.WARN)
        if (watched) Icon(Icons.Outlined.Star, "관심기업", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun CompanyPane(model: DartModel, c: DartCorp) {
    val tab by model.tab.collectAsState()
    val saved by model.saved.collectAsState()
    val watched = saved.watch.any { it.corp == c.corp }
    AppCard(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text("고유번호 ${c.corp}" + (if (c.stock.isNotBlank()) " · 종목코드 ${c.stock}" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { model.toggleWatch(c) }) { Icon(if (watched) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (watched) "관심기업 해제" else "관심기업 추가") }
            TextButton(onClick = { Os.browse("https://dart.fss.or.kr/dsab007/main.do?option=corp&textCrpNm=" + java.net.URLEncoder.encode(c.name, Charsets.UTF_8)) }) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("DART에서 보기")
            }
        }
        TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
            CompanyTab.entries.forEach { t -> Tab(selected = t == tab, onClick = { model.setTab(t) }, text = { Text(t.label) }) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                CompanyTab.OVERVIEW -> LoadView(model.company.collectAsState().value, { model.retry(tab) }) { OverviewView(it) }
                CompanyTab.HOLDERS -> LoadView(model.holders.collectAsState().value, { model.retry(tab) }) { HoldersView(it) }
                CompanyTab.FINANCE -> LoadView(model.finance.collectAsState().value, { model.retry(tab) }) { FinanceView(it) }
                CompanyTab.FILINGS -> FilingsView(model)
            }
        }
    }
}

@Composable
private fun <T> LoadView(state: Load<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Load.Idle, Load.Loading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator(Modifier.size(24.dp)) }
        is Load.Failed -> Column(Modifier.padding(top = 10.dp)) { ErrorBox(state.message) { TextButton(onClick = retry) { Text("다시 시도") } } }
        is Load.Done -> content(state.value)
    }
}

@Composable
private fun Scroll(content: @Composable () -> Unit) {
    val sc = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        SelectionContainer { Column(Modifier.fillMaxWidth().verticalScroll(sc).padding(top = 10.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() } }
        VerticalScrollbar(rememberScrollbarAdapter(sc), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun OverviewView(c: DartCompany) = Scroll {
    LabelValue("회사명", c.name + if (c.nameEn.isNotBlank()) "  (${c.nameEn})" else "")
    LabelValue("시장", dartMarketLabel(c.cls) + if (c.stock.isNotBlank()) " · ${c.stock}" else "")
    LabelValue("대표자", c.ceo)
    LabelValue("법인등록번호", c.corpNo)
    LabelValue("사업자등록번호", c.bizNo)
    LabelValue("주소", c.address)
    LabelValue("전화", c.phone)
    LabelValue("홈페이지", c.homepage)
    LabelValue("IR", c.ir)
    LabelValue("업종코드", c.industryCode)
    LabelValue("설립일", ymdLabel(c.established))
    LabelValue("결산월", if (c.accountMonth.isNotBlank()) "${c.accountMonth}월" else "")
}

@Composable
private fun HoldersView(h: DartHolders) = Scroll {
    if (h.items.isEmpty()) { Text("최근 정기보고서에서 최대주주 현황을 찾지 못했습니다.", style = MaterialTheme.typography.bodyMedium); return@Scroll }
    Text("${h.basis} 기준 · 보통주 합계 ${h.totalCommon?.let { "$it%" } ?: "-"}" + (h.totalPref?.let { " · 우선주 합계 $it%" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TableRow(listOf("성명", "관계", "보통주(주)", "지분율", "우선주(주)", "지분율"), header = true)
    h.items.forEach {
        TableRow(listOf(it.name, it.relation, it.common?.shares?.let(INT::format) ?: "-", it.common?.ratio?.let { r -> "$r%" } ?: "-", it.pref?.shares?.let(INT::format) ?: "-", it.pref?.ratio?.let { r -> "$r%" } ?: "-"))
    }
}

@Composable
private fun FinanceView(f: DartFinance) = Scroll {
    if (f.balance.isEmpty()) { Text(f.note.ifBlank { "재무 자료가 없습니다." }, style = MaterialTheme.typography.bodyMedium); return@Scroll }
    Text("${f.basis} 재무제표 · 단위 억원" + (f.quarterLabel.takeIf { it.isNotBlank() }?.let { " · 최근 $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("재무상태", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
    TableRow(listOf("구분", "자산총계", "부채총계", "자본총계"), header = true)
    f.balance.forEach { TableRow(listOf(it.label, eok(it.assets), eok(it.liab), eok(it.equity))) }
    Text("손익", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
    TableRow(listOf("구분", "매출(영업수익)", "영업이익", "당기순이익"), header = true)
    f.income.forEach { TableRow(listOf(it.label + if (it.sub.isNotBlank()) " (${it.sub})" else "", eok(it.revenue), eok(it.op), eok(it.net))) }
    if (f.trend.isNotEmpty()) {
        Text("5개년 추이", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
        TableRow(listOf("연도", "매출", "영업이익", "순이익", "부채비율", "EPS(원)"), header = true)
        f.trend.forEach { TableRow(listOf("${it.year}", eok(it.revenue), eok(it.op), eok(it.net), it.debt?.let { d -> "$d%" } ?: "-", f.eps[it.year]?.let(INT::format) ?: "-")) }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { i, c ->
            Text(
                c, modifier = Modifier.weight(if (i == 0) 1.4f else 1f),
                style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                fontWeight = if (header) FontWeight.Medium else FontWeight.Normal,
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun FilingsView(model: DartModel) {
    val q by model.filingQuery.collectAsState()
    val state by model.filings.collectAsState()
    var find by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("a" to "정기보고서", "m" to "주요사항보고서", "e" to "기타공시").forEach { (t, l) ->
                FilterChip(selected = q.type == t, onClick = { model.setFilingQuery(q.copy(type = t)) }, label = { Text(l) })
            }
            Spacer(Modifier.width(10.dp))
            listOf(1, 3, 10).forEach { y -> FilterChip(selected = q.years == y, onClick = { model.setFilingQuery(q.copy(years = y)) }, label = { Text("${y}년") }) }
            IconButton(onClick = { model.retry(CompanyTab.FILINGS) }) { Icon(Icons.Outlined.Refresh, "새로고침") }
        }
        SearchField(find, { find = it }, {}, "공시 제목에서 찾기", Modifier.fillMaxWidth())
        LoadView(state, { model.retry(CompanyTab.FILINGS) }) { r ->
            val shown = if (find.isBlank()) r.items else r.items.filter { it.title.contains(find) || it.by.contains(find) }
            val ls = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                    item { Text("${ymdLabel(r.from)} ~ ${ymdLabel(r.to)} · ${shown.size}건", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(shown, key = { it.id }) { f ->
                        Row(Modifier.fillMaxWidth().clickable { Os.browse(f.url) }.padding(vertical = 7.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(ymdLabel(f.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(78.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(f.by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (f.note.isNotBlank()) Pill(f.note)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
            }
        }
    }
}
