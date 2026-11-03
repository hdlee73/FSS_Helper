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
                tableCells(t)?.let { return@mapIndexed HwpxParagraph(t, HwpxParagraph.Style.BODY, depth.coerceAtMost(6), it) }
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

        /** "| 가 | 나 |" 꼴의 줄이면 칸 목록 */
        fun tableCells(line: String): List<String>? {
            val t = line.trim()
            if (t.length < 3 || !t.startsWith("|") || !t.endsWith("|")) return null
            return t.substring(1, t.length - 1).split('|').map { it.trim() }
        }

        private val SECTION = Regex("""^[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ]\.\s*.+""")
        private val HEAD = Regex("""^(\d+\.|[가-하]\.)\s+\S""")

        private fun t(body: String) = body.trimIndent()

        /** 금감원 검사업무 서식(사용자 제공 양식)의 구성을 따른 기본 서식. 실명·기관명은 넣지 않는다. */
        val BUILT_IN = listOf(
            DocTemplate("tpl-opinion", "검사의견서", t(
                """
                검 사 의 견 서
                                                            담당검사원 :              (서명)

                1. 제목 : (예시) ○○업무처리에 관한 사항

                2. 사실내용
                  가. 20 . . . ~ 20 . . . 기간 중 ○○○은 ──── 업무를 수행하면서 ──── 함으로써 ──── 한 결과를 초래하였음

                3. 관련법규
                  가. 「○○법」 제○조 제○항
                  나. 「○○법 시행령」 제○조
                  다. 「○○업감독규정」 제○조

                4. 관련 (임)직원 :
                  | 직위 | 성명 | 관련자구분 | 담당업무 | 담당기간 | 현소속 |
                  |      |      |            |          |          |        |

                ※ 관련자 구분(행위자, 보조자, 지시자, 감독자) : 「금융기관 검사 및 제재에 관한 규정 시행세칙」 제52조에 의함
                ※ 상기 기재항목 중 일부 내용이 생략되어 있을 수 있으며, 이 경우 이를 보완하여 다시 교부할 예정입니다. 아울러, 검사의견서에 기재된 지적예정사항은 확정된 것은 아니며, 심의과정에서 변경될 수 있습니다.
                """
            ), builtIn = true),
            DocTemplate("tpl-action", "검사결과 조치안", t(
                """
                ○○은행에 대한 ○○검사결과 조치안
                【의안번호 제20  -   호】  ※ 제재절차 완료 전 조치예정내용 등의 외부 누설 금지

                  검사실시기간 : 20 . . . ~ 20 . . .
                  검사대상기간 : 20 . . . ~ 20 . . .
                  검 사 원 : 검사반장 ○○○ 외 ○명
                  검 사 배 경 : (예: 민원사항 검사)
                                                                              ○○○○국

                Ⅰ. 문책사항
                  ※ 문책사항 관련 주의사항 포함. 자율처리필요사항 통보 대상자가 있는 경우 '문책 및 자율처리필요사항'으로 기재

                  1. (지적 제목)
                    《 관련자별 귀책사유 및 조치의견 》
                    <관련자별 조치명세>
                    | 직위(현직) | 성명 | 관련자 구분 | 담당업무(담당기간) | 관련금액(백만원) | 가중감경 | 조치의견 | 비고 |
                    |            |      |             |                    |                  |          |          |      |
                    | 자율처리필요사항 대상직원 |  |  |  |  |  |  |  |

                    ※ 조치예정내용 사전통지(고지)결과 : 의견없음 ○명, 의견제출 ○명(기관 포함)
                      - 조치사항에 대한 관련자의 의견 및 검토내용 : 붙임(의견이 있는 경우)
                    ※ 전번 검사결과 위의 관련자에 대한 제재내용 :
                      - (직위) ○○○ (제재 종류)(20 .  ., 사유)
                    ※ 행위자, 감독자 및 보조자의 순
                    ※ 담당기간 중 '현재'는 '검사종료일 현재'로 하되, 검사종료 후 퇴직한 경우에는 퇴직일 전일자 기재

                Ⅱ. 자율처리필요사항
                  ※ 직원에 대한 문책사항뿐만 아니라 주의사항으로서 자율처리필요사항 통보하는 경우를 포함

                  1. (지적 제목)
                    《 조치의견 》
                    □ 위 자율처리필요사항에 대하여는
                      ○ 「금융기관 검사 및 제재에 관한 규정 시행세칙」 제45조 제2항에 따라 위법·부당사실을 ○○○○ 대표이사(또는 ○○은행장)에게 통보하여 조치대상자 및 수준을 적의 결정하여 조치하도록 통보하고자 함
                    ※ 조치예정내용 사전통지(고지)결과 : 의견없음(또는 의견제출)

                Ⅲ. 주의사항
                  ※ 임직원 개인에 대한 주의조치 요구사항(자율처리필요사항 제외)에 한함

                  1. (지적 제목)
                    《 관련자별 귀책사유 및 조치의견 》
                    <관련자별 조치명세>
                    | 직위(현직) | 성명 | 관련자 구분 | 담당업무(담당기간) | 관련금액(백만원) | 조치의견 | 비고 |
                    |            |      |             |                    |                  |          |      |

                < 별지 > 검사결과 지적(조치)사항에 대한 관련자의 의견 및 검토내용
                  ※ 관련임직원 의견에 대해서는 개별·구체적으로 검사국 검토의견 작성
                  [금융기관 관련임직원 의견]
                    1. 임점검사중 제출의견
                    2. 검사국장에 제출된 의견
                    3. 검사결과 조치예정내용 사전통지에 대한 의견
                  [검사국 의견]
                    -

                ※ 제재 양정 계산기의 결과를 '조치안에 넣기'로 붙일 수 있습니다.
                """
            ), builtIn = true),
            DocTemplate("tpl-notice", "조치예정내용 사전통지(고지)서", t(
                """
                검사결과 조치예정내용 사전통지(고지)서

                ※ 본 조치예정내용은 향후 제재심의위원회 심의, 금융감독원장 결재, 금융위원회 의결 등의 과정에서 변경될 수 있으므로 금융감독원과의 사전협의 없이 언론 등 외부에 공개하여서는 아니되며 대외보안에 각별히 유의하시기 바랍니다.

                1. 당사자
                  가. 성명(명칭) : ○○은행 부행장 △△△ (또는 ○○은행)
                  나. 주소 :

                2. 조치의 원인이 되는 사실
                  1. 「○○법」 제○조, 「○○규정」 제○조에 의하면 ──── ○○○은 ──── 할 의무가 있음에도
                    가. 20 . . . ~ 20 . . . 기간 중 ○○○은 ──── 업무를 수행하면서 총 ○○회에 걸쳐 ──── 함으로써 ──── 한 결과를 초래하였으며
                    나. 20 . . . ~ 20 . . . 기간 중 ○○○은 △△(주)에 대하여 ──── 함으로써 ○○억원의 부실채권이 발생하였음
                    ⇒ 위 귀책사항 1.(가)(나)항과 관련하여 귀하는 ──── 업무를 ── 하고 있었음에도 이에 대한 ──── 를 적절하게 이행하지 아니한 것에 대한 행위/감독/보조/지시 책임이 있음

                3. 조치예정 내용
                  (예) 정직(감봉 ~ 면직)
                  ※ "( )"는 변경가능한 제재수준(구간)이며, 금융위원회의 제재사항인 경우 '조치예정내용(금융위원회에 건의 예정)'으로 표시하며, 퇴직자의 경우 퇴직자 위법·부당사항으로 표시

                4. 관련 법규
                  1. 「○○법」 제○조 제○항
                  2. 「○○법 시행령」 제○조 제○항
                  3. 「○○업감독규정」 제○조 제○항
                  4. 「○○업감독업무시행세칙」 제○조 제○항

                5. 의견제출방법
                  가. 제출처 : 금융감독원 ○○○○검사국 (담당검사역 ○○○)
                  나. 주소 :
                  다. 전화번호 / 모사전송 :
                  라. 전자우편주소 :
                  마. 기한 : 20  년  월  일까지

                ※ 유의사항
                  가. 본 조치예정내용 사전통지(고지)는 「행정절차법」 제21조 제1항, 「금융기관 검사 및 제재에 관한 규정」 제35조 제1항 또는 제34조 제3항에 따라 이루어지는 것입니다.
                  나. 위의 조치예정내용은 제재심의위원회 심의, 금융감독원장 결재, 금융위원회 의결 등의 과정에서 변동될 수 있습니다.
                  다. 귀하는 위의 사항에 대하여 붙임 의견진술서 서식에 의하여 서면 또는 정보통신망을 이용하여 의견을 제출할 수 있으며, 주장을 입증할 증거자료를 함께 제출할 수 있습니다.
                  라. 의견제출기한 내에 의견을 제출하지 아니하는 경우에는 의견이 없는 것으로 간주합니다.
                  마. 그밖에 궁금한 사항이 있으시면 의견제출처로 문의하시기 바랍니다.

                【작성 방법】
                  ※ '2. 조치의 원인이 되는 사실'에는 위규기간, 구체적인 위규사실, 시행세칙 제52조에 따른 관련자(행위자/보조자/지시자/감독자) 구분을 명기
                  ※ '3. 조치예정 내용'에는 조치안에 기재될 제재수준을 기재하되 변경가능한 제재수준(상·하 1단계 구간)을 병기
                  ※ '4. 관련 법규'에는 법률, 시행령, 감독규정, 시행세칙 및 기타 조치 관련 근거규정을 세부적으로 기재
                """
            ), builtIn = true),
            DocTemplate("tpl-report-cover", "검사보고서 (결재용 표지)", t(
                """
                검 사 보 고 서

                (          )에 대한 (          ) 검사결과를 붙임과 같이 보고합니다.
                ※ 「검사및제재규정시행세칙」 제30조 제2항에 따라 검사서 중 경영실태평가결과 및 일부 조치 요구사항(경영유의, 개선 및 현지조치사항)을 분리하여 우선통보

                [검사원별 조치요구사항]
                  | 성명 | 분장업무 | 경영유의 | 문책 | 자율처리필요 | 주의 | 변상 | 개선 | 계 | 현지조치 | 총계 | 통보사항 |
                  |      |          |          |      |              |      |      |      |    |          |      |          |
                  | 합계 |          |          |      |              |      |      |      |    |          |      |          |
                  ※ 통보사항 : 위임(위탁)을 받거나 관련법상 금융위(금감원)의 직접적인 제재관련 근거규정이 없어 검사결과 지적사항을 당해 금융기관장 등에게 통보하는 사항

                [검사국 자체 심의]
                  검사원 :          (인)    팀장 :          (인)
                """
            ), builtIn = true),
            DocTemplate("tpl-report", "검사서 (정기검사·경영실태평가)", t(
                """
                검 사 서

                검사 대상 기관 :
                검 사 종 류 :
                검 사 기 준 일 :
                검사서 번호 :   -   -

                ※ 본 검사서는 「금융기관 검사 및 제재에 관한 규정」에 따라 금융감독원이 시행하여 귀사에 제공하는 자료로서 금융감독원과의 사전 협의없이 본 검사서 내용을 공개하거나 광고에 이용하여서는 아니되며 보안에 각별히 유의하시기 바랍니다.

                Ⅰ. 검사실시 개요
                  1. 검사대상
                    가. 기관(점포)명 :
                    나. 기관(점포)장 성명 :      (검사종료일 현재)
                  2. 검사실시내역
                    가. 종류 :
                    나. 검사기준일 :
                    다. 검사대상기간 :
                    라. 검사실시기간 :      (  일간)
                  3. 지난번 검사실시내역 (최근 3년간)
                    가. 검사종류 :
                    나. 검사실시기간 :
                  4. 중점검사사항

                Ⅱ. 경영실태평가
                  1. 종합평가(종합등급)
                  2. 부문별 평가(부문별 등급)
                    가. 경영관리의 적정성
                      < 경영유의사항 >
                    나. 자산건전성
                      < 경영유의사항 >
                    다. 유동성
                      < 경영유의사항 >
                    라. 수익성
                      < 경영유의사항 >
                    마. 자본적정성
                      < 경영유의사항 >
                    바. 리스크관리
                      < 경영유의사항 >
                  3. 정보기술 부문 평가
                    < 경영유의사항 >

                Ⅲ. 검사결과 조치요구사항
                  1. 조치요구사항 일람표
                    | 구분 | 제목 | 비고 |
                    | 경영유의사항 | 경영실태평가 내용 중 경영유의사항 참조 |  |
                    | 지적사항 - 문책 |  |  |
                    | 지적사항 - 자율처리필요 |  |  |
                    | 지적사항 - 주의 |  |  |
                    | 지적사항 - 개선 |  |  |
                    | 현지조치 - 현지개선 |  |  |
                    | 현지조치 - 현지주의 |  |  |
                  2. 지적사항
                    가. 문책사항 (문책 및 자율처리필요사항)
                      (1) (제목)
                        - 본문 내용 -
                        < 관련법규 >
                        1. 「○○법」 제○조
                    나. 자율처리필요사항
                      (1) (제목)
                    다. 주의사항 (주의 및 자율처리필요사항)
                      (1) (제목)
                    라. 개선사항
                  3. 현지조치사항 (이미 통보하였음)
                    가. 현지개선
                    나. 현지주의

                Ⅳ. 한국은행/예금보험공사 검사결과 통보 (공동검사인 경우)
                  1. 유동성리스크 관리 관련
                  2. 전산시스템 안전성 관련
                  3. 기타

                Ⅴ. 모범사례 및 이사회 관심필요사항
                  1. 모범사례
                  2. 이사회 관심필요사항

                금 융 감 독 원 장
                """
            ), builtIn = true),
            DocTemplate("tpl-report-adhoc", "검사서 (수시검사 등)", t(
                """
                검 사 서

                검사 대상 기관 :
                검 사 종 류 :
                검 사 기 준 일 :

                Ⅰ. 검사실시 개요
                  1. 검사대상
                  2. 검사실시내역
                  3. 지난번 검사실시내역 (최근 3년간)
                  4. 중점검사사항

                Ⅱ. ○○업무 실태평가 (검사·평가 결과가 포함된 수시검사의 경우 작성)
                  1. 종합평가
                  2. 부문별 평가

                Ⅲ. 검사결과 조치요구사항
                  1. 조치요구사항 일람표
                  2. 경영유의사항
                  3. 지적사항
                    가. 문책사항
                    나. 자율처리필요사항
                    다. 주의사항
                    라. 개선사항
                  4. 현지조치사항

                Ⅳ. 한국은행/예금보험공사 검사결과 통보 (공동검사인 경우)

                Ⅴ. 모범사례 및 이사회 관심필요사항
                  1. 모범사례
                  2. 이사회 관심필요사항

                ※ 해당사항이 없는 항목은 생략할 수 있습니다.
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
        fun table(rows: List<List<String>>): String {
            val n = rows.maxOf { it.size }
            val sb = StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"5000\" w:type=\"pct\"/><w:tblBorders>")
            listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { sb.append("<w:$it w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"808080\"/>") }
            sb.append("</w:tblBorders></w:tblPr>")
            rows.forEachIndexed { r, row ->
                sb.append("<w:tr>")
                (0 until n).forEach { c ->
                    val b = if (r == 0) "<w:b/>" else ""
                    val jc = if (r == 0) "<w:jc w:val=\"center\"/>" else ""
                    sb.append("<w:tc><w:p><w:pPr>$jc</w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Malgun Gothic\" w:eastAsia=\"Malgun Gothic\" w:hAnsi=\"Malgun Gothic\"/>$b<w:sz w:val=\"18\"/></w:rPr><w:t xml:space=\"preserve\">${esc(row.getOrElse(c) { "" })}</w:t></w:r></w:p></w:tc>")
                }
                sb.append("</w:tr>")
            }
            return sb.append("</w:tbl>").toString()
        }
        val pending = mutableListOf<List<String>>()
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
            val cells = OpinionModel.tableCells(text)
            if (cells != null) { pending += cells; return@mapIndexed "" }
            val flushed = if (pending.isNotEmpty()) table(pending.toList()).also { pending.clear() } else ""
            flushed + "<w:p>$pPr<w:r>$rPr<w:t xml:space=\"preserve\">${esc(text)}</w:t></w:r></w:p>"
        }.joinToString("") + (if (pending.isNotEmpty()) table(pending.toList()) else "")
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
