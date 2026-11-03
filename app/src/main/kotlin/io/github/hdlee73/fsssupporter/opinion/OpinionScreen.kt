package io.github.hdlee73.fsssupporter.opinion

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.EmptyState
import io.github.hdlee73.fsssupporter.ui.ErrorBox
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.Tone
import io.github.hdlee73.fsssupporter.ui.parseYmd
import io.github.hdlee73.fsssupporter.ui.ymdLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

fun verdictTone(v: Verdict): Tone = when (v) {
    Verdict.OK -> Tone.OK
    Verdict.ALIAS -> Tone.WARN
    Verdict.DELETED, Verdict.NO_ARTICLE, Verdict.NO_LAW -> Tone.BAD
    Verdict.FAILED -> Tone.NEUTRAL
}

@Composable
fun OpinionScreen(model: OpinionModel, isSample: Boolean, onMessage: (String) -> Unit) {
    val data by model.data.collectAsState()
    val draft = data.drafts.firstOrNull { it.id == data.selected }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("의견서 도우미", "검사의견서·조치안·검사서 초안을 쓰고, 인용한 조문이 실제로 있는지·삭제되지 않았는지 국가법령정보로 확인합니다.") {
            NewDocButton(model)
        }
        if (isSample) Box(Modifier.padding(horizontal = 20.dp)) { InfoBox("데모 모드입니다. 설정에서 국가법령정보 OC를 입력해야 실제 법령으로 검증합니다.") }
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DraftList(model, data, Modifier.width(240.dp).fillMaxHeight())
            if (draft == null) AppCard(Modifier.weight(1f).fillMaxHeight()) {
                EmptyState(Icons.Outlined.EditNote, "문서를 고르거나 새로 만드세요", "오른쪽 위 '새 문서'에서 서식을 고르면 초안이 만들어집니다. 법령검색 화면의 '의견서에 넣기'로 조문을 바로 붙일 수도 있습니다.")
            } else {
                Editor(model, draft, Modifier.weight(1f).fillMaxHeight(), onMessage)
                ChecksPanel(model, draft, Modifier.width(360.dp).fillMaxHeight(), onMessage)
            }
        }
    }
}

@Composable
private fun NewDocButton(model: OpinionModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { open = true }) { Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("새 문서") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            model.templates.forEach { t ->
                DropdownMenuItem(text = { Text(t.name) }, onClick = { model.create(t, t.name.substringBefore(" (")); open = false })
            }
        }
    }
}

@Composable
private fun DraftList(model: OpinionModel, data: OpinionData, modifier: Modifier) {
    val fmt = remember { SimpleDateFormat("MM.dd HH:mm") }
    val ls = rememberLazyListState()
    Box(modifier) {
        LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (data.drafts.isEmpty()) item { Text("저장된 문서가 없습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            items(data.drafts.sortedByDescending { it.updatedAt }, key = { it.id }) { d ->
                Surface(
                    Modifier.fillMaxWidth().clickable { model.select(d.id) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (d.id == data.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(d.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(fmt.format(Date(d.updatedAt)) + " · ${d.body.length}자", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val custom = data.templates
            if (custom.isNotEmpty()) {
                item { Text("내 서식", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, start = 4.dp)) }
                items(custom, key = { "t" + it.id }) { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
                        IconButton(onClick = { model.deleteTemplate(t.id) }) { Icon(Icons.Outlined.Delete, "서식 삭제", Modifier.size(16.dp)) }
                    }
                }
            }
        }
        VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

@Composable
private fun Editor(model: OpinionModel, d: Draft, modifier: Modifier, onMessage: (String) -> Unit) {
    var title by remember(d.id) { mutableStateOf(d.title) }
    var body by remember(d.id) { mutableStateOf(d.body) }
    var asOfText by remember(d.id) { mutableStateOf(ymdLabel(d.asOf)) }
    var confirmDelete by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // 법령검색에서 조문을 넣으면 저장본이 바뀌므로 그때만 편집기 글을 다시 맞춘다.
    LaunchedEffect(d.body) { if (d.body != body && d.body.length > body.length && d.body.startsWith(body)) body = d.body }
    LaunchedEffect(title, body, asOfText) {
        delay(500)
        val asOf = parseYmd(asOfText).orEmpty()
        if (title != d.title || body != d.body || asOf != d.asOf) model.update(d.copy(title = title, body = body, asOf = asOf))
    }
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, Modifier.weight(1f), singleLine = true, label = { Text("문서 제목") }, textStyle = MaterialTheme.typography.titleSmall)
            OutlinedTextField(asOfText, { asOfText = it }, Modifier.width(170.dp), singleLine = true, label = { Text("검증 기준일 (비우면 현행)") },
                isError = asOfText.isNotBlank() && parseYmd(asOfText) == null, textStyle = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "문서 삭제") }
        }
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            placeholder = { Text("본문을 쓰거나 붙여 넣으세요. 「법령명」 제N조 형식의 인용을 자동으로 찾아 검증합니다.") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { model.update(d.copy(title = title, body = body, asOf = parseYmd(asOfText).orEmpty())); model.verify(d.copy(body = body, asOf = parseYmd(asOfText).orEmpty())) }) {
                Icon(Icons.Outlined.FactCheck, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("인용 조문 검증")
            }
            OutlinedButton(onClick = {
                scope.launch {
                    runCatching { model.exportHwpx(d.copy(title = title, body = body)) }
                        .onSuccess { onMessage("한글 파일로 저장했어요: ${it.name}"); Os.open(it.parentFile) }
                        .onFailure { onMessage("저장하지 못했어요: ${it.message}") }
                }
            }) { Text("한글(HWPX)") }
            OutlinedButton(onClick = {
                scope.launch {
                    runCatching { model.exportDocx(d.copy(title = title, body = body)) }
                        .onSuccess { onMessage("워드 파일로 저장했어요: ${it.name}"); Os.open(it.parentFile) }
                        .onFailure { onMessage("저장하지 못했어요: ${it.message}") }
                }
            }) { Text("워드(DOCX)") }
            OutlinedButton(onClick = { Os.copy(body); onMessage("본문을 복사했어요.") }) { Text("복사") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { templateName = title }) { Text("서식으로 저장") }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("문서를 삭제할까요?") },
        text = { Text("'${d.title}'이(가) 이 PC에서 지워지며 되돌릴 수 없습니다.") },
        confirmButton = { TextButton(onClick = { model.delete(d.id); confirmDelete = false }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
    )
    templateName?.let { name ->
        var n by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { templateName = null },
            title = { Text("서식으로 저장") },
            text = { OutlinedTextField(n, { n = it }, label = { Text("서식 이름") }, singleLine = true) },
            confirmButton = { TextButton(enabled = n.isNotBlank(), onClick = { model.saveAsTemplate(d.copy(body = body), n.trim()); templateName = null; onMessage("서식으로 저장했어요. '새 문서'에서 고를 수 있습니다.") }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { templateName = null }) { Text("취소") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecksPanel(model: OpinionModel, d: Draft, modifier: Modifier, onMessage: (String) -> Unit) {
    val state by model.checks.collectAsState()
    var open by remember { mutableStateOf<String?>(null) }
    AppCard(modifier) {
        CardTitle("인용 검증 결과")
        when (val s = state) {
            OpinionModel.CheckState.Idle -> Text("'인용 조문 검증'을 누르면 본문의 「법령명」 제N조 인용을 모두 찾아 확인합니다. 법령명 없이 '제5조'만 쓴 곳은 건너뜁니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OpinionModel.CheckState.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Text("국가법령정보에서 확인하는 중", style = MaterialTheme.typography.bodySmall)
            }
            is OpinionModel.CheckState.Failed -> ErrorBox(s.message)
            is OpinionModel.CheckState.Done -> {
                val r = if (s.draftId == d.id) s.results else emptyList()
                if (r.isEmpty()) Text("본문에서 법령 인용을 찾지 못했어요. 「자본시장과 금융투자업에 관한 법률」 제54조처럼 법령명과 조문을 함께 써 주세요.", style = MaterialTheme.typography.bodySmall)
                else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Verdict.entries.forEach { v -> r.count { it.verdict == v }.takeIf { it > 0 }?.let { Pill("${v.label} $it", verdictTone(v)) } }
                    }
                    val ls = rememberLazyListState()
                    Box(Modifier.weight(1f)) {
                        LazyColumn(state = ls, modifier = Modifier.fillMaxSize().padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(r.sortedBy { it.verdict.ordinal * -1 }, key = { it.citation.query }) { c ->
                                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(c.citation.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Pill(c.verdict.label, verdictTone(c.verdict))
                                    }
                                    if (c.citation.count > 1) Text("본문에 ${c.citation.count}번 나옴", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(c.message, style = MaterialTheme.typography.bodySmall)
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        c.suggestion?.let { sug -> TextButton(onClick = { Os.copy(sug); onMessage("복사했어요: $sug") }) { Text("정식 표기 복사") } }
                                        c.article?.let { a ->
                                            TextButton(onClick = { open = if (open == c.citation.query) null else c.citation.query }) { Text(if (open == c.citation.query) "원문 닫기" else "원문 보기") }
                                            TextButton(onClick = { model.insert((c.suggestion ?: c.citation.text) + "\n" + a.body); onMessage("본문 끝에 조문을 넣었어요.") }) { Text("원문 넣기") }
                                        }
                                    }
                                    if (open == c.citation.query) c.article?.let { a ->
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        Text(a.fullTitle, style = MaterialTheme.typography.labelLarge)
                                        Text(a.body, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                        VerticalScrollbar(rememberScrollbarAdapter(ls), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                    }
                }
            }
        }
    }
}
