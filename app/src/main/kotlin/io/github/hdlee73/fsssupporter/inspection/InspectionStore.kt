package io.github.hdlee73.fsssupporter.inspection

import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.HBlock
import io.github.hdlee73.fsssupporter.data.HPara
import io.github.hdlee73.fsssupporter.data.Hancom
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
    /** 이번 검사에서 생략하는 단계 */
    val skipped: Set<String> = emptySet(),
    /** 다른 팀(부서)이 맡는 단계 → 맡은 곳(비어 있을 수 있음). 진행률에서 뺀다. */
    val others: Map<String, String> = emptyMap(),
    /** "단계id#번호" */
    val checks: Set<String> = emptySet(),
    val notes: Map<String, String> = emptyMap(),
) {
    /** 내가 하는 단계(생략·다른 팀 담당 제외) */
    fun steps(): List<Step> = InspectionGuide.steps(regular).filter { stateOf(it.id).active }
    fun stateOf(stepId: String): StepState = when {
        stepId in skipped -> StepState.SKIPPED
        stepId in others -> StepState.OTHER
        stepId in done -> StepState.DONE
        else -> StepState.TODO
    }
    fun ratio(): Float = steps().let { s -> if (s.isEmpty()) 0f else s.count { it.id in done }.toFloat() / s.size }
    /** 아직 끝내지 않은 첫 단계 */
    fun current(): Step? = steps().firstOrNull { it.id !in done }
    fun stageOf(step: Step): Stage? = InspectionGuide.STAGES.firstOrNull { st -> st.steps.any { it.id == step.id } }
}

/** 단계 상태: 할 일 · 완료 · 생략 · 다른 팀 담당 */
enum class StepState(val label: String, val active: Boolean) {
    TODO("할 일", true), DONE("완료", true), SKIPPED("생략", false), OTHER("다른 팀 담당", false)
}

@Serializable
data class InspectionData(val items: Map<String, InspectionProgress> = emptyMap())

/** 서식 문서 안의 "< 제2-4호 서식 >" 표시 한 개(문서 순서대로) */
@Serializable
data class FormMark(val no: String, val text: String)

/** 가져온 매뉴얼·서식 원문(이 PC에만 저장). 원본 파일 사본도 함께 둔다. */
@Serializable
data class GuideLibraryData(
    /** 절 제목 → 글자(찾기용) */
    val manual: Map<String, String> = emptyMap(),
    /** 서식 번호 → 글자(찾기용) */
    val forms: Map<String, String> = emptyMap(),
    /** 절 제목 → 문단·표 구조(보기용) */
    val manualDocs: Map<String, List<HBlock>> = emptyMap(),
    /** 서식 번호 → 문단·표 구조(보기용) */
    val formDocs: Map<String, List<HBlock>> = emptyMap(),
    val formMarks: List<FormMark> = emptyList(),
    val manualFile: String = "",
    val formsFile: String = "",
    /** 원본 사본 경로(앱 데이터 폴더 안) */
    val manualCopy: String = "",
    val formsCopy: String = "",
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
    fun toggleSkip(taskId: String, stepId: String) = setState(taskId, stepId, if (of(taskId).stateOf(stepId) == StepState.SKIPPED) StepState.TODO else StepState.SKIPPED)

    /** 단계 상태를 바꾼다. [owner]는 다른 팀 담당일 때 맡은 곳 */
    fun setState(taskId: String, stepId: String, state: StepState, owner: String? = null) = update(taskId) { p ->
        val base = p.copy(done = p.done - stepId, skipped = p.skipped - stepId, others = p.others - stepId)
        when (state) {
            StepState.TODO -> base
            StepState.DONE -> base.copy(done = base.done + stepId)
            StepState.SKIPPED -> base.copy(skipped = base.skipped + stepId)
            StepState.OTHER -> base.copy(others = base.others + (stepId to (owner ?: p.others[stepId].orEmpty())))
        }
    }

    fun setOwner(taskId: String, stepId: String, owner: String) = update(taskId) { p -> if (stepId in p.others) p.copy(others = p.others + (stepId to owner)) else p }
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
        val blocks = HwpText.read(file)
        val docs = splitManualBlocks(blocks)
        if (docs.size < 10) throw IllegalArgumentException("검사품질관리 매뉴얼의 절을 찾지 못했습니다(찾은 절 ${docs.size}개).")
        val copy = keepOriginal(file, "manual")
        saveLibrary { it.copy(manual = docs.mapValues { (_, v) -> HwpText.flatten(v).trim() }, manualDocs = docs, manualFile = file.name, manualCopy = copy.absolutePath, importedAt = System.currentTimeMillis()) }
        docs.size
    }

    suspend fun importForms(file: File): Int = withContext(Dispatchers.IO) {
        val blocks = HwpText.read(file)
        val (docs, marks) = splitFormBlocks(blocks)
        if (docs.isEmpty()) throw IllegalArgumentException("'< 제0-0호 서식 >' 표시가 있는 서식 문서가 아닙니다.")
        val copy = keepOriginal(file, "forms")
        File(AppDirs.root, "library/cut").deleteRecursively()
        saveLibrary { it.copy(forms = docs.mapValues { (_, v) -> HwpText.flatten(v).trim() }, formDocs = docs, formMarks = marks, formsFile = file.name, formsCopy = copy.absolutePath, importedAt = System.currentTimeMillis()) }
        docs.size
    }

    /** 원본 파일을 앱 데이터 폴더에 사본으로 둔다(원래 파일을 옮기거나 지워도 쓸 수 있게). */
    private fun keepOriginal(file: File, name: String): File {
        val ext = file.extension.lowercase().ifBlank { "hwp" }
        val dir = File(AppDirs.root, "library").apply { mkdirs() }
        dir.listFiles { f -> f.nameWithoutExtension == name }?.forEach { it.delete() }
        return File(dir, "$name.$ext").also { file.copyTo(it, overwrite = true) }
    }

    /** 원본 서식 파일에서 [no] 서식 부분만 남긴 한글 파일(앱 데이터 폴더에 한 번 만들어 두고 다시 쓴다) */
    suspend fun formOriginal(no: String, lib: GuideLibraryData = _library.value): Hancom.Result {
        val src = File(lib.formsCopy)
        if (!src.isFile) return Hancom.Result.Failed("서식 원본이 없습니다. 서식·매뉴얼에서 '검사관련 서식' 한글 파일을 다시 가져와 주세요.")
        val range = formRange(lib.formMarks, no) ?: return Hancom.Result.Failed("제${no}호 서식 표시를 원본에서 찾지 못했습니다.")
        val out = File(AppDirs.root, "library/cut/제${no}호.${src.extension.lowercase()}")
        if (out.isFile && out.lastModified() >= src.lastModified() && out.length() > 0) return Hancom.Result.Ok(out)
        return Hancom.extract(src, out, range.first, range.second)
    }

    fun clearLibrary() = saveLibrary { GuideLibraryData() }

    private fun saveLibrary(block: (GuideLibraryData) -> GuideLibraryData) {
        _library.update(block)
        runCatching { libStore.write(_library.value) }
    }

    /** 단계에 해당하는 매뉴얼 절 제목(가져온 경우) */
    fun manualFor(step: Step, lib: GuideLibraryData = _library.value): List<String> =
        step.manual.distinctBy(::norm).mapNotNull { t -> findManualKey(t, lib) }.distinct()

    /** 제목이 같거나 제목으로 시작하는 절(예: "…실무지침(제정 2015.5.11.)") */
    fun findManualKey(title: String, lib: GuideLibraryData = _library.value): String? {
        val n = norm(title)
        return lib.manual.keys.firstOrNull { norm(it) == n } ?: lib.manual.keys.firstOrNull { norm(it).startsWith(n) }
    }

    fun findManual(title: String, lib: GuideLibraryData = _library.value): String? = findManualKey(title, lib)?.let { lib.manual[it] }

    /** 절의 문단·표 구조. 예전에 글자만 가져온 경우 글자를 문단으로 바꿔 준다. */
    fun manualDoc(key: String, lib: GuideLibraryData = _library.value): List<HBlock> =
        lib.manualDocs[key] ?: lib.manual[key]?.lines()?.map { HPara(it) }.orEmpty()

    fun formDoc(no: String, lib: GuideLibraryData = _library.value): List<HBlock> =
        lib.formDocs[no] ?: lib.forms[no]?.lines()?.map { HPara(it) }.orEmpty()

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
        private val CHAPTER = Regex("""^\s*제\d+장(\s.*)?$""")

        /** 매뉴얼을 문단·표 구조째로 절별로 나눈다(나누는 규칙은 [splitManual]과 같다). */
        fun splitManualBlocks(blocks: List<HBlock>): Map<String, List<HBlock>> {
            val out = LinkedHashMap<String, MutableList<HBlock>>()
            var current: MutableList<HBlock>? = null
            var i = 0
            fun textAt(k: Int) = (blocks.getOrNull(k) as? HPara)?.takeIf { it.hasText }?.text
            while (i < blocks.size) {
                val t = textAt(i)
                if (t != null && SECTION_NO.matches(t)) {
                    var j = i + 1
                    while (j < blocks.size && (blocks[j] as? HPara)?.text?.isBlank() == true) j++
                    val title = textAt(j)?.trim().orEmpty()
                    if (title.length in 2..40 && !title.startsWith("□") && !title.startsWith("◦")) {
                        current = out.getOrPut(title) { mutableListOf() }.also { if (it.isNotEmpty()) it += HPara("", hasText = false) }
                        i = j + 1
                        continue
                    }
                }
                if (t == null || !CHAPTER.matches(t)) current?.add(blocks[i])
                i++
            }
            return out.mapValues { (_, v) -> trimBlank(v) }.filterValues { HwpText.flatten(it).trim().length > 20 }
        }

        /** 서식 문서를 "< 제2-4호 서식 >" 문단으로 나눈다. 같은 번호가 다시 나오면 이어 붙인다. */
        fun splitFormBlocks(blocks: List<HBlock>): Pair<Map<String, List<HBlock>>, List<FormMark>> {
            val out = LinkedHashMap<String, MutableList<HBlock>>()
            val marks = mutableListOf<FormMark>()
            var current: MutableList<HBlock>? = null
            blocks.forEach { b ->
                val t = (b as? HPara)?.text?.trim()
                val m = t?.let { FORM_MARK.matchEntire(it) }
                if (m != null) {
                    val no = "${m.groupValues[1]}-${m.groupValues[2]}"
                    marks += FormMark(no, t)
                    current = out.getOrPut(no) { mutableListOf() }.also { if (it.isNotEmpty()) it += HPara("", hasText = false) }
                } else current?.add(b)
            }
            return out.mapValues { (_, v) -> trimBlank(v) }.filterValues { it.isNotEmpty() } to marks
        }

        private fun trimBlank(v: List<HBlock>): List<HBlock> {
            fun blank(b: HBlock) = b is HPara && b.text.isBlank()
            val a = v.indexOfFirst { !blank(it) }
            val z = v.indexOfLast { !blank(it) }
            return if (a < 0) emptyList() else v.subList(a, z + 1).toList()
        }

        /**
         * 한글에서 찾을 시작·끝 표시. 시작은 [no]의 첫 표시, 끝은 그 뒤에 처음 나오는 다른 번호의 표시.
         * 같은 글자가 앞에 또 있으면 몇 번째인지(nth)로 가린다.
         */
        fun formRange(marks: List<FormMark>, no: String): Pair<Hancom.Mark, Hancom.Mark?>? {
            val i = marks.indexOfFirst { it.no == no }
            if (i < 0) return null
            fun nth(k: Int) = marks.subList(0, k + 1).count { it.text == marks[k].text }
            val j = (i + 1 until marks.size).firstOrNull { marks[it].no != no }
            return Hancom.Mark(marks[i].text, nth(i)) to j?.let { Hancom.Mark(marks[it].text, nth(it)) }
        }

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
