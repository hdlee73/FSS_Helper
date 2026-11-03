package io.github.hdlee73.fsssupporter.sanction

import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.JsonFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.io.OutputStream
import java.text.DecimalFormat
import java.util.UUID

/** 가중·감경 사유 한 줄. 금전 제재는 [percent](%), 신분 제재는 [steps](단계)로 쓴다. */
@Serializable
data class Factor(val id: String = newKey(), val label: String, val percent: Double = 0.0, val steps: Int = 1)

/** 위반 정도(중대성) 구분과 기준금액 대비 비율(%) */
@Serializable
data class Grade(val label: String, val percent: Double)

/**
 * 과징금·과태료 산정 기준 묶음. 숫자는 모두 사용자가 고칠 수 있다.
 * 기본 제공 기준은 '계산 틀' 예시이고, 실제 비율은 근거 규정 별표를 보고 맞춰야 한다.
 */
@Serializable
data class FineProfile(
    val id: String = newKey(),
    val name: String,
    /** 과징금 / 과태료 */
    val kind: String = "과징금",
    /** 근거 규정(법령검색으로 연다) */
    val basis: String = "",
    /** 법정 최고한도(원). 0이면 한도 없음 */
    val statutoryMax: Long = 0,
    val grades: List<Grade> = emptyList(),
    val aggravations: List<Factor> = emptyList(),
    val mitigations: List<Factor> = emptyList(),
    /** 가중 합계 상한(%) */
    val aggravationCap: Double = 50.0,
    /** 감경 합계 상한(%) */
    val mitigationCap: Double = 50.0,
    /** 끝수 버림 단위(원) */
    val roundUnit: Long = 10_000,
    val builtIn: Boolean = false,
)

/** 신분 제재 구분(임원·직원·기관)과 무거운 순서의 양정 단계 */
@Serializable
data class LevelProfile(
    val id: String = newKey(),
    val name: String,
    val target: String,
    /** 무거운 것부터 */
    val levels: List<String>,
    val basis: String = "",
    val aggravations: List<Factor> = emptyList(),
    val mitigations: List<Factor> = emptyList(),
    val aggravationCapSteps: Int = 1,
    val mitigationCapSteps: Int = 2,
    val builtIn: Boolean = false,
)

@Serializable
data class SanctionData(val fines: List<FineProfile> = emptyList(), val levels: List<LevelProfile> = emptyList())

fun newKey(): String = UUID.randomUUID().toString().substring(0, 8)

/** 금전 제재 계산 입력 */
data class FineInput(
    /** 직접 입력한 기준금액(원). null이면 법정 최고한도 × 위반 정도 비율 */
    val directBase: Long? = null,
    /** 위반 관련 금액(원). 있으면 위반 관련 금액 × 위반 정도 비율이 기준금액이 된다 */
    val violationAmount: Long? = null,
    val gradeIndex: Int = 0,
    val aggravations: Set<String> = emptySet(),
    val mitigations: Set<String> = emptySet(),
)

data class FineResult(
    val base: Long,
    val baseNote: String,
    val aggravationPercent: Double,
    val aggravationCapped: Boolean,
    val afterAggravation: Long,
    val mitigationPercent: Double,
    val mitigationCapped: Boolean,
    val afterMitigation: Long,
    val limited: Boolean,
    val final: Long,
    val lines: List<Pair<String, String>>,
)

data class LevelResult(
    val baseLevel: String,
    val aggravationSteps: Int,
    val mitigationSteps: Int,
    val net: Int,
    val final: String,
    val clipped: Boolean,
    val lines: List<Pair<String, String>>,
)

object SanctionCalc {
    private val won = DecimalFormat("#,##0")
    fun won(v: Long): String = won.format(v) + "원"
    fun pct(v: Double): String = if (v == Math.floor(v)) "${v.toLong()}%" else "${"%.1f".format(v)}%"

    /** 기준금액에 가중을 먼저, 감경을 나중에 차례로 적용하고, 법정 한도를 넘지 않게 자른 뒤 끝수를 버린다. */
    fun fine(p: FineProfile, input: FineInput): FineResult {
        val grade = p.grades.getOrNull(input.gradeIndex)
        val (base, note) = when {
            input.directBase != null -> input.directBase to "직접 입력"
            input.violationAmount != null && grade != null -> Math.round(input.violationAmount * grade.percent / 100.0) to "위반 관련 금액 ${won(input.violationAmount)} × ${grade.label} ${pct(grade.percent)}"
            grade != null && p.statutoryMax > 0 -> Math.round(p.statutoryMax * grade.percent / 100.0) to "법정 최고한도 ${won(p.statutoryMax)} × ${grade.label} ${pct(grade.percent)}"
            else -> 0L to "기준금액을 입력하세요"
        }
        val aggSum = p.aggravations.filter { it.id in input.aggravations }.sumOf { it.percent }
        val agg = minOf(aggSum, p.aggravationCap)
        val afterAgg = Math.round(base * (1 + agg / 100.0))
        val mitSum = p.mitigations.filter { it.id in input.mitigations }.sumOf { it.percent }
        val mit = minOf(mitSum, p.mitigationCap)
        val afterMit = Math.round(afterAgg * (1 - mit / 100.0)).coerceAtLeast(0)
        val limited = p.statutoryMax in 1 until afterMit
        val capped = if (limited) p.statutoryMax else afterMit
        val unit = p.roundUnit.coerceAtLeast(1)
        val final = capped / unit * unit
        val lines = buildList {
            add("기준금액" to "${won(base)} ($note)")
            add("가중" to "+${pct(agg)}" + (if (aggSum > agg) " (합계 ${pct(aggSum)} → 상한 ${pct(p.aggravationCap)})" else "") + " → ${won(afterAgg)}")
            add("감경" to "-${pct(mit)}" + (if (mitSum > mit) " (합계 ${pct(mitSum)} → 상한 ${pct(p.mitigationCap)})" else "") + " → ${won(afterMit)}")
            if (limited) add("법정 한도" to "${won(p.statutoryMax)}로 제한")
            add("끝수" to "${won(unit)} 미만 버림")
            add("산정 결과" to won(final))
        }
        return FineResult(base, note, agg, aggSum > agg, afterAgg, mit, mitSum > mit, afterMit, limited, final, lines)
    }

    /** 기본 양정에서 가중(무겁게)·감경(가볍게) 단계를 상한 안에서 더하고 뺀다. */
    fun level(p: LevelProfile, baseIndex: Int, aggravations: Set<String>, mitigations: Set<String>): LevelResult {
        val i = baseIndex.coerceIn(0, (p.levels.size - 1).coerceAtLeast(0))
        val aggSum = p.aggravations.filter { it.id in aggravations }.sumOf { it.steps }
        val mitSum = p.mitigations.filter { it.id in mitigations }.sumOf { it.steps }
        val agg = minOf(aggSum, p.aggravationCapSteps)
        val mit = minOf(mitSum, p.mitigationCapSteps)
        val net = mit - agg
        val target = i + net
        val clipped = target < 0 || target >= p.levels.size
        val fi = target.coerceIn(0, p.levels.size - 1)
        val lines = buildList {
            add("기본 양정" to p.levels.getOrElse(i) { "-" })
            add("가중" to "${agg}단계" + if (aggSum > agg) " (합계 ${aggSum}단계 → 상한 ${p.aggravationCapSteps}단계)" else "")
            add("감경" to "${mit}단계" + if (mitSum > mit) " (합계 ${mitSum}단계 → 상한 ${p.mitigationCapSteps}단계)" else "")
            if (clipped) add("범위" to if (target < 0) "가장 무거운 단계에서 멈춤" else "가장 가벼운 단계에서 멈춤(불문 여부는 별도 판단)")
            add("산정 결과" to p.levels.getOrElse(fi) { "-" })
        }
        return LevelResult(p.levels.getOrElse(i) { "-" }, agg, mit, net, p.levels.getOrElse(fi) { "-" }, clipped, lines)
    }

    /** 조치안에 붙일 글 */
    fun summary(title: String, profileName: String, basis: String, lines: List<Pair<String, String>>, reasons: List<String>): String = buildString {
        appendLine("■ $title ($profileName)")
        lines.forEach { (k, v) -> appendLine("  - $k : $v") }
        if (reasons.isNotEmpty()) appendLine("  - 반영 사유 : " + reasons.joinToString(", "))
        if (basis.isNotBlank()) appendLine("  - 근거 : 「$basis」")
        append("  ※ 산정 기준 수치는 근거 규정 별표와 대조해 확인할 것")
    }

    /** 계산 결과 한 장짜리 엑셀 */
    fun writeXlsx(title: String, profileName: String, basis: String, lines: List<Pair<String, String>>, reasons: List<String>, out: OutputStream) {
        val wb = org.dhatim.fastexcel.Workbook(out, "FSS_Supporter", "1.0")
        val ws = wb.newWorksheet("양정")
        ws.width(0, 18.0)
        ws.width(1, 70.0)
        ws.value(0, 0, title)
        ws.style(0, 0).bold().fontSize(14).set()
        ws.value(1, 0, "기준")
        ws.value(1, 1, profileName)
        var r = 2
        if (basis.isNotBlank()) { ws.value(r, 0, "근거"); ws.value(r, 1, "「$basis」"); r++ }
        r++
        ws.value(r, 0, "항목"); ws.value(r, 1, "내용")
        ws.range(r, 0, r, 1).style().bold().fillColor("E6F4EC").set()
        r++
        lines.forEach { (k, v) -> ws.value(r, 0, k); ws.value(r, 1, v); r++ }
        if (reasons.isNotEmpty()) { ws.value(r, 0, "반영 사유"); ws.value(r, 1, reasons.joinToString(", ")); r++ }
        r++
        ws.value(r, 0, "※ 산정 기준 수치는 근거 규정 별표와 대조해 확인할 것")
        ws.value(r + 1, 0, "작성: FSS_Supporter " + java.time.LocalDate.now())
        wb.finish()
    }
}

/** 양정 기준 저장소. 기본 기준에 사용자가 만든 기준을 더한다. */
class SanctionStore(file: File = File(AppDirs.root, "sanction.json")) {
    private val store = JsonFile(file, SanctionData.serializer()) { SanctionData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<SanctionData> = _data.asStateFlow()

    fun fines(d: SanctionData = _data.value): List<FineProfile> = BUILT_IN_FINES.filter { b -> d.fines.none { it.id == b.id } } + d.fines
    fun levels(d: SanctionData = _data.value): List<LevelProfile> = BUILT_IN_LEVELS.filter { b -> d.levels.none { it.id == b.id } } + d.levels

    private fun save(block: (SanctionData) -> SanctionData) {
        _data.update(block)
        runCatching { store.write(_data.value) }
    }

    /** 기본 기준을 고치면 같은 id로 덮어쓴 사본이 저장된다. */
    fun saveFine(p: FineProfile) = save { d -> d.copy(fines = d.fines.filter { it.id != p.id } + p.copy(builtIn = false)) }
    fun saveLevel(p: LevelProfile) = save { d -> d.copy(levels = d.levels.filter { it.id != p.id } + p.copy(builtIn = false)) }
    fun deleteFine(id: String) = save { d -> d.copy(fines = d.fines.filter { it.id != id }) }
    fun deleteLevel(id: String) = save { d -> d.copy(levels = d.levels.filter { it.id != id }) }
    fun isOverridden(id: String) = _data.value.fines.any { it.id == id } || _data.value.levels.any { it.id == id }

    companion object {
        const val RULE = "금융기관 검사 및 제재에 관한 규정"
        const val RULE_DETAIL = "금융기관 검사 및 제재에 관한 규정 시행세칙"

        private val COMMON_AGG = listOf(
            Factor("agg-repeat", "최근 동일·유사 위반으로 제재받은 이력", 30.0, 1),
            Factor("agg-conceal", "위반 사실 은폐·축소, 검사 방해", 30.0, 1),
            Factor("agg-scale", "위반 규모가 크거나 피해가 넓음", 20.0, 1),
        )
        private val COMMON_MIT = listOf(
            Factor("mit-self", "자진 신고 또는 검사 전 자체 시정", 30.0, 1),
            Factor("mit-coop", "검사에 적극 협조", 20.0, 1),
            Factor("mit-repair", "피해 회복(배상) 완료", 20.0, 1),
            Factor("mit-control", "내부통제는 작동했으나 개인 일탈", 10.0, 1),
        )

        val BUILT_IN_FINES = listOf(
            FineProfile(
                id = "fine-penalty", name = "과징금 (계산 틀 예시)", kind = "과징금", basis = RULE_DETAIL,
                grades = listOf(Grade("매우 중대", 100.0), Grade("중대", 75.0), Grade("일반", 50.0), Grade("경미", 25.0)),
                aggravations = COMMON_AGG, mitigations = COMMON_MIT, aggravationCap = 50.0, mitigationCap = 50.0, builtIn = true,
            ),
            FineProfile(
                id = "fine-fine", name = "과태료 (계산 틀 예시)", kind = "과태료", basis = RULE_DETAIL,
                grades = listOf(Grade("중대", 100.0), Grade("일반", 60.0), Grade("경미", 30.0)),
                aggravations = COMMON_AGG.take(2), mitigations = COMMON_MIT, aggravationCap = 50.0, mitigationCap = 50.0, builtIn = true,
            ),
        )

        val BUILT_IN_LEVELS = listOf(
            LevelProfile("lv-exec", "임원 제재 (예시)", "임원", listOf("해임권고", "직무정지", "문책경고", "주의적경고", "주의"), RULE, COMMON_AGG, COMMON_MIT, builtIn = true),
            LevelProfile("lv-staff", "직원 제재 (예시)", "직원", listOf("면직", "정직", "감봉", "견책", "주의"), RULE, COMMON_AGG, COMMON_MIT, builtIn = true),
            LevelProfile("lv-inst", "기관 제재 (예시)", "기관", listOf("영업정지", "기관경고", "기관주의"), RULE, COMMON_AGG, COMMON_MIT, builtIn = true),
        )
    }
}
