package io.github.hdlee73.fsssupporter.inspection

import java.time.DayOfWeek
import java.time.LocalDate

/** 검사 서식 한 건(서식번호·이름·관련 규정·대상처). 원문은 사용자가 가져온 서식 파일에서 채운다. */
data class FormRef(val no: String, val name: String, val basis: String = "", val target: String = "", val group: String = "") {
    val label: String get() = "제${no}호 $name"
}

/** 기한 기준: 착수일·현장검사 종료일에서 며칠(또는 영업일) 전후 */
enum class Anchor { START, END }

data class DueRule(val anchor: Anchor, val days: Int, val businessDays: Boolean = false, val regularDays: Int? = null, val regularBusiness: Boolean? = null) {
    fun due(regular: Boolean, start: LocalDate?, end: LocalDate?): LocalDate? {
        val base = (if (anchor == Anchor.START) start else end) ?: return null
        val d = if (regular && regularDays != null) regularDays else days
        val biz = if (regular && regularBusiness != null) regularBusiness else businessDays
        return if (biz) addBusinessDays(base, d) else base.plusDays(d.toLong())
    }

    fun label(regular: Boolean): String {
        val d = if (regular && regularDays != null) regularDays else days
        val biz = if (regular && regularBusiness != null) regularBusiness else businessDays
        val unit = if (biz) "영업일" else "일"
        val a = if (anchor == Anchor.START) "착수" else "현장검사 종료"
        return when {
            d == 0 -> "$a 당일"
            d < 0 -> "$a ${-d}${unit} 전"
            else -> "$a ${d}${unit} 후"
        }
    }
}

/** 주말만 빼고 센다(공휴일은 반영하지 않음). */
fun addBusinessDays(from: LocalDate, days: Int): LocalDate {
    var d = from
    var left = kotlin.math.abs(days)
    val step = if (days < 0) -1L else 1L
    while (left > 0) {
        d = d.plusDays(step)
        if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) left--
    }
    return d
}

/**
 * 검사 단계 한 칸.
 * [manual]은 검사품질관리 매뉴얼의 절 제목(가져온 매뉴얼 원문을 이어 붙일 때 씀),
 * [template]은 의견서 도우미의 기본 서식 id(있으면 그 서식으로 바로 작성).
 */
data class Step(
    val id: String,
    val title: String,
    val checks: List<String>,
    val forms: List<String> = emptyList(),
    val manual: List<String> = emptyList(),
    val due: DueRule? = null,
    val regularOnly: Boolean = false,
    val optional: Boolean = false,
)

data class Stage(val id: String, val title: String, val steps: List<Step>)

/**
 * 검사품질관리 매뉴얼의 흐름(검사준비 → 현장검사 → 검사결과 처리 → 사후관리)에 맞춘 단계표.
 * 여기에는 단계 이름·점검 항목 요지·서식 번호만 두고, 매뉴얼 원문은 사용자가 가져와 PC에만 저장한다.
 */
object InspectionGuide {
    val FORMS: List<FormRef> = listOf(
        FormRef("1-1", "임직원 면담 사전통지서", "세칙 제6조의2", "검사대상기관", "상시감시"),
        FormRef("1-2", "조사출장 실시통지서", "세칙 제6조의3", "검사대상기관", "상시감시"),
        FormRef("1-3", "조사출장 실시품의서", "세칙 제6조의3", "", "상시감시"),
        FormRef("1-4", "모니터링, 면담, 조사출장 결과보고", "세칙 제6조의2, 제6조의3", "", "상시감시"),
        FormRef("2-1", "검사실시통보서", "세칙 제18조", "검사대상기관", "검사준비"),
        FormRef("2-2", "금융거래정보의 제공요구서", "세칙 제21조", "검사대상기관", "검사준비"),
        FormRef("2-3", "금융거래정보요구서 관리대장", "세칙 제21조", "", "검사준비"),
        FormRef("2-4", "검사사전예고 통지서", "규정 제8조의2", "검사대상기관", "검사준비"),
        FormRef("2-5", "검사실시계획", "세칙 제14조, 제16조", "", "검사준비"),
        FormRef("2-6", "검사사전준비보고서", "세칙 제9조", "", "검사준비"),
        FormRef("3-1", "봉인표", "규정 제6조, 세칙 제20조", "검사대상기관", "현장검사"),
        FormRef("3-2", "출석답변 협조 요청서", "규정 제9조", "검사대상기관 등", "현장검사"),
        FormRef("3-3", "자료제출 협조 요청서", "규정 제9조", "검사대상기관 등", "현장검사"),
        FormRef("3-4", "검사진행상황 보고", "세칙 제19조", "", "현장검사"),
        FormRef("3-5", "확인서 제출 요구, 확인서 접수대장", "세칙 제24조", "검사대상기관", "현장검사"),
        FormRef("3-6", "문답서, 문답서 관리대장", "세칙 제24조", "검사대상기관", "현장검사"),
        FormRef("3-7", "조력을 받을 수 있는 권리 및 이의신청절차에 대한 유의사항", "규정 제8조의3", "검사대상기관", "현장검사"),
        FormRef("3-8", "검사의견서 교부", "세칙 제24조", "검사대상기관", "현장검사"),
        FormRef("3-9", "질문서·답변서, 관리대장", "세칙 제24조", "검사대상기관", "현장검사"),
        FormRef("3-10", "경영실태평가등급점검표", "세칙 제25조", "", "현장검사"),
        FormRef("3-11", "현지조치사항 통보서식", "세칙 제31조", "검사대상기관", "현장검사"),
        FormRef("3-12", "자료제출 요구서", "규정 제6조, 세칙 제6조의4", "검사대상기관", "현장검사"),
        FormRef("3-13", "자료제출요구서 관리대장", "", "", "현장검사"),
        FormRef("3-14", "경영진(이사회 또는 감사위원회) 면담에서 제기된 금융회사 주요의견", "세칙 제26조", "", "현장검사"),
        FormRef("3-15", "DATA 제공 동의서", "세칙 제6조의4", "검사대상기관", "현장검사"),
        FormRef("3-16", "면담예고 통보서", "세칙 제17조", "검사대상기관", "현장검사"),
        FormRef("3-17", "검사업무 협조요청 공문", "규정 제6조", "검사대상기관", "현장검사"),
        FormRef("4-1", "귀임보고서", "세칙 제29조", "", "검사결과 처리"),
        FormRef("4-2", "검사서체제", "규정 제14조, 세칙 제30조", "", "검사결과 처리"),
        FormRef("4-3", "고발(통보)서식", "세칙 제32조", "수사기관", "검사결과 처리"),
        FormRef("4-4", "고발·통보업무 처리기록부", "세칙 제32조", "", "검사결과 처리"),
        FormRef("4-5", "확약서 및 양해각서", "규정 제20조의2, 세칙 제50조의2", "검사대상기관", "검사결과 처리"),
        FormRef("4-6", "지적불문사항 통보서", "세칙 제35조", "검사대상기관", "검사결과 처리"),
        FormRef("4-7", "건의사항 통보", "세칙 제13조", "관련부서, 감독총괄국", "검사결과 처리"),
        FormRef("4-8", "검사결과 중요사항에 대한 금융회사 의견", "세칙 제34조", "", "검사결과 처리"),
        FormRef("4-9", "지적사항 변동기록부", "세칙 제35조", "", "검사결과 처리"),
        FormRef("5-1", "심사·조정의뢰", "세칙 제34조", "제재심의국", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-2", "검사결과 조치예정내용 사전통지서", "규정 제35조, 세칙 제59조", "검사대상기관", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-3", "제재건의예정서", "규정 제33조", "금융위", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-4", "검사결과 사전통지에 대한 구술의견기록서", "규정 제35조, 세칙 제59조", "", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-5", "제재심의위원회 부의예정사실 통지", "세칙 제59조", "검사대상기관", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-6", "지적사항에 대한 제재심의위원회 부의안", "세칙 제57조", "제재심의국", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-7", "이의신청사항에 대한 제재심의위원회 부의안", "세칙 제61조", "제재심의국", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-8", "청문실시통지서", "세칙 제60조", "검사대상기관", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-9", "(과징금·과태료) 부과·부과면제 건의서", "규정 제20조", "제재심의국, 금융위", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-10", "검사결과 안건상정 요청", "규정 제13조, 세칙 제36조", "금융위", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-11", "금융기관 검사보고서 송부", "규정 제17조~제19조", "금융위", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-12", "금융기관 검사결과 신속보고", "규정 제13조", "금융위", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-13", "제재조치 변경사항 통보서", "세칙 제58조", "제재심의국", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-14", "부의예정안 등 열람신청서", "세칙 제59조의2", "검사부서", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-15", "부의예정안 등에 대한 열람거부(제한) 통지서", "규정 제35조의3, 세칙 제59조의2", "열람신청인", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("5-16", "참고인 진술 신청서", "세칙 제57조", "검사부서", "제재조치안 심사조정 및 금융위 부의·보고"),
        FormRef("6-1", "검사결과 통보 및 조치요구", "규정 제14조", "검사대상기관", "검사결과 통보"),
        FormRef("6-2", "유관기관에 검사결과 통보", "규정 제14조", "유관기관", "검사결과 통보"),
        FormRef("6-3", "위탁검사결과 통보", "규정 제14조", "유관기관", "검사결과 통보"),
        FormRef("6-4", "금융실명제 위반사항에 대한 조사결과 통보", "규정 제14조", "금융위", "검사결과 통보"),
        FormRef("6-5", "예보·한국은행의 요청사항에 대한 검사결과 통보", "규정 제14조", "예보·한국은행", "검사결과 통보"),
        FormRef("7-1", "기관경고장", "규정 제17조", "검사대상기관", "기관 및 임원 제재"),
        FormRef("7-2", "문책경고장", "규정 제18조", "검사대상기관", "기관 및 임원 제재"),
        FormRef("7-3", "주의적경고장", "규정 제18조", "검사대상기관", "기관 및 임원 제재"),
        FormRef("8-1", "검사결과 조치요구사항 정리보고서", "규정 제15조", "", "검사조치에 대한 사후관리"),
        FormRef("8-2", "조치요구사항 재정리요구서", "세칙 제37조", "검사대상기관", "검사조치에 대한 사후관리"),
        FormRef("8-3", "정리보고서에 대한 심사 결과", "세칙 제37조", "", "검사조치에 대한 사후관리"),
        FormRef("9-1", "정보사항", "세칙 제13조", "관련부서, 감독총괄국", "기타"),
        FormRef("9-2", "검사자료 편철양식", "세칙 제39조", "", "기타"),
        FormRef("9-3", "검사자료 제공요청에 대한 회신", "규정 제6조", "유관기관", "기타"),
        FormRef("9-4", "검사결과 처리기간 종합표", "규정 제14조, 세칙 제30조의2", "관련부서, 감독총괄국", "기타"),
    )

    private val byNo = FORMS.associateBy { it.no }
    fun form(no: String): FormRef? = byNo[no]

    /** 의견서 도우미의 기본 서식과 짝이 되는 서식 */
    val TEMPLATE_FOR_FORM = mapOf("3-8" to "tpl-opinion", "5-2" to "tpl-notice", "4-2" to "tpl-report")

    val STAGES: List<Stage> = listOf(
        Stage("prep", "1. 검사준비", listOf(
            Step("prep-decide", "검사실시 결정", listOf(
                "연간·분기 검사계획과 수검 가능 상황 확인",
                "자체감사·감사원·예보 등 타 기관 검사 일정과 겹치는지 확인",
                "정기검사 전후 다른 수시검사 실시 여부 점검",
            ), manual = listOf("검사실시 결정")),
            Step("prep-notice", "검사 사전예고·사전자료 요구", listOf(
                "검사사전예고 통지(생략 사유 해당 여부 검토)",
                "보유 자료(업무보고서, DART 등)로 중복 요구 여부 확인",
                "자료작성 기준일·제출기한을 금융회사와 협의",
                "검사종합관리시스템으로 사전자료 요구·접수",
            ), forms = listOf("2-4"), manual = listOf("검사의 사전예고 및 검사사전자료 요구"),
                due = DueRule(Anchor.START, -7, regularDays = -30)),
            Step("prep-team", "검사반 편성·업무분장 확정", listOf(
                "검사반 편성·업무분장 확정",
                "사전준비기간 확보(수시 5영업일, 정기 10영업일 이상)",
                "검사반 편성시 유의사항(이해상충 등) 확인",
            ), manual = listOf("검사반 편성시 유의사항"), due = DueRule(Anchor.START, -10, businessDays = true)),
            Step("prep-ready", "검사사전준비", listOf(
                "유관부서와 검사사전준비협의회 개최",
                "검사매뉴얼 체크리스트로 사전요구자료 점검",
                "검사사전준비보고서 작성·보고(정기·테마검사는 착수 전 결재)",
                "검사전략회의(Scope Meeting)로 범위·중점사항·분담 확정",
                "검사원 교육(목적, 복무수칙, 유의사항)",
            ), forms = listOf("2-6"), manual = listOf("검사사전준비"),
                due = DueRule(Anchor.START, -3, businessDays = true, regularDays = -7, regularBusiness = false)),
            Step("prep-approve", "검사실시 품의·통보", listOf(
                "검사실시계획 품의",
                "검사실시통보서 발송",
                "금융거래정보 요구 시 제공요구서 발송·관리대장 기록",
            ), forms = listOf("2-5", "2-1", "2-2", "2-3"), manual = listOf("검사실시 품의"), due = DueRule(Anchor.START, -1, businessDays = true)),
            Step("prep-sample", "표본검사", listOf("표본 선정 기준과 범위 결정", "표본검사 결과를 본검사 계획에 반영"),
                manual = listOf("표본검사"), optional = true),
        )),
        Stage("field", "2. 현장검사", listOf(
            Step("field-start", "검사 착수·검사개시 면담", listOf(
                "검사장 배치와 보안 확인(필요시 현물검사·봉인)",
                "검사개시 면담에서 검사목적·일정 설명",
                "검사업무 처리절차와 조력을 받을 권리·이의신청 절차 안내",
            ), forms = listOf("3-7", "3-1", "3-17"), manual = listOf("검사 착수", "현물검사(선택)", "검사장 배치", "검사개시 면담"), due = DueRule(Anchor.START, 0)),
            Step("field-data", "검사자료 요구·관리", listOf(
                "자료제출 요구서로 요구하고 관리대장 기록",
                "DATA 제공 동의서 확보(전산자료)",
                "중복·과다 요구 자제",
            ), forms = listOf("3-12", "3-13", "3-3", "3-15"), manual = listOf("검사자료 요구", "검사자료 요구 및 관리에 관한 실무지침")),
            Step("field-interview", "임직원 면담·출석답변", listOf(
                "면담예고 통보서 발송",
                "출석답변 협조 요청",
                "면담 내용 기록",
            ), forms = listOf("3-16", "3-2"), manual = listOf("금융회사 임직원과의 면담", "검사 방해행위 유형별 대응요령")),
            Step("field-evidence", "입증자료 확보(확인서·문답서·질문서)", listOf(
                "지적사항별 입증자료 확보",
                "확인서 제출 요구·접수대장",
                "문답서 작성·관리대장",
                "질문서·답변서 관리",
            ), forms = listOf("3-5", "3-6", "3-9"), manual = listOf("검사결과 입증자료 확보", "확인서 및 문답서 징구에 관한 실무지침")),
            Step("field-daily", "일일 진행상황 기록·보고", listOf("일일 검사진행상황 기록", "검사진행상황 보고"),
                forms = listOf("3-4"), manual = listOf("일일 검사진행상황 기록", "검사진행상황 보고")),
            Step("field-rating", "경영실태평가 등급심의", listOf("경영실태평가등급점검표 작성", "등급심의회 개최"),
                forms = listOf("3-10"), manual = listOf("경영실태평가시 등급심의회 개최"), regularOnly = true),
            Step("field-mgmt", "경영진·사외이사 면담", listOf("경영진(이사회·감사위원회) 면담", "사외이사 면담", "제기된 주요의견 정리"),
                forms = listOf("3-14"), manual = listOf("경영진 면담", "사외이사 면담"), regularOnly = true),
            Step("field-onsite", "현지조치", listOf("현지조치 대상 여부 판단", "현지조치사항 통보"),
                forms = listOf("3-11"), manual = listOf("현지조치", "현지조치 운영 가이드라인"), optional = true),
            Step("field-opinion", "검사의견서 교부", listOf(
                "지적예정사항별 검사의견서 작성",
                "관련 법규 인용 검증",
                "금융회사에 교부하고 의견 접수",
            ), forms = listOf("3-8"), manual = listOf("검사의견서", "검사의견서 작성 및 교부에 관한 실무지침", "검사현장에서의 법률질의")),
            Step("field-close", "현장검사 종료", listOf(
                "쟁점사항 분리처리 여부 결정",
                "고발·통보 대상 검토",
                "강평 등 현장검사 마무리",
            ), forms = listOf("4-3", "4-4"), manual = listOf("쟁점사항 분리처리", "현장검사의 종료", "고발", "금융회사 임직원의 위법행위 등에 대한 고발업무 운영기준"), due = DueRule(Anchor.END, 0)),
        )),
        Stage("result", "3. 검사결과 처리", listOf(
            Step("res-precheck", "지적사항 사전협의회", listOf("지적사항 사전협의회 개최", "협의 결과 반영"), manual = listOf("검사결과", "지적사항 사전협의회")),
            Step("res-return", "귀임보고서 작성", listOf("귀임보고서 작성·보고"), forms = listOf("4-1"), manual = listOf("귀임보고서 작성")),
            Step("res-report", "검사서 작성·자체심의", listOf(
                "검사서체제에 따라 검사서 작성",
                "검사국 자체심의",
                "지적사항 변동기록부 관리",
                "검사결과 중요사항에 대한 금융회사 의견 반영",
            ), forms = listOf("4-2", "4-9", "4-8"), manual = listOf("검사서 작성 및 자체심의")),
            Step("res-action", "조치안 사전협의·중간브리핑", listOf(
                "검사결과 조치안 작성(제재 양정 계산기 활용)",
                "조치안 사전협의회",
                "검사결과 중간브리핑",
                "검사결과 진행상황 통지",
            ), manual = listOf("검사결과 조치안 사전협의회", "검사결과 조치안 사전협의회 실무지침", "검사결과 중간브리핑", "검사결과 진행상황 통지")),
            Step("res-notice", "조치예정내용 사전통지·의견청취·열람", listOf(
                "조치예정내용 사전통지서 발송(변경가능 구간 병기)",
                "구술의견 기록",
                "부의예정안 열람신청 처리",
                "참고인 진술 신청 처리",
            ), forms = listOf("5-2", "5-4", "5-14", "5-15", "5-16"), manual = listOf("조치예정내용의 사전통지·의견청취 및 조치안 열람")),
            Step("res-review", "심사·조정의뢰·제재심 부의", listOf(
                "심사·조정의뢰",
                "제재심의위원회 부의예정사실 통지",
                "부의안 작성",
                "금융위 건의 사항(제재건의예정서, 과징금·과태료 건의서) 처리",
            ), forms = listOf("5-1", "5-5", "5-6", "5-3", "5-9", "5-8", "5-10", "5-11", "5-12", "5-13"), manual = listOf("검사결과의 심사･조정의뢰", "검사결과의 심사·조정의뢰")),
            Step("res-board", "검사결과 이사회 설명", listOf("이사회 설명 대상 여부 확인", "설명 결과 기록"), manual = listOf("검사결과 이사회 설명"), optional = true),
            Step("res-special", "지적불문·금융실명제 등 처리", listOf("지적불문사항 통보", "금융실명제 위반사항 조치", "확약서·양해각서 처리"),
                forms = listOf("4-6", "6-4", "4-5", "4-7"), manual = listOf("금융실명제 위반사항에 대한 조치", "금융실명거래 위반에 대한 과태료 부과 관련 업무 처리 방법", "지적불문사항의 처리"), optional = true),
            Step("res-notify", "검사결과 보고·통보", listOf(
                "검사결과 통보 및 조치요구",
                "유관기관·예보·한국은행 통보",
                "기관경고장·문책경고장·주의적경고장 발송",
                "검사결과 보도·제재내용 공개 확인",
            ), forms = listOf("6-1", "6-2", "6-3", "6-5", "7-1", "7-2", "7-3"), manual = listOf("검사결과 보고･통보", "검사결과 보고·통보", "검사결과 보도", "제재내용 공개")),
        )),
        Stage("post", "4. 사후관리", listOf(
            Step("post-follow", "조치요구사항 사후관리", listOf("정리보고서 접수·심사", "부적정·미정리 사항 재정리요구"),
                forms = listOf("8-1", "8-2", "8-3"), manual = listOf("조치요구사항에 대한 사후관리")),
            Step("post-file", "검사자료 정리", listOf("검사자료 편철", "검사결과 처리기간 종합표 작성"), forms = listOf("9-2", "9-4"), manual = listOf("검사자료의 정리")),
            Step("post-eval", "검사평가회의", listOf("검사평가회의 개최", "개선사항 정리"), manual = listOf("검사평가회의", "검사품질 점검", "검사진도 분석")),
            Step("post-appeal", "이의신청", listOf("이의신청 접수·검토", "이의신청사항 부의안 작성"), forms = listOf("5-7"), manual = listOf("이의신청"), optional = true),
        )),
    )

    /** 단계와 상관없이 늘 참고하는 매뉴얼 절 */
    val REFERENCES = listOf("검사업무 운영원칙", "검사원의 마음가짐", "검사원 복무수칙", "금융회사 임직원 권익보호기준", "협업검사 세부절차", "검사 방해행위 유형별 대응요령")

    val ALL_STEPS: List<Step> get() = STAGES.flatMap { it.steps }

    /** 정기·수시 검사에 쓰이는 단계만 */
    fun steps(regular: Boolean): List<Step> = ALL_STEPS.filter { regular || !it.regularOnly }
}
