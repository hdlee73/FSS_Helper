package io.github.hdlee73.fsssupporter.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.data.Recent
import io.github.hdlee73.fsssupporter.data.RecentKind
import io.github.hdlee73.fsssupporter.data.RecentStore
import io.github.hdlee73.fsssupporter.ui.Dest
import io.github.hdlee73.fsssupporter.ui.SearchField
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 홈 카드에 붙일 작은 표시(예: 할 일 3) */
data class HomeBadges(val counts: Map<Dest, String> = emptyMap())

/**
 * 이음오피스 시작 화면처럼: 인사, 도구 카드 두 줄, 아래에 최근 사용 목록(탭·찾기·즐겨찾기).
 */
@Composable
fun HomeScreen(recents: RecentStore, badges: HomeBadges, onTool: (Dest) -> Unit, onRecent: (Recent) -> Unit) {
    val data by recents.data.collectAsState()
    var tab by remember { mutableStateOf<RecentKind?>(null) }
    var starredOnly by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    val sc = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(sc).padding(horizontal = 36.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Column {
            Text("안녕하세요.", style = MaterialTheme.typography.headlineMedium)
            Text("무엇을 하고 싶으신가요?", style = MaterialTheme.typography.headlineSmall)
        }
        // 도구 카드: 화면 너비에 맞춰 한 줄 4개(좁으면 2개)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth < 760.dp) 2 else 4
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Dest.tools.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { d -> ToolCard(d, badges.counts[d], Modifier.weight(1f)) { onTool(d) } }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        // 최근 사용
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Segmented(
                listOf("전체") + RecentKind.entries.map { it.label } + "즐겨찾기",
                when { starredOnly -> RecentKind.entries.size + 1; tab == null -> 0; else -> RecentKind.entries.indexOf(tab) + 1 },
            ) { i ->
                starredOnly = i == RecentKind.entries.size + 1
                tab = if (i in 1..RecentKind.entries.size) RecentKind.entries[i - 1] else null
            }
            Spacer(Modifier.weight(1f))
            SearchField(find, { find = it }, {}, "이름 찾기", Modifier.width(260.dp))
        }
        val items = data.items
            .filter { tab == null || it.type == tab }
            .filter { !starredOnly || it.starred }
            .filter { find.isBlank() || it.title.contains(find.trim(), ignoreCase = true) || it.where.contains(find.trim(), ignoreCase = true) }
        Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    HeaderCell("이름", Modifier.weight(1f))
                    HeaderCell("종류", Modifier.width(110.dp))
                    HeaderCell("위치", Modifier.width(180.dp))
                    HeaderCell("사용한 날짜 ↓", Modifier.width(150.dp))
                    Spacer(Modifier.width(72.dp))
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (items.isEmpty()) {
                    Text(
                        if (data.items.isEmpty()) "아직 사용한 것이 없습니다. 위 도구에서 서식·매뉴얼·법령·기업을 열면 여기에 쌓입니다." else "조건에 맞는 항목이 없습니다.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp),
                    )
                }
                items.take(40).forEach { r -> RecentRow(r, recents, onRecent) }
            }
        }
        Row {
            Text("최근 사용 ${data.items.size}개 · 즐겨찾기 ${data.items.count { it.starred }}개", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (data.items.any { !it.starred }) TextButton(onClick = recents::clear) { Text("최근 기록 지우기(즐겨찾기는 남김)") }
        }
    }
}

@Composable
private fun ToolCard(d: Dest, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (hovered) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = if (hovered) 3.dp else 0.dp,
        modifier = modifier.height(112.dp).clip(RoundedCornerShape(12.dp)).hoverable(hover).clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            ToolGlyph(d.icon, d.tint)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (badge != null) Box(Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                        Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(d.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 이음오피스 파일 아이콘처럼: 옅은 바탕에 왼쪽 색 띠와 도구 그림 */
@Composable
fun ToolGlyph(icon: ImageVector, tint: Color, size: Int = 30) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.10f))) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(tint))
        Icon(icon, null, tint = tint, modifier = Modifier.align(Alignment.Center).padding(start = 2.dp).size((size * 0.58f).dp))
    }
}

@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(l, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

private val TIME = DateTimeFormatter.ofPattern("a h:mm")
private val DATE = DateTimeFormatter.ofPattern("M월 d일")

private fun whenLabel(at: Long): String {
    val t = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val d = t.toLocalDate()
    val today = LocalDate.now()
    val day = when (d) { today -> "오늘"; today.minusDays(1) -> "어제"; else -> d.format(DATE) }
    return "$day · " + t.format(TIME).replace("AM", "오전").replace("PM", "오후")
}

private fun kindDest(k: RecentKind): Dest = when (k) {
    RecentKind.FORM, RecentKind.FILE -> Dest.FORMS
    RecentKind.MANUAL -> Dest.MANUAL
    RecentKind.LAW -> Dest.LAW
    RecentKind.CORP -> Dest.DART
}

@Composable
private fun RecentRow(r: Recent, recents: RecentStore, onOpen: (Recent) -> Unit) {
    val d = kindDest(r.type)
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(r) }.padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolGlyph(d.icon, d.tint, 24)
            Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(r.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(r.where.ifBlank { d.label }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(180.dp))
        Text(whenLabel(r.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(150.dp))
        IconButton(onClick = { recents.toggleStar(r.key) }, modifier = Modifier.size(32.dp)) {
            Icon(if (r.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (r.starred) "즐겨찾기 해제" else "즐겨찾기", Modifier.size(17.dp),
                tint = if (r.starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
        }
        IconButton(onClick = { recents.remove(r.key) }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Close, "목록에서 빼기", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.outline)
        }
    }
}
