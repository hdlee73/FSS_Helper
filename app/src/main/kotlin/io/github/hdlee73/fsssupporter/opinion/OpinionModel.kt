package io.github.hdlee73.fsssupporter.opinion

import com.hdlee73.everylaw.core.domain.HwpxExporter
import com.hdlee73.everylaw.core.domain.HwpxParagraph
import io.github.hdlee73.fsssupporter.data.AppDirs
import io.github.hdlee73.fsssupporter.data.JsonFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class DocTemplate(val id: String, val name: String, val body: String, val builtIn: Boolean = false)

@Serializable
data class Draft(
    val id: String = UUID.randomUUID().toString().substring(0, 8),
    val title: String,
    val templateId: String = "",
    val body: String,
    /** 인용 검증 기준일(yyyyMMdd). 빈 값이면 현행 */
    val asOf: String = "",
    val taskId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class OpinionData(val drafts: List<Draft> = emptyList(), val templates: List<DocTemplate> = emptyList(), val selected: String? = null)

/**
 * 의견서·조치안·검사서 초안과 서식. 기본 서식은 일반적인 구성 예시이고, 실제 부서 서식은 '서식으로 저장'으로 추가한다.
 */
class OpinionModel(private val verifier: CitationVerifier, private val scope: CoroutineScope) {
    private val store = JsonFile(File(AppDirs.root, "opinions.json"), OpinionData.serializer()) { OpinionData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<OpinionData> = _data.asStateFlow()

    private val _checks = MutableStateFlow<CheckState>(CheckState.Idle)
    val checks: StateFlow<CheckState> = _checks.asStateFlow()
    private var job: Job? = null

    sealed interface CheckState {
        data object Idle : CheckState
        data object Running : CheckState
        data class Done(val draftId: String, val results: List<CitationCheck>, val at: Long = System.currentTimeMillis()) : CheckState
        data class Failed(val message: String) : CheckState
    }

    val templates: List<DocTemplate> get() = BUILT_IN + _data.value.templates
    val selected: Draft? get() = _data.value.drafts.firstOrNull { it.id == _data.value.selected }

    private fun save(block: (OpinionData) -> OpinionData) {
        _data.update(block)
        runCatching { store.write(_data.value) }
    }

    fun select(id: String?) {
        save { it.copy(selected = id) }
        _checks.value = CheckState.Idle
    }

    fun create(template: DocTemplate, title: String = template.name): Draft {
        val d = Draft(title = "$title ${LocalDate.now()}", templateId = template.id, body = template.body)
        save { it.copy(drafts = listOf(d) + it.drafts, selected = d.id) }
        _checks.value = CheckState.Idle
        return d
    }

    fun update(d: Draft) = save { s -> s.copy(drafts = s.drafts.map { if (it.id == d.id) d.copy(updatedAt = System.currentTimeMillis()) else it }) }

    fun delete(id: String) = save { s -> s.copy(drafts = s.drafts.filter { it.id != id }, selected = if (s.selected == id) null else s.selected) }

    fun saveAsTemplate(d: Draft, name: String) = save { s -> s.copy(templates = s.templates + DocTemplate(UUID.randomUUID().toString().substring(0, 8), name, d.body)) }

    fun deleteTemplate(id: String) = save { s -> s.copy(templates = s.templates.filter { it.id != id }) }

    /** 다른 화면(법령검색)에서 보낸 조문을 지금 초안 끝에 붙인다. 초안이 없으면 새로 만든다. */
    fun insert(text: String) {
        val d = selected ?: create(DocTemplate("blank", "메모", "", builtIn = true), "조문 메모")
        val sep = if (d.body.isBlank() || d.body.endsWith("\n\n")) "" else if (d.body.endsWith("\n")) "\n" else "\n\n"
        update(d.copy(body = d.body + sep + text + "\n"))
    }

    fun verify(d: Draft) {
        job?.cancel()
        job = scope.launch {
            _checks.value = CheckState.Running
            _checks.value = try {
                CheckState.Done(d.id, verifier.verify(d.body, d.asOf.ifBlank { null }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CheckState.Failed(e.message ?: "검증하지 못했습니다.")
            }
        }
    }

    suspend fun exportHwpx(d: Draft): File = withContext(Dispatchers.IO) {
        val file = File(AppDirs.exports, safeName(d.title) + ".hwpx")
        file.outputStream().use { HwpxExporter.writeDocument(d.title, toParagraphs(d.body), it) }
        file
    }

    suspend fun exportDocx(d: Draft): File = withContext(Dispatchers.IO) {
        val file = File(AppDirs.exports, safeName(d.title) + ".docx")
        file.outputStream().use { DocxWriter.write(d.body.lines(), it) }
        file
    }

    companion object {
        fun safeName(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_").take(80)

        /**
         * 글 줄을 한글 문단으로 바꾼다. 첫 줄(서식 제목)은 가운데 큰 글씨, "1." "Ⅰ." 같은 번호 머리는 굵게,
         * 앞 공백 두 칸마다 한 단계 들여쓴다.
         */
        fun toParagraphs(body: String): List<HwpxParagraph> {
            val lines = body.lines()
            val firstIdx = lines.indexOfFirst { it.isNotBlank() }
            return lines.mapIndexed { i, raw ->
                val depth = (raw.length - raw.trimStart().length) / 2
                val t = raw.trim()
                val style = when {
                    i == firstIdx -> HwpxParagraph.Style.TITLE
                    SECTION.matches(t) -> HwpxParagraph.Style.SECTION
                    HEAD.containsMatchIn(t) && t.length <= 40 -> HwpxParagraph.Style.BOLD
                    t.startsWith("※") -> HwpxParagraph.Style.GRAY
                    else -> HwpxParagraph.Style.BODY
                }
                HwpxParagraph(t, style, depth.coerceAtMost(6))
            }
        }

        private val SECTION = Regex("""^[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]\.\s*.+""")
        private val HEAD = Regex("""^(\d+\.|[가-하]\.)\s+\S""")

        private fun t(body: String) = body.trimIndent()

        val BUILT_IN = listOf(
            DocTemplate("tpl-opinion", "검사의견서 (기본 예시)", t(
                """
                검 사 의 견 서

                1. 검사 개요
                  가. 검사 대상 :
                  나. 검사 기간 :
                  다. 검사 범위 :

                2. 위법·부당 사항
                  가. (지적 제목)
                    1) 사실관계
                      -
                    2) 관련 법규
                      - 「자본시장과 금융투자업에 관한 법률」 제00조
                    3) 판단
                      -

                3. 조치 의견
                  가. 기관 :
                  나. 임직원 :

                ※ 기본 예시 서식입니다. 실제 부서 서식으로 바꾸려면 내용을 고친 뒤 '서식으로 저장'을 누르세요.
                """
            ), builtIn = true),
            DocTemplate("tpl-action", "조치안 (기본 예시)", t(
                """
                조 치 안

                1. 대상
                  가. 기관 :
                  나. 관련 임직원 :

                2. 위법·부당 사실
                  가. (위반 내용 요약)

                3. 적용 법규
                  가. 위반 조항 : 「법령명」 제00조
                  나. 제재 근거 : 「법령명」 제00조

                4. 제재 양정
                  가. 기관 :
                  나. 임직원 :
                  다. 가중·감경 사유 :

                5. 조치 내용
                  가.

                ※ 제재 양정 계산기의 결과를 '조치안에 넣기'로 붙여 넣을 수 있습니다.
                """
            ), builtIn = true),
            DocTemplate("tpl-report", "검사서 (기본 예시)", t(
                """
                검 사 서

                Ⅰ. 검사 실시 개요
                  1. 검사 목적
                  2. 검사 대상 및 기간
                  3. 검사 범위 및 중점 검사 사항

                Ⅱ. 일반 현황
                  1. 경영 현황
                  2. 내부통제 현황

                Ⅲ. 검사 결과
                  1. 위법·부당 사항
                    가.
                  2. 개선 필요 사항
                    가.

                Ⅳ. 조치 요구 사항
                  1.

                ※ 기본 예시 서식입니다.
                """
            ), builtIn = true),
            DocTemplate("tpl-blank", "빈 문서", "", builtIn = true),
        )
    }
}

/** 글 줄을 그대로 문단으로 넣는 최소한의 Word(.docx) 파일 */
object DocxWriter {
    fun write(lines: List<String>, out: java.io.OutputStream) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val firstIdx = lines.indexOfFirst { it.isNotBlank() }
        val paras = lines.mapIndexed { i, raw ->
            val indent = (raw.length - raw.trimStart().length) / 2 * 400
            val text = raw.trim()
            val title = i == firstIdx
            val pPr = buildString {
                append("<w:pPr>")
                if (title) append("<w:jc w:val=\"center\"/>")
                if (indent > 0) append("<w:ind w:left=\"$indent\"/>")
                append("</w:pPr>")
            }
            val rPr = "<w:rPr><w:rFonts w:ascii=\"Malgun Gothic\" w:eastAsia=\"Malgun Gothic\" w:hAnsi=\"Malgun Gothic\"/>" +
                (if (title) "<w:b/><w:sz w:val=\"32\"/>" else "<w:sz w:val=\"22\"/>") + "</w:rPr>"
            "<w:p>$pPr<w:r>$rPr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
        }.joinToString("")
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paras<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1701" w:right="1440" w:bottom="1440" w:left="1440" w:header="851" w:footer="992" w:gutter="0"/></w:sectPr></w:body></w:document>"""
        val types = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        ZipOutputStream(out).use { z ->
            fun add(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            add("[Content_Types].xml", types)
            add("_rels/.rels", rels)
            add("word/document.xml", document)
        }
    }
}
