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
 * 시작 화면: 짧은 인사, 바로 가기(한 가지 색), 최근 사용 목록(전체/즐겨찾기·찾기).
 * 메뉴는 위 탭에 모두 있으므로 여기서는 꼭 필요한 것만 차분하게 보여 준다.
 */
@Composable
fun HomeScreen(recents: RecentStore, badges: HomeBadges, onTool: (Dest) -> Unit, onRecent: (Recent) -> Unit) {
    val data by recents.data.collectAsState()
    var starredOnly by remember { mutableStateOf(false) }
    var find by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val sc = rememberScrollState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 1180.dp).fillMaxWidth().verticalScroll(sc).padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("안녕하세요", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(LocalDate.now().format(TODAY), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SectionTitle("바로 가기")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val perRow = if (maxWidth < 820.dp) 2 else 4
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Dest.tools.chunked(perRow).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { d -> ToolCard(d, badges.counts[d], Modifier.weight(1f)) { onTool(d) } }
                            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionTitle("최근 사용")
                Spacer(Modifier.width(10.dp))
                TextTab("전체", !starredOnly) { starredOnly = false }
                TextTab("즐겨찾기", starredOnly) { starredOnly = true }
                Spacer(Modifier.weight(1f))
                SearchField(find, { find = it }, {}, "이름 찾기", Modifier.width(240.dp))
            }
            val items = data.items
                .filter { !starredOnly || it.starred }
                .filter { find.isBlank() || it.title.contains(find.trim(), ignoreCase = true) || it.where.contains(find.trim(), ignoreCase = true) }
            Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (items.isEmpty()) {
                        Text(
                            when {
                                data.items.isEmpty() -> "아직 사용한 것이 없습니다. 서식·매뉴얼·법령·기업을 열면 여기에 쌓입니다."
                                starredOnly -> "즐겨찾기한 항목이 없습니다. 목록에서 ☆를 누르면 여기에 모입니다."
                                else -> "조건에 맞는 항목이 없습니다."
                            },
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(22.dp),
                        )
                    }
                    val shown = if (showAll) items.take(60) else items.take(10)
                    shown.forEachIndexed { i, r ->
                        if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        RecentRow(r, recents, onRecent)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (items.size > 10) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "접기" else "더 보기 (${items.size - 10})") }
                Spacer(Modifier.weight(1f))
                if (data.items.any { !it.starred }) TextButton(onClick = recents::clear) { Text("최근 기록 지우기", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

private val TODAY = DateTimeFormatter.ofPattern("M월 d일 EEEE", java.util.Locale.KOREAN)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun TextTab(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToolCard(d: Dest, badge: String?, modifier: Modifier, onClick: () -> Unit) {
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hovered) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(72.dp).clip(RoundedCornerShape(12.dp)).hoverable(hover).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToolGlyph(d.icon)
            Column(Modifier.weight(1f)) {
                Text(d.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(badge ?: d.hint, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (badge != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 도구 그림: 모든 도구가 같은 한 가지 색(주 색)의 옅은 동그라미 */
@Composable
fun ToolGlyph(icon: ImageVector, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size((size * 0.5f).dp))
    }
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
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().hoverable(hover).clickable { onOpen(r) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(d.icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(r.type.label, r.where.ifBlank { d.label }).distinct().joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(whenLabel(r.at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 별·빼기는 마우스를 올렸을 때만(즐겨찾기한 것은 별을 늘 보여 줌)
        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
            Row {
                if (hovered || r.starred) IconButton(onClick = { recents.toggleStar(r.key) }, modifier = Modifier.size(30.dp)) {
                    Icon(if (r.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (r.starred) "즐겨찾기 해제" else "즐겨찾기", Modifier.size(16.dp),
                        tint = if (r.starred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
                }
                if (hovered) IconButton(onClick = { recents.remove(r.key) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Outlined.Close, "목록에서 빼기", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
