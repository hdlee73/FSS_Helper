package io.github.hdlee73.fsssupporter.cases

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Rule
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
import io.github.hdlee73.fsssupporter.sanction.Factor
import io.github.hdlee73.fsssupporter.sanction.FineProfile
import io.github.hdlee73.fsssupporter.sanction.LevelProfile
import io.github.hdlee73.fsssupporter.sanction.SanctionCalc
import io.github.hdlee73.fsssupporter.sanction.SanctionStore
import io.github.hdlee73.fsssupporter.tasks.LocalField
import io.github.hdlee73.fsssupporter.tasks.WorkTask
import io.github.hdlee73.fsssupporter.ui.AppCard
import io.github.hdlee73.fsssupporter.ui.CardTitle
import io.github.hdlee73.fsssupporter.ui.EmptyState
import io.github.hdlee73.fsssupporter.ui.InfoBox
import io.github.hdlee73.fsssupporter.ui.Os
import io.github.hdlee73.fsssupporter.ui.Pill
import io.github.hdlee73.fsssupporter.ui.Tone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ACTIONS = listOf("문책", "주의", "자율처리필요", "경영유의", "개선", "현지조치")

@Composable
fun FindingsTab(store: FindingStore, sanction: SanctionStore, task: WorkTask, ui: CaseUi, actions: CaseActions) {
    val data by store.data.collectAsState()
    val sData by sanction.data.collectAsState()
    val list = data.byCase[task.id].orEmpty()
    val levels = sanction.levels(sData)
    val fines = sanction.fines(sData)
    val scope = rememberCoroutineScope()
    val selected = list.firstOrNull { it.id == ui.findingId }
    fun export(ext: String) {
        scope.launch {
            val f = runCatching {
                withContext(Dispatchers.IO) {
                    CaseFiles.unique(CaseFiles.dir(task), "조치명세_${CaseFiles.safe(task.title)}_${java.time.LocalDate.now()}.$ext").also { file ->
                        file.outputStream().use { if (ext == "hwpx") FindingStore.writeHwpx(task, list, sanction, it) else FindingStore.writeXlsx(task, list, sanction, it) }
                    }
                }
            }
            f.onSuccess { actions.message("검사건 폴더에 저장했습니다: ${it.name}"); Os.open(it) }.onFailure { actions.message("저장하지 못했습니다: ${it.message}") }
        }
    }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(330.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { val f = Finding(title = "지적사항 ${list.size + 1}"); store.upsert(task.id, f); ui.findingId = f.id }) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Text(" 지적사항")
                }
                OutlinedButton(onClick = { export("hwpx") }, enabled = list.isNotEmpty()) { Text("명세 한글") }
                OutlinedButton(onClick = { export("xlsx") }, enabled = list.isNotEmpty()) { Text("엑셀") }
            }
            AppCard(Modifier.fillMaxSize()) {
                if (list.isEmpty()) {
                    EmptyState(Icons.Outlined.Rule, "지적사항이 없어요", "지적사항을 추가하고, 지적사항마다 관련 임직원(행위자·보조자·지시·감독자)을 넣으면 사람별 양정과 과징금·과태료를 계산해 조치 명세로 만듭니다.")
                } else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(list, key = { _, f -> f.id }) { i, f ->
                        val sel = f.id == selected?.id
                        Column(
                            Modifier.fillMaxWidth().background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .clickable { ui.findingId = f.id }.padding(8.dp),
                        ) {
                            Text("${i + 1}. ${f.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val sum = summaryOf(f, levels, fines)
                            Text(sum, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (selected == null) {
            ActionTable(list, sanction, Modifier.weight(1f).fillMaxHeight())
        } else {
            FindingEditor(store, task, selected, levels, fines, actions, onClose = { ui.findingId = null }, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

private fun summaryOf(f: Finding, levels: List<LevelProfile>, fines: List<FineProfile>): String {
    val lv = levels.associateBy { it.id }
    val fp = fines.associateBy { it.id }
    val parts = mutableListOf<String>()
    if (f.action.isNotBlank()) parts += f.action
    f.institution?.let { parts += "기관 " + FindingStore.levelText(lv[it.profileId], it).first }
    FindingStore.fineAmount(fp[f.penalty.profileId], f.penalty)?.let { parts += "과징금 " + SanctionCalc.won(it) }
    FindingStore.fineAmount(fp[f.fine.profileId], f.fine)?.let { parts += "과태료 " + SanctionCalc.won(it) }
    if (f.actors.isNotEmpty()) parts += "관련자 ${f.actors.size}명"
    return parts.joinToString(" · ").ifBlank { "내용을 넣으세요" }
}

/** 지적사항을 고르지 않았을 때: 조치 명세 미리 보기 */
@Composable
private fun ActionTable(list: List<Finding>, sanction: SanctionStore, modifier: Modifier) {
    val rows = FindingStore.rows(list, sanction)
    AppCard(modifier) {
        CardTitle("조치 명세 미리 보기") { Pill("${rows.size}줄") }
        if (rows.isEmpty()) { InfoBox("왼쪽에서 지적사항을 추가하고 고르면 내용을 채울 수 있습니다."); return@AppCard }
        val head = listOf("번호" to 0.5f, "지적사항" to 2f, "대상" to 1.4f, "구분" to 0.8f, "제재" to 1f, "사전통지 표기" to 1.4f, "과징금" to 1f, "과태료" to 1f)
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(6.dp)) {
            head.forEach { (h, w) -> Text(h, Modifier.weight(w), style = MaterialTheme.typography.labelMedium) }
        }
        LazyColumn {
            itemsIndexed(rows) { _, r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp)) {
                    listOf(r.findingNo.toString(), r.finding, r.target, r.role, r.sanction, r.range, r.penalty, r.fine).zip(head).forEach { (v, h) ->
                        Text(v, Modifier.weight(h.second), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Text("※ 계산 결과는 참고값입니다. 기준 수치는 양정 기준·계산기 메뉴에서 근거 규정 별표에 맞춰 고칠 수 있습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingEditor(
    store: FindingStore, task: WorkTask, f: Finding, levels: List<LevelProfile>, fines: List<FineProfile>, actions: CaseActions,
    onClose: () -> Unit, modifier: Modifier,
) {
    fun save(n: Finding) = store.upsert(task.id, n)
    val instProfile = levels.firstOrNull { it.id == "lv-inst" } ?: levels.firstOrNull { it.target == "기관" }
    AppCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("지적사항", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { store.move(task.id, f.id, -1) }) { Icon(Icons.Outlined.ArrowUpward, "위로") }
            IconButton(onClick = { store.move(task.id, f.id, 1) }) { Icon(Icons.Outlined.ArrowDownward, "아래로") }
            IconButton(onClick = { store.delete(task.id, f.id); onClose() }) { Icon(Icons.Outlined.DeleteOutline, "지적사항 삭제", tint = MaterialTheme.colorScheme.error) }
            TextButton(onClick = onClose) { Text("조치 명세 보기") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LocalField("f-title-${f.id}", f.title, Modifier.fillMaxWidth(), "제목(예: 투자권유 시 설명의무 위반)", true) { save(f.copy(title = it)) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LocalField("f-laws-${f.id}", f.laws, Modifier.weight(1f), "위반 법규(예: 「자본시장법」 제47조)", true) { save(f.copy(laws = it)) }
                if (f.laws.isNotBlank()) TextButton(onClick = { actions.openLaw(f.laws.substringBefore(',').replace("「", "").replace("」", "").trim()) }) {
                    Icon(Icons.Outlined.ManageSearch, null, Modifier.size(16.dp)); Text(" 법률 도우미")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("조치 구분", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
                ACTIONS.forEach { a -> FilterChip(f.action == a, { save(f.copy(action = if (f.action == a) "" else a)) }, label = { Text(a) }) }
            }
            LocalField("f-facts-${f.id}", f.facts, Modifier.fillMaxWidth().heightIn(min = 90.dp), "위반 내용·사실관계", false) { save(f.copy(facts = it)) }

            HorizontalDivider()
            // ── 기관
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("기관 제재", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(f.institution != null, { on -> save(f.copy(institution = if (on) LevelPick(instProfile?.id ?: "lv-inst", baseIndex = ((instProfile?.levels?.size ?: 2) - 1).coerceAtLeast(0)) else null)) })
            }
            if (f.institution != null && instProfile != null) LevelPickEditor(instProfile, f.institution) { save(f.copy(institution = it)) }

            HorizontalDivider()
            FineSection("과징금 (기관)", "과징금", fines, f.penalty) { save(f.copy(penalty = it)) }
            HorizontalDivider()
            FineSection("과태료 (기관)", "과태료", fines, f.fine) { save(f.copy(fine = it)) }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("관련 임직원 ${f.actors.size}명", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { save(f.copy(actors = f.actors + Actor())) }) { Icon(Icons.Outlined.PersonAdd, null, Modifier.size(16.dp)); Text(" 관련자 추가") }
            }
            if (f.actors.isEmpty()) InfoBox("행위자·보조자·지시·감독자를 사람마다 넣으면 각자의 양정(임원·직원 기준)과 개인 과태료를 따로 계산합니다.")
            f.actors.forEach { a ->
                ActorCard(a, levels, fines,
                    onChange = { n -> save(f.copy(actors = f.actors.map { if (it.id == a.id) n else it })) },
                    onDelete = { save(f.copy(actors = f.actors.filter { it.id != a.id })) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActorCard(a: Actor, levels: List<LevelProfile>, fines: List<FineProfile>, onChange: (Actor) -> Unit, onDelete: () -> Unit) {
    var roleOpen by remember { mutableStateOf(false) }
    val profile = levels.firstOrNull { it.id == a.level.profileId } ?: levels.firstOrNull { it.id == a.kind.profileId }
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ActorKind.entries.forEach { k ->
                FilterChip(a.kind == k, {
                    if (a.kind != k) {
                        val p = levels.firstOrNull { it.id == k.profileId }
                        onChange(a.copy(kind = k, level = LevelPick(k.profileId, baseIndex = ((p?.levels?.size ?: 2) / 2))))
                    }
                }, label = { Text(k.label) })
            }
            LocalField("a-pos-${a.id}", a.position, Modifier.width(120.dp), "직위", true) { onChange(a.copy(position = it)) }
            LocalField("a-name-${a.id}", a.name, Modifier.width(110.dp), "성명", true) { onChange(a.copy(name = it)) }
            Box {
                OutlinedButton(onClick = { roleOpen = true }) { Text(a.role.label) }
                DropdownMenu(roleOpen, { roleOpen = false }) {
                    ActorRole.entries.forEach { r -> DropdownMenuItem(text = { Text(r.label) }, onClick = { roleOpen = false; onChange(a.copy(role = r)) }) }
                }
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "관련자 삭제") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LocalField("a-duty-${a.id}", a.duty, Modifier.weight(1f), "담당 업무·재직 기간", true) { onChange(a.copy(duty = it)) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChange(a.copy(retired = !a.retired)) }) {
                Checkbox(a.retired, { onChange(a.copy(retired = it)) })
                Text("퇴직자('상당' 표기)", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (profile != null) LevelPickEditor(profile, a.level) { onChange(a.copy(level = it)) }
        FineSection("개인 과태료", "과태료", fines, a.fine) { onChange(a.copy(fine = it)) }
        LocalField("a-note-${a.id}", a.note, Modifier.fillMaxWidth(), "비고(가중·감경 판단 근거 등)", true) { onChange(a.copy(note = it)) }
    }
}

/** 신분 제재: 기본 양정 + 가중·감경 → 결과와 사전통지 표기 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelPickEditor(p: LevelProfile, pick: LevelPick, onChange: (LevelPick) -> Unit) {
    val r = SanctionCalc.level(p, pick.baseIndex, pick.aggravations, pick.mitigations)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("기본 양정", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
            p.levels.forEachIndexed { i, l -> FilterChip(pick.baseIndex == i, { onChange(pick.copy(baseIndex = i)) }, label = { Text(l) }) }
        }
        FactorChips("가중", p.aggravations, pick.aggravations, { "+${it.steps}" }) { onChange(pick.copy(aggravations = it)) }
        FactorChips("감경", p.mitigations, pick.mitigations, { "-${it.steps}" }) { onChange(pick.copy(mitigations = it)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("산정", style = MaterialTheme.typography.labelMedium)
            Pill(r.final, Tone.ACCENT)
            Text("사전통지 표기: ${r.range}", style = MaterialTheme.typography.bodySmall)
            if (r.clipped) Pill("범위 끝", Tone.WARN)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FactorChips(title: String, factors: List<Factor>, picked: Set<String>, amount: (Factor) -> String, onChange: (Set<String>) -> Unit) {
    if (factors.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
        factors.forEach { f ->
            FilterChip(f.id in picked, { onChange(if (f.id in picked) picked - f.id else picked + f.id) }, label = { Text("${f.label} ${amount(f)}", style = MaterialTheme.typography.labelSmall) })
        }
    }
}

/** 과징금·과태료 한 칸: 켜기 → 기준 고르기 → 금액·위반 정도·가중·감경 → 결과(또는 확정 금액 직접 입력) */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FineSection(title: String, kind: String, all: List<FineProfile>, pick: FinePick, onChange: (FinePick) -> Unit) {
    val profiles = all.filter { it.kind == kind }.ifEmpty { all }
    val p = profiles.firstOrNull { it.id == pick.profileId } ?: profiles.firstOrNull()
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (pick.enabled) {
                val amount = FindingStore.fineAmount(p, pick)
                if (amount != null) Pill(SanctionCalc.won(amount), Tone.ACCENT)
            }
            Switch(pick.enabled, { onChange(pick.copy(enabled = it, profileId = pick.profileId.ifBlank { p?.id.orEmpty() })) }, modifier = Modifier.padding(start = 8.dp))
        }
        if (!pick.enabled || p == null) return@Column
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                OutlinedButton(onClick = { open = true }) { Text(p.name, maxLines = 1) }
                DropdownMenu(open, { open = false }) {
                    profiles.forEach { pr -> DropdownMenuItem(text = { Text(pr.name) }, onClick = { open = false; onChange(pick.copy(profileId = pr.id, gradeIndex = 0, aggravations = emptySet(), mitigations = emptySet())) }) }
                }
            }
            MoneyField("${title}-v-${p.id}", pick.violationAmount, "위반 관련 금액(원)", Modifier.weight(1f)) { onChange(pick.copy(violationAmount = it)) }
            MoneyField("${title}-b-${p.id}", pick.directBase, "기준금액 직접(원)", Modifier.weight(1f)) { onChange(pick.copy(directBase = it)) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("위반 정도", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp, end = 4.dp))
            p.grades.forEachIndexed { i, g -> FilterChip(pick.gradeIndex == i, { onChange(pick.copy(gradeIndex = i)) }, label = { Text("${g.label} ${SanctionCalc.pct(g.percent)}") }) }
        }
        FactorChips("가중", p.aggravations, pick.aggravations, { "+" + SanctionCalc.pct(it.percent) }) { onChange(pick.copy(aggravations = it)) }
        FactorChips("감경", p.mitigations, pick.mitigations, { "-" + SanctionCalc.pct(it.percent) }) { onChange(pick.copy(mitigations = it)) }
        val r = SanctionCalc.fine(p, pick.input())
        Text(r.lines.joinToString("  ·  ") { "${it.first} ${it.second}" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyField("${title}-fixed-${p.id}", pick.fixed, "확정 금액 직접 입력(원) · 넣으면 계산값 대신 씀", Modifier.fillMaxWidth()) { onChange(pick.copy(fixed = it)) }
    }
}

@Composable
private fun MoneyField(key: String, value: Long?, label: String, modifier: Modifier, onValue: (Long?) -> Unit) {
    var text by remember(key) { mutableStateOf(value?.toString().orEmpty()) }
    androidx.compose.material3.OutlinedTextField(
        text, { v -> text = v.filter(Char::isDigit).take(15); onValue(text.toLongOrNull()) }, modifier,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium,
        supportingText = text.toLongOrNull()?.let { { Text(SanctionCalc.won(it)) } },
    )
}
