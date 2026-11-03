package io.github.hdlee73.fsssupporter.opinion

import com.hdlee73.everylaw.core.domain.ItemAttachment
import com.hdlee73.everylaw.core.domain.LawRepository
import com.hdlee73.everylaw.core.model.Article
import com.hdlee73.everylaw.core.model.ArticleNo
import com.hdlee73.everylaw.core.model.LawSummary
import com.hdlee73.everylaw.core.model.formatYmd
import com.hdlee73.everylaw.core.parser.ArticleCitationExtractor
import com.hdlee73.everylaw.core.parser.ExtractedCitation
import com.hdlee73.everylaw.core.parser.LawAliases
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

enum class Verdict(val label: String) {
    OK("현행 일치"),
    ALIAS("약칭 사용"),
    DELETED("삭제된 조문"),
    NO_ARTICLE("없는 조문"),
    NO_LAW("법령 못 찾음"),
    FAILED("확인 실패"),
}

/** 인용 한 건의 검증 결과 */
data class CitationCheck(
    val citation: ExtractedCitation,
    val verdict: Verdict,
    val message: String,
    /** 찾은 법령의 정식 명칭 */
    val formalName: String? = null,
    val article: Article? = null,
    /** 「정식 명칭」 제N조 */
    val suggestion: String? = null,
    /** 기준일 조문과 현행 조문이 다르면 현행 조문(기준일로 검증했을 때만) */
    val current: Article? = null,
)  {
    val changedSince: Boolean get() = current != null
}

/**
 * 문서 속 "「법령명」 제N조" 인용을 모두 찾아, 국가법령정보에서 그 조문이 실제로 있는지·삭제되지 않았는지 확인한다.
 * [asOf](yyyyMMdd)를 주면 그날 시행 중이던 법령 기준으로 본다(위반행위 당시 조문 확인).
 */
class CitationVerifier(private val repository: LawRepository) {
    fun extract(text: String): List<ExtractedCitation> = ArticleCitationExtractor.extract(text)

    suspend fun verify(text: String, asOf: String? = null): List<CitationCheck> = coroutineScope {
        val gate = Semaphore(4)
        extract(text).map { c -> async { gate.withPermit { check(c, asOf) } } }.awaitAll()
    }

    private val lawCache: MutableMap<String, LawSummary?> = java.util.Collections.synchronizedMap(HashMap())

    private suspend fun findLaw(name: String): LawSummary? {
        lawCache[name]?.let { return it }
        val found = repository.searchLaws(name)
        val key = name.replace(" ", "")
        val hit = found.firstOrNull { it.name.replace(" ", "") == key } ?: found.firstOrNull { it.abbreviation?.replace(" ", "") == key }
        lawCache[name] = hit
        return hit
    }

    suspend fun check(c: ExtractedCitation, asOf: String?): CitationCheck = try {
        val (base, level) = splitLevel(c.lawName)
        val formalBase = LawAliases.resolve(base)
        val formal = if (level != null) "$formalBase $level" else formalBase
        val law = findLaw(formal)
        val no = ArticleNo.parse(c.article)
        when {
            law == null -> CitationCheck(c, Verdict.NO_LAW, "'$formal'을(를) 국가법령정보에서 찾지 못했어요. 법령명을 확인해 주세요.")
            no == null -> CitationCheck(c, Verdict.FAILED, "조문 번호를 읽지 못했어요.", law.name)
            else -> {
                val articles = if (asOf != null) {
                    val v = repository.getLawVersions(law).filter { it.effectiveDate <= asOf }.maxByOrNull { it.effectiveDate }
                        ?: return CitationCheck(c, Verdict.NO_LAW, "${formatYmd(asOf)}에 시행 중이던 ${law.name}을(를) 찾지 못했어요.", law.name)
                    repository.getLawArticlesAt(v, no)
                } else repository.getLawArticles(law, no)
                val a = articles.firstOrNull { it.number?.main == no.main && it.number?.branch == no.branch }
                val suggestion = "「${law.name}」 ${no.display}"
                val basis = if (asOf != null) "${formatYmd(asOf)} 시행 기준" else "현행"
                when {
                    a == null -> CitationCheck(c, Verdict.NO_ARTICLE, "${law.name}에 ${no.display}가 없어요($basis).", law.name, null, suggestion)
                    isDeleted(a) -> CitationCheck(c, Verdict.DELETED, "${no.display}는 삭제된 조문이에요($basis). 이관된 조문을 확인해 주세요.", law.name, a, suggestion)
                    base.replace(" ", "") != formalBase.replace(" ", "") -> CitationCheck(c, Verdict.ALIAS, "조문은 있어요($basis). 공식 문서에는 정식 명칭을 권합니다: $suggestion", law.name, a, suggestion)
                    else -> {
                        // 기준일로 볼 때는 현행 조문과 견줘 그 뒤에 바뀌었는지도 알려 준다.
                        val now = if (asOf != null) runCatching { repository.getLawArticles(law, no).firstOrNull { it.number?.main == no.main && it.number?.branch == no.branch } }.getOrNull() else null
                        val changed = now != null && squash(now.content) != squash(a.content)
                        CitationCheck(c, Verdict.OK, "${a.fullTitle} · $basis" + if (changed) " · 그 뒤 개정되어 현행과 다름" else "", law.name, a, suggestion, if (changed) now else null)
                    }
                }
            }
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        CitationCheck(c, Verdict.FAILED, "확인하지 못했어요: ${e.message ?: e::class.simpleName}")
    }

    companion object {
        private val LEVEL = Regex("""\s*(시행령|시행규칙)$""")
        private val DELETED_HEAD = Regex("""^제\s*\d+\s*조(?:\s*의\s*\d+)?\s*(?:\([^)]*\))?\s*삭제""")

        fun squash(s: String) = s.replace(Regex("""\s+"""), "")

        fun splitLevel(name: String): Pair<String, String?> {
            val m = LEVEL.find(name) ?: return name.trim() to null
            return name.substring(0, m.range.first).trim() to m.groupValues[1]
        }

        fun isDeleted(a: Article): Boolean {
            val content = a.content.trim()
            return a.title.trim() == "삭제" || DELETED_HEAD.containsMatchIn(content) || ItemAttachment.isDeletedText(content)
        }
    }
}
