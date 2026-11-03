package io.github.hdlee73.fsssupporter.cases

import com.hdlee73.everylaw.core.domain.HwpxExporter
import com.hdlee73.everylaw.core.domain.HwpxParagraph
import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.JsonFile
import io.github.hdlee73.fsssupporter.sanction.FineInput
import io.github.hdlee73.fsssupporter.sanction.FineProfile
import io.github.hdlee73.fsssupporter.sanction.LevelProfile
import io.github.hdlee73.fsssupporter.sanction.SanctionCalc
import io.github.hdlee73.fsssupporter.sanction.SanctionStore
import io.github.hdlee73.fsssupporter.tasks.WorkTask
import io.github.hdlee73.fsssupporter.tasks.newId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import java.io.File
import java.io.OutputStream
import java.time.LocalDate

/** 관련자 구분 */
@Serializable
enum class ActorRole(val label: String) { ACTOR("행위자"), ASSIST("보조자"), ORDER("지시·감독자"), SUPERVISOR("감독자"), ETC("기타") }

/** 신분 구분: 임원·직원 제재 기준이 다르다 */
@Serializable
enum class ActorKind(val label: String, val profileId: String) { EXEC("임원", "lv-exec"), STAFF("직원", "lv-staff") }

/** 신분(임원·직원·기관) 제재 양정 입력 */
@Serializable
data class LevelPick(
    val profileId: String,
    val baseIndex: Int = 0,
    val aggravations: Set<String> = emptySet(),
    val mitigations: Set<String> = emptySet(),
)

/** 과징금 또는 과태료 산정 입력. [enabled]가 false면 해당 없음 */
@Serializable
data class FinePick(
    val enabled: Boolean = false,
    val profileId: String = "",
    /** 직접 넣은 기준금액(원) */
    val directBase: Long? = null,
    /** 위반 관련 금액(원) */
    val violationAmount: Long? = null,
    val gradeIndex: Int = 0,
    val aggravations: Set<String> = emptySet(),
    val mitigations: Set<String> = emptySet(),
    /** 계산 대신 확정 금액을 직접 쓸 때(원) */
    val fixed: Long? = null,
) {
    fun input() = FineInput(directBase, violationAmount, gradeIndex, aggravations, mitigations)
}

/** 지적사항의 관련 임직원 한 사람 */
@Serializable
data class Actor(
    val id: String = newId(),
    val kind: ActorKind = ActorKind.STAFF,
    val name: String = "",
    val position: String = "",
    val role: ActorRole = ActorRole.ACTOR,
    /** 담당 업무·재직 기간 등 */
    val duty: String = "",
    /** 퇴직자면 '상당' 표기(위법·부당사항 통보) */
    val retired: Boolean = false,
    val level: LevelPick = LevelPick(kind.profileId),
    /** 개인에게 매기는 과태료 */
    val fine: FinePick = FinePick(profileId = "fine-fine"),
    val note: String = "",
)

/** 검사건의 지적사항 한 건 */
@Serializable
data class Finding(
    val id: String = newId(),
    val title: String = "새 지적사항",
    /** 위반 법규(예: 「자본시장법」 제54조) */
    val laws: String = "",
    /** 위반 내용·사실관계 요약 */
    val facts: String = "",
    /** 조치 구분: 문책, 주의, 자율처리필요, 경영유의, 개선 등 */
    val action: String = "",
    /** 기관 제재. null이면 없음 */
    val institution: LevelPick? = null,
    /** 기관 과징금 */
    val penalty: FinePick = FinePick(profileId = "fine-penalty"),
    /** 기관 과태료 */
    val fine: FinePick = FinePick(profileId = "fine-fine"),
    val actors: List<Actor> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class FindingData(val byCase: Map<String, List<Finding>> = emptyMap())

/** 조치 명세 한 줄 */
data class ActionRow(val findingNo: Int, val finding: String, val target: String, val role: String, val sanction: String, val range: String, val penalty: String, val fine: String)

/** 검사건별 지적사항·관련자 저장소 */
class FindingStore(file: File = File(AppDirs.root, "findings.json")) {
    private val store = JsonFile(file, FindingData.serializer()) { FindingData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<FindingData> = _data.asStateFlow()

    fun of(caseId: String): List<Finding> = _data.value.byCase[caseId].orEmpty()

    private fun save(caseId: String, block: (List<Finding>) -> List<Finding>) {
        _data.update { d -> d.copy(byCase = d.byCase + (caseId to block(d.byCase[caseId].orEmpty()))) }
        runCatching { store.write(_data.value) }
    }

    fun upsert(caseId: String, f: Finding) = save(caseId) { list -> if (list.any { it.id == f.id }) list.map { if (it.id == f.id) f else it } else list + f }
    fun delete(caseId: String, id: String) = save(caseId) { list -> list.filter { it.id != id } }
    fun move(caseId: String, id: String, delta: Int) = save(caseId) { list ->
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) list else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    fun removeCase(caseId: String) {
        _data.update { d -> d.copy(byCase = d.byCase - caseId) }
        runCatching { store.write(_data.value) }
    }

    companion object {
        fun levelText(p: LevelProfile?, pick: LevelPick?): Pair<String, String> {
            if (p == null || pick == null) return "-" to "-"
            val r = SanctionCalc.level(p, pick.baseIndex, pick.aggravations, pick.mitigations)
            return r.final to r.range
        }

        fun fineAmount(p: FineProfile?, pick: FinePick): Long? {
            if (!pick.enabled) return null
            pick.fixed?.let { return it }
            return p?.let { SanctionCalc.fine(it, pick.input()).final }
        }

        /** 지적사항·관련자를 조치 명세 표로 편다. */
        fun rows(findings: List<Finding>, sanction: SanctionStore): List<ActionRow> {
            val levels = sanction.levels().associateBy { it.id }
            val fines = sanction.fines().associateBy { it.id }
            fun money(v: Long?) = v?.let(SanctionCalc::won) ?: "-"
            return findings.flatMapIndexed { i, f ->
                val no = i + 1
                buildList {
                    val inst = levelText(f.institution?.let { levels[it.profileId] }, f.institution)
                    val pen = fineAmount(fines[f.penalty.profileId], f.penalty)
                    val fin = fineAmount(fines[f.fine.profileId], f.fine)
                    if (f.institution != null || pen != null || fin != null) {
                        add(ActionRow(no, f.title, "기관", "-", inst.first, inst.second, money(pen), money(fin)))
                    }
                    f.actors.forEach { a ->
                        val (lv, range) = levelText(levels[a.level.profileId], a.level)
                        val who = listOf(a.position, a.name).filter { it.isNotBlank() }.joinToString(" ").ifBlank { a.kind.label }
                        add(ActionRow(no, f.title, "$who(${a.kind.label})", a.role.label, if (a.retired) "$lv 상당" else lv, range, "-", money(fineAmount(fines[a.fine.profileId], a.fine))))
                    }
                    if (isEmpty()) add(ActionRow(no, f.title, "-", "-", f.action.ifBlank { "-" }, "-", "-", "-"))
                }
            }
        }

        private val HEADER = listOf("번호", "지적사항", "대상", "구분", "제재(양정)", "사전통지 표기", "과징금", "과태료")

        private fun cells(r: ActionRow) = listOf(r.findingNo.toString(), r.finding, r.target, r.role, r.sanction, r.range, r.penalty, r.fine)

        /** 조치 명세를 한글(HWPX)로 */
        fun writeHwpx(task: WorkTask, findings: List<Finding>, sanction: SanctionStore, out: OutputStream) {
            val rows = rows(findings, sanction)
            val paras = buildList {
                add(HwpxParagraph("검사건: ${task.title}" + task.target.takeIf { it.isNotBlank() }?.let { " (대상: $it)" }.orEmpty()))
                add(HwpxParagraph("작성일: ${LocalDate.now()}"))
                add(HwpxParagraph(""))
                add(HwpxParagraph("1. 조치 명세", style = HwpxParagraph.Style.SECTION))
                add(HwpxParagraph("", cells = HEADER))
                rows.forEach { add(HwpxParagraph("", cells = cells(it))) }
                add(HwpxParagraph(""))
                add(HwpxParagraph("2. 지적사항별 내용", style = HwpxParagraph.Style.SECTION))
                findings.forEachIndexed { i, f ->
                    add(HwpxParagraph("(${i + 1}) ${f.title}", style = HwpxParagraph.Style.BOLD))
                    if (f.laws.isNotBlank()) add(HwpxParagraph("위반 법규: ${f.laws}"))
                    if (f.action.isNotBlank()) add(HwpxParagraph("조치 구분: ${f.action}"))
                    f.facts.lines().forEach { add(HwpxParagraph(it)) }
                    add(HwpxParagraph(""))
                }
                add(HwpxParagraph("※ 양정·금액은 FSS_Helper 계산 결과이며, 근거 규정 별표와 대조해 확인할 것"))
            }
            HwpxExporter.writeDocument("조치 명세 - ${task.title}", paras, out)
        }

        /** 조치 명세를 엑셀로 */
        fun writeXlsx(task: WorkTask, findings: List<Finding>, sanction: SanctionStore, out: OutputStream) {
            val wb = org.dhatim.fastexcel.Workbook(out, "FSS_Helper", "1.0")
            val ws = wb.newWorksheet("조치 명세")
            listOf(6.0, 34.0, 20.0, 12.0, 16.0, 22.0, 16.0, 16.0).forEachIndexed { i, w -> ws.width(i, w) }
            ws.value(0, 0, "조치 명세 - ${task.title}")
            ws.style(0, 0).bold().fontSize(14).set()
            HEADER.forEachIndexed { i, h -> ws.value(2, i, h) }
            ws.range(2, 0, 2, HEADER.size - 1).style().bold().fillColor("E6F4EC").set()
            rows(findings, sanction).forEachIndexed { r, row -> cells(row).forEachIndexed { c, v -> ws.value(3 + r, c, v) } }
            wb.finish()
        }
    }
}
