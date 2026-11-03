package io.github.hdlee73.fsssupporter.verify

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.hdlee73.everylaw.core.model.formatYmd
import io.github.hdlee73.fsssupporter.cases.pickFile
import io.github.hdlee73.fsssupporter.data.HwpText
import io.github.hdlee73.fsssupporter.opinion.CitationCheck
import io.github.hdlee73.fsssupporter.opinion.CitationVerifier
import io.github.hdlee73.fsssupporter.opinion.Verdict
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun verdictTone(v: Verdict): Tone = when (v) {
    Verdict.OK -> Tone.OK
    Verdict.ALIAS -> Tone.WARN
    Verdict.DELETED, Verdict.NO_ARTICLE, Verdict.NO_LAW -> Tone.BAD
    Verdict.FAILED -> Tone.NEUTRAL
}

/**
 * 문서 법규 검증: 이미 만든 한글 문서(HWP·HWPX)를 불러와, 글 속에 인용된 「법령」 제N조가 기준일(위반 당시 등)에
 * 실제로 시행 중이던 조문과 맞는지 확인한다.
 */
class VerifyModel(private val verifier: CitationVerifier, private val scope: CoroutineScope) {
    sealed interface State {
        data object Idle : State
        data class Reading(val name: String) : State
        data object Running : State
        data class Done(val results: List<CitationCheck>, val asOf: String?) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    var file by mutableStateOf<File?>(null)
        private set
    var text by mutableStateOf("")
    var asOfText by mutableStateOf("")
    /** 검사건에서 열었으면 그 이름 */
    var caseTitle by mutableStateOf<String?>(null)
    private var job: Job? = null

    fun load(f: File, case: String? = null) {
        file = f
        caseTitle = case
        job?.cancel()
        job = scope.launch {
            _state.value = State.Reading(f.name)
            try {
                text = withContext(Dispatchers.IO) { HwpText.extract(f) }
                run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = State.Failed("문서를 읽지 못했습니다: ${e.message}")
            }
        }
    }

    fun run() {
        val asOf = asOfText.trim().takeIf { it.isNotEmpty() }?.let(::parseYmd)
        if (asOfText.isNotBlank() && asOf == null) { _state.value = State.Failed("기준일을 2024-03-15처럼 넣어 주세요."); return }
        if (text.isBlank()) { _state.value = State.Failed("검증할 글이 없습니다. 문서를 불러오거나 글을 붙여 넣으세요."); return }
        job?.cancel()
        job = scope.launch {
            _state.value = State.Running
            _state.value = try {
                State.Done(verifier.verify(text, asOf), asOf)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed("검증하지 못했습니다: ${e.message}")
            }
        }
    }

    fun clear() { file = null; text = ""; caseTitle = null; _state.value = State.Idle }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerifyScreen(model: VerifyModel, isSample: Boolean, onOpenLaw: (String) -> Unit, onMessage: (String) -> Unit) {
    val state by model.state.collectAsState()
    var filter by remember { mutableStateOf<Verdict?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("문서 법규 검증", "이미 작성한 한글 문서(HWP·HWPX)를 불러와, 인용한 「법령」 제N조가 기준일(위반행위 당시 등)에 시행 중이던 조문과 맞는지 확인합니다.") {
            if (isSample) Pill("데모 데이터", Tone.WARN)
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppCard(Modifier.fillMaxWidth()) {
                    CardTitle("1. 문서")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { pickFile("검증할 한글 문서 고르기") { it.endsWith(".hwp") || it.endsWith(".hwpx") }?.let { model.load(it) } }) {
                            Icon(Icons.Outlined.FileOpen, null, Modifier.size(16.dp)); Text(" 한글 문서 불러오기")
                        }
                        if (model.file != null || model.text.isNotBlank()) TextButton(onClick = { model.clear() }) { Text("비우기") }
                    }
                    model.file?.let { f ->
                        Text(f.name + (model.caseTitle?.let { " · 검사건: $it" } ?: ""), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { Os.open(f) }) { Text("한글로 열기") }
                    }
                    CardTitle("2. 기준일")
                    OutlinedTextField(model.asOfText, { model.asOfText = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("기준일(위반행위일 등) · 비우면 현행 기준") }, placeholder = { Text("2024-03-15") },
                        isError = model.asOfText.isNotBlank() && parseYmd(model.asOfText) == null)
                    Button(onClick = { model.run() }, modifier = Modifier.fillMaxWidth(), enabled = state !is VerifyModel.State.Running) {
                        Icon(Icons.Outlined.Rule, null, Modifier.size(16.dp)); Text(" 인용 법규 검증")
                    }
                    Text("「법령명」 제N조, 약칭(자본시장법 제54조)도 찾습니다. 법령명 없이 '제5조'만 쓴 곳, '같은 법'처럼 앞 문장을 받는 표현은 건너뜁니다.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AppCard(Modifier.fillMaxWidth().weight(1f)) {
                    CardTitle("문서 글자") { Text("${model.text.length}자", style = MaterialTheme.typography.labelSmall) }
                    OutlinedTextField(model.text, { model.text = it }, Modifier.fillMaxSize(), textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text("문서를 불러오면 글자가 여기에 나옵니다. 글을 직접 붙여 넣어도 됩니다.") })
                }
            }
            AppCard(Modifier.weight(1f).fillMaxHeight()) {
                when (val s = state) {
                    VerifyModel.State.Idle -> EmptyState(Icons.Outlined.Rule, "문서를 불러오세요", "검사의견서·조치안·검사서 등 이미 작성한 한글 문서를 불러오면 인용된 법규를 모두 찾아 기준일 조문과 대조합니다. 검사건의 '문서·서식' 탭에서도 바로 검증할 수 있습니다.")
                    is VerifyModel.State.Reading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("'${s.name}' 읽는 중")
                    }
                    VerifyModel.State.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("국가법령정보에서 조문을 확인하는 중")
                    }
                    is VerifyModel.State.Failed -> ErrorBox(s.message)
                    is VerifyModel.State.Done -> {
                        val r = s.results
                        CardTitle("인용 ${r.size}건 · " + (s.asOf?.let { "${formatYmd(it)} 시행 기준" } ?: "현행 기준")) {
                            TextButton(onClick = { Os.copy(report(r, s.asOf, model.file?.name)); onMessage("검증 결과를 복사했습니다.") }) { Text("결과 복사") }
                        }
                        if (r.isEmpty()) InfoBox("법령명과 조문 번호가 함께 쓰인 인용을 찾지 못했습니다.")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(filter == null, { filter = null }, label = { Text("전체 ${r.size}") })
                            Verdict.entries.forEach { v -> r.count { it.verdict == v }.takeIf { it > 0 }?.let { n -> FilterChip(filter == v, { filter = if (filter == v) null else v }, label = { Text("${v.label} $n") }) } }
                            r.count { it.changedSince }.takeIf { it > 0 }?.let { Pill("기준일 뒤 개정 $it", Tone.WARN, Modifier.padding(top = 8.dp)) }
                        }
                        HorizontalDivider()
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(r.filter { filter == null || it.verdict == filter }, key = { it.citation.query }) { c -> CheckRow(c, onOpenLaw) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(c: CitationCheck, onOpenLaw: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(c.verdict.label, verdictTone(c.verdict))
            if (c.changedSince) Pill("기준일 뒤 개정", Tone.WARN)
            Text(c.citation.text + if (c.citation.count > 1) " (${c.citation.count}곳)" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOpenLaw(c.citation.query) }) { Text("법률 도우미") }
        }
        Text(c.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (open) {
            c.article?.let { a ->
                Text("기준일 조문", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                SelectionContainer { Text(a.fullTitle + "\n" + a.body, style = MaterialTheme.typography.bodySmall) }
            }
            c.current?.let { a ->
                Text("현행 조문", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                SelectionContainer { Text(a.fullTitle + "\n" + a.body, style = MaterialTheme.typography.bodySmall) }
            }
        } else if (c.article != null) Text("눌러서 조문 보기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private fun report(r: List<CitationCheck>, asOf: String?, name: String?): String = buildString {
    appendLine("문서 법규 검증 결과" + (name?.let { " - $it" } ?: ""))
    appendLine("기준: " + (asOf?.let { "${formatYmd(it)} 시행 법령" } ?: "현행 법령"))
    r.forEach { c -> appendLine("[${c.verdict.label}${if (c.changedSince) "·기준일 뒤 개정" else ""}] ${c.citation.text} - ${c.message}") }
}
