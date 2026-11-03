package io.github.hdlee73.fsssupporter.inspection

import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.HwpText
import io.github.hdlee73.fsssupporter.data.JsonFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

/** 작업(검사) 하나의 진행 기록 */
@Serializable
data class InspectionProgress(
    val taskId: String,
    /** 정기검사(경영실태평가 포함)면 true, 수시검사면 false */
    val regular: Boolean = false,
    /** 현장검사 종료일(yyyy-MM-dd). 착수일은 작업의 시작일을 쓴다. */
    val fieldEnd: String = "",
    val done: Set<String> = emptySet(),
    val skipped: Set<String> = emptySet(),
    /** "단계id#번호" */
    val checks: Set<String> = emptySet(),
    val notes: Map<String, String> = emptyMap(),
) {
    fun steps(): List<Step> = InspectionGuide.steps(regular).filter { it.id !in skipped }
    fun ratio(): Float = steps().let { s -> if (s.isEmpty()) 0f else s.count { it.id in done }.toFloat() / s.size }
    /** 아직 끝내지 않은 첫 단계 */
    fun current(): Step? = steps().firstOrNull { it.id !in done }
    fun stageOf(step: Step): Stage? = InspectionGuide.STAGES.firstOrNull { st -> st.steps.any { it.id == step.id } }
}

@Serializable
data class InspectionData(val items: Map<String, InspectionProgress> = emptyMap())

/** 가져온 매뉴얼·서식 원문(이 PC에만 저장) */
@Serializable
data class GuideLibraryData(
    val manual: Map<String, String> = emptyMap(),
    val forms: Map<String, String> = emptyMap(),
    val manualFile: String = "",
    val formsFile: String = "",
    val importedAt: Long = 0,
)

class InspectionStore(dir: File = AppDirs.root) {
    private val store = JsonFile(File(dir, "inspections.json"), InspectionData.serializer()) { InspectionData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<InspectionData> = _data.asStateFlow()

    private val libStore = JsonFile(File(dir, "guide-library.json"), GuideLibraryData.serializer()) { GuideLibraryData() }
    private val _library = MutableStateFlow(libStore.read())
    val library: StateFlow<GuideLibraryData> = _library.asStateFlow()

    fun of(taskId: String): InspectionProgress = _data.value.items[taskId] ?: InspectionProgress(taskId)

    fun update(taskId: String, block: (InspectionProgress) -> InspectionProgress) {
        _data.update { d -> d.copy(items = d.items + (taskId to block(d.items[taskId] ?: InspectionProgress(taskId)))) }
        runCatching { store.write(_data.value) }
    }

    fun remove(taskId: String) {
        _data.update { d -> d.copy(items = d.items - taskId) }
        runCatching { store.write(_data.value) }
    }

    fun toggleDone(taskId: String, stepId: String) = update(taskId) { p -> p.copy(done = if (stepId in p.done) p.done - stepId else p.done + stepId) }
    fun toggleSkip(taskId: String, stepId: String) = update(taskId) { p -> p.copy(skipped = if (stepId in p.skipped) p.skipped - stepId else p.skipped + stepId, done = p.done - stepId) }
    fun toggleCheck(taskId: String, stepId: String, index: Int) = update(taskId) { p ->
        val key = "$stepId#$index"
        val checks = if (key in p.checks) p.checks - key else p.checks + key
        // 점검 항목을 모두 끝내면 단계도 끝난 것으로 본다.
        val step = InspectionGuide.ALL_STEPS.firstOrNull { it.id == stepId }
        val all = step != null && step.checks.indices.all { "$stepId#$it" in checks }
        p.copy(checks = checks, done = if (all) p.done + stepId else if (key in p.checks) p.done - stepId else p.done)
    }
    fun setNote(taskId: String, stepId: String, text: String) = update(taskId) { p -> p.copy(notes = if (text.isBlank()) p.notes - stepId else p.notes + (stepId to text)) }

    // ---------- 매뉴얼·서식 가져오기 ----------

    suspend fun importManual(file: File): Int = withContext(Dispatchers.IO) {
        val sections = splitManual(HwpText.extract(file))
        if (sections.size < 10) throw IllegalArgumentException("검사품질관리 매뉴얼의 절을 찾지 못했습니다(찾은 절 ${sections.size}개).")
        saveLibrary { it.copy(manual = sections, manualFile = file.name, importedAt = System.currentTimeMillis()) }
        sections.size
    }

    suspend fun importForms(file: File): Int = withContext(Dispatchers.IO) {
        val forms = splitForms(HwpText.extract(file))
        if (forms.isEmpty()) throw IllegalArgumentException("'< 제0-0호 서식 >' 표시가 있는 서식 문서가 아닙니다.")
        saveLibrary { it.copy(forms = forms, formsFile = file.name, importedAt = System.currentTimeMillis()) }
        forms.size
    }

    fun clearLibrary() = saveLibrary { GuideLibraryData() }

    private fun saveLibrary(block: (GuideLibraryData) -> GuideLibraryData) {
        _library.update(block)
        runCatching { libStore.write(_library.value) }
    }

    /** 단계에 해당하는 매뉴얼 원문(가져온 경우) */
    fun manualFor(step: Step, lib: GuideLibraryData = _library.value): List<Pair<String, String>> {
        return step.manual.distinctBy(::norm).mapNotNull { t -> findManual(t, lib)?.let { t to it } }
    }

    /** 제목이 같거나 제목으로 시작하는 절(예: "…실무지침(제정 2015.5.11.)") */
    fun findManual(title: String, lib: GuideLibraryData = _library.value): String? {
        val n = norm(title)
        return lib.manual.entries.firstOrNull { norm(it.key) == n }?.value
            ?: lib.manual.entries.firstOrNull { norm(it.key).startsWith(n) }?.value
    }

    companion object {
        fun norm(s: String) = s.filter { it.isLetterOrDigit() }

        private val SECTION_NO = Regex("""^\s*(0[1-9]|1[0-9]|2[0-9])\s*$""")

        /** "01 ↵ 제목 ↵ 본문…" 꼴로 이어지는 매뉴얼 본문을 절 제목별로 나눈다. 같은 제목이 다시 나오면 이어 붙인다. */
        fun splitManual(text: String): Map<String, String> {
            val lines = text.lines()
            val out = LinkedHashMap<String, StringBuilder>()
            var current: StringBuilder? = null
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (SECTION_NO.matches(line)) {
                    var j = i + 1
                    while (j < lines.size && lines[j].isBlank()) j++
                    val title = lines.getOrNull(j)?.trim().orEmpty()
                    if (title.length in 2..40 && !title.startsWith("□") && !title.startsWith("◦")) {
                        current = out.getOrPut(title) { StringBuilder() }.also { if (it.isNotEmpty()) it.append("\n\n") }
                        i = j + 1
                        continue
                    }
                }
                // 장 머리(제N장 …)와 쪽 머리는 본문에 넣지 않는다.
                if (!Regex("""^\s*제\d+장(\s.*)?$""").matches(line)) current?.append(line)?.append('\n')
                i++
            }
            return out.mapValues { (_, v) -> v.toString().replace(Regex("""\n{3,}"""), "\n\n").trim() }.filterValues { it.length > 20 }
        }

        private val FORM_MARK = Regex("""<\s*제\s*(\d+)\s*-\s*(\d+)\s*호\s*서식\s*>""")

        /** "< 제2-4호 서식 >" 표시로 서식 문서를 나눈다. */
        fun splitForms(text: String): Map<String, String> {
            val marks = FORM_MARK.findAll(text).toList()
            val out = LinkedHashMap<String, StringBuilder>()
            marks.forEachIndexed { k, m ->
                val no = "${m.groupValues[1]}-${m.groupValues[2]}"
                val end = marks.getOrNull(k + 1)?.range?.first ?: text.length
                val body = text.substring(m.range.last + 1, end).replace(Regex("""[ \t]+\n"""), "\n").replace(Regex("""\n{3,}"""), "\n\n").trim()
                out.getOrPut(no) { StringBuilder() }.also { if (it.isNotEmpty()) it.append("\n\n") }.append(body)
            }
            return out.mapValues { it.value.toString() }.filterValues { it.isNotBlank() }
        }

        fun dueOf(step: Step, p: InspectionProgress, start: LocalDate?): LocalDate? =
            step.due?.due(p.regular, start, io.github.hdlee73.fsssupporter.tasks.parseDate(p.fieldEnd))
    }
}
