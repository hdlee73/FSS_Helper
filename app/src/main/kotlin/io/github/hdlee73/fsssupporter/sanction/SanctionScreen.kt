package io.github.hdlee73.fsssupporter.sanction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.opinion.OpinionModel
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.LabelValue
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.ScreenHeader
import io.github.hdlee73.fsssupporter.ui.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 화면 상태(메뉴를 오가도 유지) */
class SanctionUi {
    var tab by mutableStateOf(0)
    var fineId by mutableStateOf("fine-penalty")
    var levelId by mutableStateOf("lv-exec")
    var directBase by mutableStateOf("")
    var violation by mutableStateOf("")
    var gradeIndex by mutableStateOf(0)
    var levelIndex by mutableStateOf(2)
    val agg = mutableStateListOf<String>()
    val mit = mutableStateListOf<String>()

    fun reset() { agg.clear(); mit.clear() }
}

@Composable
fun SanctionScreen(
    store: SanctionStore,
    ui: SanctionUi,
    onOpenLaw: (String) -> Unit,
    onInsertToOpinion: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val data by store.data.collectAsState()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("제재 양정 계산기", "기준금액·위반 정도·가중·감경 사유를 넣으면 산정 과정을 단계별로 보여 주고, 조치안에 그대로 붙일 수 있습니다.")
        TabRow(selectedTabIndex = ui.tab, modifier = Modifier.padding(horizontal = 20.dp).width(360.dp)) {
            Tab(ui.tab == 0, { ui.tab = 0; ui.reset() }, text = { Text("과징금·과태료") })
            Tab(ui.tab == 1, { ui.tab = 1; ui.reset() }, text = { Text("임원·직원·기관 제재") })
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.tab == 0) {
                val profiles = store.fines(data)
                val p = profiles.firstOrNull { it.id == ui.fineId } ?: profiles.first()
                FineInputs(store, p, profiles, ui, onOpenLaw, Modifier.weight(1f).fillMaxHeight())
                val result = SanctionCalc.fine(p, FineInput(ui.directBase.filter(Char::isDigit).toLongOrNull(), ui.violation.filter(Char::isDigit).toLongOrNull(), ui.gradeIndex, ui.agg.toSet(), ui.mit.toSet()))
                val reasons = (p.aggravations.filter { it.id in ui.agg }.map { "가중: ${it.label}" } + p.mitigations.filter { it.id in ui.mit }.map { "감경: ${it.label}" })
                ResultPanel("${p.kind} 산정", p.name, p.basis, result.lines, reasons, SanctionCalc.won(result.final),
                    warn = if (result.limited) "법정 최고한도로 제한됐습니다." else null,
                    onInsertToOpinion, onMessage, Modifier.width(380.dp).fillMaxHeight())
            } else {
                val profiles = store.levels(data)
                val p = profiles.firstOrNull { it.id == ui.levelId } ?: profiles.first()
                LevelInputs(store, p, profiles, ui, onOpenLaw, Modifier.weight(1f).fillMaxHeight())
                val result = SanctionCalc.level(p, ui.levelIndex, ui.agg.toSet(), ui.mit.toSet())
                val reasons = (p.aggravations.filter { it.id in ui.agg }.map { "가중: ${it.label}" } + p.mitigations.filter { it.id in ui.mit }.map { "감경: ${it.label}" })
                ResultPanel("${p.target} 제재 양정", p.name, p.basis, result.lines, reasons, result.final,
                    warn = if (result.clipped) "단계 범위 끝에서 멈췄습니다." else null,
                    onInsertToOpinion, onMessage, Modifier.width(380.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun <T> ProfilePicker(current: String, items: List<T>, name: (T) -> String, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }) { Text(current) }
    DropdownMenu(open, { open = false }) {
        items.forEach { DropdownMenuItem(text = { Text(name(it)) }, onClick = { open = false; onPick(it) }) }
    }
}

@Composable
private fun FineInputs(store: SanctionStore, p: FineProfile, profiles: List<FineProfile>, ui: SanctionUi, onOpenLaw: (String) -> Unit, modifier: Modifier) {
    var editing by remember { mutableStateOf(false) }
    AppCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfilePicker(p.name, profiles, { it.name }) { ui.fineId = it.id; ui.gradeIndex = 0; ui.reset() }
                TextButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, null); Text(" 기준 고치기") }
                TextButton(onClick = { store.saveFine(p.copy(id = newKey(), name = p.name.replace(" (계산 틀 예시)", "") + " 사본")) }) { Text("사본 만들기") }
                if (p.basis.isNotBlank()) TextButton(onClick = { onOpenLaw(p.basis) }) { Icon(Icons.Outlined.ManageSearch, null); Text(" 근거 규정") }
            }
            if (p.builtIn) InfoBox("기본 기준의 비율·상한은 계산 틀을 보여 주는 예시값입니다. '기준 고치기'에서 근거 규정 별표의 수치로 맞춰 쓰세요. 고친 기준은 이 PC에 저장됩니다.")
            CardTitle("1. 기준금액")
            Text(if (p.statutoryMax > 0) "법정 최고한도 ${SanctionCalc.won(p.statutoryMax)}" else "법정 최고한도 미설정 (기준 고치기에서 입력)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = ui.violation,
                onValueChange = { v -> ui.violation = v.filter(Char::isDigit).take(15) },
                label = { Text("위반 관련 금액(원) · 있으면 금액 × 위반 정도 비율") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = ui.violation.toLongOrNull()?.let { { Text(SanctionCalc.won(it)) } },
            )
            OutlinedTextField(
                value = ui.directBase,
                onValueChange = { v -> ui.directBase = v.filter(Char::isDigit).take(15) },
                label = { Text("기준금액 직접 입력(원) · 입력하면 위 두 방식보다 우선") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = ui.directBase.toLongOrNull()?.let { { Text(SanctionCalc.won(it)) } },
            )
            CardTitle("2. 위반 정도 (부과기준율)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.grades.forEachIndexed { i, g ->
                    FilterChip(ui.gradeIndex == i, { ui.gradeIndex = i }, label = { Text("${g.label} ${SanctionCalc.pct(g.percent)}") })
                }
            }
            FactorList("3. 가중 사유", "합계 상한 ${SanctionCalc.pct(p.aggravationCap)}", p.aggravations, ui.agg) { "+" + SanctionCalc.pct(it.percent) }
            FactorList("4. 감경 사유", "합계 상한 ${SanctionCalc.pct(p.mitigationCap)}", p.mitigations, ui.mit) { "-" + SanctionCalc.pct(it.percent) }
        }
    }
    if (editing) FineEditor(p, onDismiss = { editing = false }, onDelete = if (!p.builtIn || store.isOverridden(p.id)) ({
        store.deleteFine(p.id); editing = false; ui.reset()
    }) else null) { store.saveFine(it); editing = false }
}

@Composable
private fun LevelInputs(store: SanctionStore, p: LevelProfile, profiles: List<LevelProfile>, ui: SanctionUi, onOpenLaw: (String) -> Unit, modifier: Modifier) {
    var editing by remember { mutableStateOf(false) }
    AppCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfilePicker(p.name, profiles, { it.name }) { ui.levelId = it.id; ui.levelIndex = (it.levels.size / 2); ui.reset() }
                TextButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, null); Text(" 기준 고치기") }
                if (p.basis.isNotBlank()) TextButton(onClick = { onOpenLaw(p.basis) }) { Icon(Icons.Outlined.ManageSearch, null); Text(" 근거 규정") }
            }
            if (p.builtIn) InfoBox("양정 단계와 가중·감경 상한은 예시값입니다. 근거 규정과 부서 기준에 맞게 고쳐 쓰세요.")
            CardTitle("1. 기본 양정 (위반 정도로 정한 단계)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                p.levels.forEachIndexed { i, l -> FilterChip(ui.levelIndex == i, { ui.levelIndex = i }, label = { Text(l) }) }
            }
            FactorList("2. 가중 사유", "합계 상한 ${p.aggravationCapSteps}단계", p.aggravations, ui.agg) { "+${it.steps}단계" }
            FactorList("3. 감경 사유", "합계 상한 ${p.mitigationCapSteps}단계", p.mitigations, ui.mit) { "-${it.steps}단계" }
        }
    }
    if (editing) LevelEditor(p, onDismiss = { editing = false }) { store.saveLevel(it); editing = false }
}

@Composable
private fun FactorList(title: String, cap: String, factors: List<Factor>, picked: MutableList<String>, amount: (Factor) -> String) {
    CardTitle(title) { Pill(cap) }
    factors.forEach { f ->
        Row(
            Modifier.fillMaxWidth().clickable { if (f.id in picked) picked.remove(f.id) else picked.add(f.id) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(f.id in picked, { if (it) picked.add(f.id) else picked.remove(f.id) })
            Text(f.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(amount(f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ResultPanel(
    title: String, profileName: String, basis: String, lines: List<Pair<String, String>>, reasons: List<String>, headline: String, warn: String?,
    onInsertToOpinion: (String) -> Unit, onMessage: (String) -> Unit, modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    AppCard(modifier) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CardTitle(title)
            Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            if (warn != null) Pill(warn, Tone.WARN)
            HorizontalDivider()
            lines.forEach { (k, v) -> LabelValue(k, v, 80) }
            if (reasons.isNotEmpty()) {
                HorizontalDivider()
                Text("반영 사유", style = MaterialTheme.typography.labelMedium)
                reasons.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
            val text = SanctionCalc.summary(title, profileName, basis, lines, reasons)
            Button(onClick = { onInsertToOpinion(text) }, modifier = Modifier.fillMaxWidth()) { Text("조치안에 넣기") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { Os.copy(text); onMessage("산정 내용을 복사했습니다.") }) { Icon(Icons.Outlined.ContentCopy, null); Text(" 복사") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val file = runCatching {
                            withContext(Dispatchers.IO) {
                                File(AppDirs.exports, OpinionModel.safeName("양정_${title}_${java.time.LocalDate.now()}") + ".xlsx").also { f ->
                                    f.outputStream().use { SanctionCalc.writeXlsx(title, profileName, basis, lines, reasons, it) }
                                }
                            }
                        }
                        file.onSuccess { onMessage("엑셀로 저장했습니다: ${it.name}"); Os.open(it) }
                            .onFailure { onMessage("저장하지 못했습니다: ${it.message}") }
                    }
                }) { Icon(Icons.Outlined.TableChart, null); Text(" 엑셀") }
            }
            Text("※ 계산 결과는 판단을 돕는 참고값입니다. 최종 양정은 근거 규정과 심의 결과에 따릅니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 숫자 입력 칸. 글자 입력 중 끊기지 않게 화면 안에 글을 두고, 숫자로 읽힐 때만 알린다. */
@Composable
private fun NumberField(label: String, initial: String, modifier: Modifier = Modifier, onValue: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(text, { text = it; onValue(it) }, modifier, label = { Text(label) }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun FactorEditor(title: String, items: MutableList<Factor>, steps: Boolean) {
    CardTitle(title) {
        IconButton(onClick = { items.add(Factor(label = "새 사유", percent = 10.0, steps = 1)) }) { Icon(Icons.Outlined.Add, "추가") }
    }
    items.toList().forEachIndexed { i, f ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            var label by remember(f.id) { mutableStateOf(f.label) }
            OutlinedTextField(label, { label = it; items[i] = items[i].copy(label = it) }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
            if (steps) NumberField("단계", f.steps.toString(), Modifier.width(80.dp)) { v -> v.toIntOrNull()?.let { items[i] = items[i].copy(steps = it) } }
            else NumberField("%", SanctionCalc.pct(f.percent).removeSuffix("%"), Modifier.width(80.dp)) { v -> v.toDoubleOrNull()?.let { items[i] = items[i].copy(percent = it) } }
            IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Outlined.Close, "삭제") }
        }
    }
}

@Composable
private fun FineEditor(p: FineProfile, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (FineProfile) -> Unit) {
    var name by rememberSaveable { mutableStateOf(p.name) }
    var basis by remember { mutableStateOf(p.basis) }
    var max by remember { mutableStateOf(p.statutoryMax.toString()) }
    var aggCap by remember { mutableStateOf(p.aggravationCap) }
    var mitCap by remember { mutableStateOf(p.mitigationCap) }
    var unit by remember { mutableStateOf(p.roundUnit) }
    val grades = remember { mutableStateListOf(*p.grades.toTypedArray()) }
    val agg = remember { mutableStateListOf(*p.aggravations.toTypedArray()) }
    val mit = remember { mutableStateListOf(*p.mitigations.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("산정 기준 고치기") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).width(560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("기준 이름") }, singleLine = true)
                OutlinedTextField(basis, { basis = it }, Modifier.fillMaxWidth(), label = { Text("근거 규정(법령명)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(max, { max = it.filter(Char::isDigit).take(15) }, Modifier.weight(1f), label = { Text("법정 최고한도(원)") }, singleLine = true)
                    NumberField("끝수 단위(원)", unit.toString(), Modifier.width(130.dp)) { v -> v.toLongOrNull()?.let { unit = it } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("가중 상한(%)", SanctionCalc.pct(aggCap).removeSuffix("%"), Modifier.weight(1f)) { v -> v.toDoubleOrNull()?.let { aggCap = it } }
                    NumberField("감경 상한(%)", SanctionCalc.pct(mitCap).removeSuffix("%"), Modifier.weight(1f)) { v -> v.toDoubleOrNull()?.let { mitCap = it } }
                }
                CardTitle("위반 정도별 비율") {
                    IconButton(onClick = { grades.add(Grade("구분", 50.0)) }) { Icon(Icons.Outlined.Add, "추가") }
                }
                grades.toList().forEachIndexed { i, g ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        var label by remember(i, grades.size) { mutableStateOf(g.label) }
                        OutlinedTextField(label, { label = it; grades[i] = grades[i].copy(label = it) }, Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodySmall)
                        NumberField("%", SanctionCalc.pct(g.percent).removeSuffix("%"), Modifier.width(80.dp)) { v -> v.toDoubleOrNull()?.let { grades[i] = grades[i].copy(percent = it) } }
                        IconButton(onClick = { grades.removeAt(i) }) { Icon(Icons.Outlined.Close, "삭제") }
                    }
                }
                FactorEditor("가중 사유", agg, steps = false)
                FactorEditor("감경 사유", mit, steps = false)
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(p.copy(name = name.ifBlank { p.name }, basis = basis.trim(), statutoryMax = max.toLongOrNull() ?: 0, aggravationCap = aggCap,
                    mitigationCap = mitCap, roundUnit = unit.coerceAtLeast(1), grades = grades.toList(), aggravations = agg.toList(), mitigations = mit.toList()))
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(if (p.builtIn) "기본값으로 되돌리기" else "삭제") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

@Composable
private fun LevelEditor(p: LevelProfile, onDismiss: () -> Unit, onSave: (LevelProfile) -> Unit) {
    var levels by remember { mutableStateOf(p.levels.joinToString(", ")) }
    var basis by remember { mutableStateOf(p.basis) }
    var aggCap by remember { mutableStateOf(p.aggravationCapSteps) }
    var mitCap by remember { mutableStateOf(p.mitigationCapSteps) }
    val agg = remember { mutableStateListOf(*p.aggravations.toTypedArray()) }
    val mit = remember { mutableStateListOf(*p.mitigations.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("양정 기준 고치기 · ${p.target}") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).width(560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(levels, { levels = it }, Modifier.fillMaxWidth(), label = { Text("양정 단계 (무거운 것부터, 쉼표로 구분)") })
                OutlinedTextField(basis, { basis = it }, Modifier.fillMaxWidth(), label = { Text("근거 규정(법령명)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("가중 상한(단계)", aggCap.toString(), Modifier.weight(1f)) { v -> v.toIntOrNull()?.let { aggCap = it } }
                    NumberField("감경 상한(단계)", mitCap.toString(), Modifier.weight(1f)) { v -> v.toIntOrNull()?.let { mitCap = it } }
                }
                FactorEditor("가중 사유", agg, steps = true)
                FactorEditor("감경 사유", mit, steps = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val lv = levels.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
                onSave(p.copy(levels = lv.ifEmpty { p.levels }, basis = basis.trim(), aggravationCapSteps = aggCap.coerceAtLeast(0),
                    mitigationCapSteps = mitCap.coerceAtLeast(0), aggravations = agg.toList(), mitigations = mit.toList()))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
