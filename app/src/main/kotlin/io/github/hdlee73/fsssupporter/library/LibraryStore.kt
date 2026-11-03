package io.github.hdlee73.fsssupporter.library

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
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File

/** 서식 문서 안의 "< 제2-4호 서식 >" 표시 한 개(문서 순서대로) */
@Serializable
data class FormMark(val no: String, val text: String)

/** 매뉴얼 목차 한 줄: 절 제목과 PDF 쪽(0부터) */
@Serializable
data class TocEntry(val title: String, val page: Int)

/** 서식 하나가 PDF에서 차지하는 쪽(0부터, 끝 포함) */
@Serializable
data class PageSpan(val start: Int, val end: Int) {
    val pages: List<Int> get() = (start..end).toList()
}

/**
 * 가져온 검사매뉴얼·검사관련 서식(이 PC의 앱 데이터 폴더에만 둔다).
 * 보기는 PDF(원본 모양 그대로), 서식 작성은 한글 원본에서 그 서식만 잘라 낸 한글 파일로 한다.
 */
@Serializable
data class LibraryData(
    val manualName: String = "",
    /** 한글 원본 사본(없으면 PDF만 가져온 경우) */
    val manualSrc: String = "",
    val manualPdf: String = "",
    /** 한글 원본에서 찾은 절 제목(문서 순서) */
    val manualTitles: List<String> = emptyList(),
    val manualToc: List<TocEntry> = emptyList(),
    val formsName: String = "",
    val formsSrc: String = "",
    val formsPdf: String = "",
    val formMarks: List<FormMark> = emptyList(),
    val formPages: Map<String, PageSpan> = emptyMap(),
    val importedAt: Long = 0,
) {
    val hasManual: Boolean get() = manualPdf.isNotBlank() && File(manualPdf).isFile
    val hasForms: Boolean get() = formsPdf.isNotBlank() && File(formsPdf).isFile
    val canWriteForms: Boolean get() = formsSrc.isNotBlank() && File(formsSrc).isFile && formMarks.isNotEmpty()

    /** 가져온 서식 번호(문서 순서, 중복 없이) */
    val formNos: List<String> get() = (formMarks.map { it.no } + formPages.keys).distinct()
}

/** 가져오기 결과 안내 */
data class ImportResult(val message: String, val pdfMissing: Boolean = false)

class LibraryStore(private val dir: File = File(AppDirs.root, "library")) {
    private val store = JsonFile(File(dir.parentFile, "library.json"), LibraryData.serializer()) { LibraryData() }
    private val _data = MutableStateFlow(store.read())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    private fun save(block: (LibraryData) -> LibraryData) {
        _data.update(block)
        runCatching { store.write(_data.value) }
    }

    private fun pagesFile(kind: String) = File(dir, "$kind.pages.txt")

    /** PDF 쪽별 글자(찾기용). 가져올 때 한 번 만들어 둔다. */
    fun pageTexts(kind: String): List<String> =
        runCatching { pagesFile(kind).readText(Charsets.UTF_8).split('\u000C') }.getOrDefault(emptyList())

    // ---------- 검사매뉴얼 ----------

    /** 한글(HWP·HWPX)이면 한글로 PDF를 만들고, PDF면 그대로 쓴다. */
    suspend fun importManual(file: File): ImportResult = withContext(Dispatchers.IO) {
        dir.mkdirs()
        when (file.extension.lowercase()) {
            "pdf" -> {
                val pdf = copyAs(file, "manual.pdf")
                val titles = _data.value.manualTitles
                val texts = indexPdf(pdf, "manual")
                val toc = mapToc(titles, texts).ifEmpty { outlineToc(pdf) }
                save { it.copy(manualPdf = pdf.absolutePath, manualToc = toc, manualName = it.manualName.ifBlank { file.name }, importedAt = now()) }
                ImportResult("검사매뉴얼 PDF를 가져왔습니다(${texts.size}쪽, 목차 ${toc.size}개).")
            }
            "hwp", "hwpx" -> {
                val src = copyAs(file, "manual." + file.extension.lowercase())
                val titles = runCatching { splitManualBlocks(HwpText.read(src)).keys.toList() }.getOrDefault(emptyList())
                save { it.copy(manualName = file.name, manualSrc = src.absolutePath, manualTitles = titles, importedAt = now()) }
                val pdf = File(dir, "manual.pdf")
                when (val r = Hancom.toPdf(src, pdf)) {
                    is Hancom.Result.Ok -> {
                        val texts = indexPdf(pdf, "manual")
                        val toc = mapToc(titles, texts).ifEmpty { outlineToc(pdf) }
                        save { it.copy(manualPdf = pdf.absolutePath, manualToc = toc) }
                        ImportResult("검사매뉴얼을 가져왔습니다(${texts.size}쪽, 목차 ${toc.size}개).")
                    }
                    is Hancom.Result.Failed -> {
                        save { it.copy(manualPdf = "", manualToc = emptyList()) }
                        ImportResult("한글 원본은 저장했지만 PDF로 바꾸지 못했습니다. ${r.message} 한글에서 PDF로 저장한 파일을 'PDF 지정'으로 넣어 주세요.", pdfMissing = true)
                    }
                }
            }
            else -> throw IllegalArgumentException("한글(HWP·HWPX) 또는 PDF 파일을 골라 주세요.")
        }
    }

    // ---------- 검사관련 서식 ----------

    suspend fun importForms(file: File): ImportResult = withContext(Dispatchers.IO) {
        dir.mkdirs()
        when (file.extension.lowercase()) {
            "pdf" -> {
                val pdf = copyAs(file, "forms.pdf")
                val texts = indexPdf(pdf, "forms")
                val marks = _data.value.formMarks.ifEmpty { marksFromPages(texts) }
                val spans = formSpans(texts)
                save { it.copy(formsPdf = pdf.absolutePath, formMarks = marks, formPages = spans, formsName = it.formsName.ifBlank { file.name }, importedAt = now()) }
                ImportResult("서식 PDF를 가져왔습니다(서식 ${spans.size}종)." + if (!_data.value.canWriteForms) " 한글로 작성하려면 서식 한글 파일도 가져와 주세요." else "")
            }
            "hwp", "hwpx" -> {
                val src = copyAs(file, "forms." + file.extension.lowercase())
                val (_, marks) = splitFormBlocks(HwpText.read(src))
                if (marks.isEmpty()) throw IllegalArgumentException("'< 제0-0호 서식 >' 표시가 있는 서식 문서가 아닙니다.")
                File(dir, "cut").deleteRecursively()
                save { it.copy(formsName = file.name, formsSrc = src.absolutePath, formMarks = marks, importedAt = now()) }
                val pdf = File(dir, "forms.pdf")
                when (val r = Hancom.toPdf(src, pdf)) {
                    is Hancom.Result.Ok -> {
                        val spans = formSpans(indexPdf(pdf, "forms"))
                        save { it.copy(formsPdf = pdf.absolutePath, formPages = spans) }
                        ImportResult("검사관련 서식을 가져왔습니다(서식 ${marks.map { it.no }.distinct().size}종).")
                    }
                    is Hancom.Result.Failed -> {
                        save { it.copy(formsPdf = "", formPages = emptyMap()) }
                        ImportResult("한글 원본은 저장해 서식 작성은 됩니다. 다만 PDF로 바꾸지 못해 원본 보기가 안 됩니다. ${r.message} 한글에서 PDF로 저장한 파일을 'PDF 지정'으로 넣어 주세요.", pdfMissing = true)
                    }
                }
            }
            else -> throw IllegalArgumentException("한글(HWP·HWPX) 또는 PDF 파일을 골라 주세요.")
        }
    }

    /** 원본 서식 파일에서 [no] 서식 부분만 남긴 한글 파일(앱 데이터 폴더에 한 번 만들어 두고 다시 쓴다) */
    suspend fun formOriginal(no: String, lib: LibraryData = _data.value): Hancom.Result {
        val src = File(lib.formsSrc)
        if (!src.isFile) return Hancom.Result.Failed("서식 한글 원본이 없습니다. 검사관련 서식 화면에서 한글 파일을 가져와 주세요.")
        val range = formRange(lib.formMarks, no) ?: return Hancom.Result.Failed("제${no}호 서식 표시를 원본에서 찾지 못했습니다.")
        val out = File(dir, "cut/제${no}호.${src.extension.lowercase()}")
        if (out.isFile && out.lastModified() >= src.lastModified() && out.length() > 0) return Hancom.Result.Ok(out)
        return Hancom.extract(src, out, range.first, range.second)
    }

    fun clear(kind: String) {
        if (kind == "manual") save { it.copy(manualName = "", manualSrc = "", manualPdf = "", manualTitles = emptyList(), manualToc = emptyList()) }
        else save { it.copy(formsName = "", formsSrc = "", formsPdf = "", formMarks = emptyList(), formPages = emptyMap()) }
        dir.listFiles { f -> f.name.startsWith("$kind.") }?.forEach { it.delete() }
        if (kind == "forms") File(dir, "cut").deleteRecursively()
    }

    // ---------- 찾기 ----------

    data class Hit(val page: Int, val snippet: String)

    /** PDF 글자에서 [query]가 들어 있는 쪽과 앞뒤 글 */
    fun search(kind: String, query: String, limit: Int = 200): List<Hit> {
        val q = squash(query)
        if (q.length < 2) return emptyList()
        return pageTexts(kind).withIndex().mapNotNull { (i, t) ->
            val flat = t.replace(Regex("""\s+"""), " ")
            val idx = indexIgnoringSpace(flat, q)
            if (idx < 0) null else Hit(i, "…" + flat.substring((idx - 30).coerceAtLeast(0), (idx + 60).coerceAtMost(flat.length)).trim() + "…")
        }.take(limit)
    }

    private fun copyAs(file: File, name: String): File {
        val prefix = name.substringBefore('.') + "."
        if (name.endsWith(".pdf")) File(dir, name).delete()
        else dir.listFiles { f -> f.name.startsWith(prefix) && (f.extension == "hwp" || f.extension == "hwpx") }?.forEach { it.delete() }
        return File(dir, name).also { file.copyTo(it, overwrite = true) }
    }

    /** PDF 쪽별 글자를 뽑아 파일로 남기고 돌려준다. */
    private fun indexPdf(pdf: File, kind: String): List<String> {
        val texts = Loader.loadPDF(pdf).use { doc ->
            val stripper = PDFTextStripper()
            (1..doc.numberOfPages).map { p ->
                stripper.startPage = p
                stripper.endPage = p
                runCatching { stripper.getText(doc) }.getOrDefault("").replace('\u000C', ' ')
            }
        }
        runCatching { pagesFile(kind).writeText(texts.joinToString("\u000C"), Charsets.UTF_8) }
        return texts
    }

    private fun outlineToc(pdf: File): List<TocEntry> = runCatching {
        Loader.loadPDF(pdf).use { doc ->
            val out = mutableListOf<TocEntry>()
            fun walk(item: PDOutlineItem?, depth: Int) {
                var cur = item
                while (cur != null) {
                    val node = cur
                    val page = runCatching { node.findDestinationPage(doc)?.let { p -> doc.pages.indexOf(p) } }.getOrNull()
                    if (page != null && page >= 0 && depth <= 1) out += TocEntry(node.title.orEmpty().trim(), page)
                    walk(node.firstChild, depth + 1)
                    cur = node.nextSibling
                }
            }
            walk(doc.documentCatalog.documentOutline?.firstChild, 0)
            out
        }
    }.getOrDefault(emptyList())

    private fun now() = System.currentTimeMillis()

    companion object {
        fun squash(s: String) = s.filter { it.isLetterOrDigit() }

        /** 띄어쓰기를 무시하고 [needle](이미 squash한 글자)을 찾아 원문 위치를 돌려준다. */
        fun indexIgnoringSpace(text: String, needle: String): Int {
            if (needle.isEmpty()) return -1
            val map = ArrayList<Int>(text.length)
            val sb = StringBuilder()
            text.forEachIndexed { i, c -> if (c.isLetterOrDigit()) { sb.append(c.lowercaseChar()); map += i } }
            val k = sb.indexOf(needle.lowercase())
            return if (k < 0) -1 else map[k]
        }

        /**
         * 절 제목을 PDF 쪽에 맞춘다. 제목이 여러 개 한꺼번에 나오는 쪽(차례)은 건너뛰고,
         * 문서 순서대로 앞 절보다 뒤에서 처음 나오는 쪽을 그 절의 시작으로 본다.
         */
        fun mapToc(titles: List<String>, pages: List<String>): List<TocEntry> {
            if (titles.isEmpty() || pages.isEmpty()) return emptyList()
            val flat = pages.map { squash(it) }
            val keys = titles.map { squash(it) }.filter { it.length >= 2 }
            val tocPages = flat.indices.filter { p -> keys.count { flat[p].contains(it) } >= 5 }.toSet()
            var cursor = 0
            return titles.mapNotNull { t ->
                val k = squash(t)
                if (k.length < 2) return@mapNotNull null
                val p = (cursor until flat.size).firstOrNull { it !in tocPages && flat[it].contains(k) } ?: return@mapNotNull null
                cursor = p
                TocEntry(t, p)
            }
        }

        private val MARK_FLAT = Regex("""<제(\d+)-(\d+)호서식>""")

        /** PDF 쪽 글자에서 서식 표시를 순서대로 찾는다(한글 원본이 없을 때). */
        fun marksFromPages(pages: List<String>): List<FormMark> = pages.flatMap { t ->
            MARK_FLAT.findAll(t.filterNot { it.isWhitespace() }).map { m -> FormMark("${m.groupValues[1]}-${m.groupValues[2]}", "< 제${m.groupValues[1]}-${m.groupValues[2]}호 서식 >") }.toList()
        }

        /**
         * 서식마다 PDF 쪽 범위를 정한다. 다음 서식 표시가 쪽 맨 위가 아니라 중간에 있으면 그 쪽까지 이 서식에 넣는다.
         * 같은 번호가 다시 나오면(이어지는 부분) 범위를 넓힌다.
         */
        fun formSpans(pages: List<String>): Map<String, PageSpan> {
            data class Hit(val no: String, val page: Int, val top: Boolean)
            val hits = pages.flatMapIndexed { p, t ->
                val flat = t.filterNot { it.isWhitespace() }
                MARK_FLAT.findAll(flat).map { m -> Hit("${m.groupValues[1]}-${m.groupValues[2]}", p, m.range.first < 40) }.toList()
            }
            val out = LinkedHashMap<String, PageSpan>()
            hits.forEachIndexed { i, h ->
                val next = hits.drop(i + 1).firstOrNull { it.no != h.no }
                val end = when {
                    next == null -> pages.size - 1
                    next.page == h.page -> h.page
                    next.top -> next.page - 1
                    else -> next.page
                }.coerceAtLeast(h.page)
                val old = out[h.no]
                out[h.no] = if (old == null) PageSpan(h.page, end) else PageSpan(minOf(old.start, h.page), maxOf(old.end, end))
            }
            return out
        }

        private val SECTION_NO = Regex("""^\s*(0[1-9]|1[0-9]|2[0-9])\s*$""")
        private val FORM_MARK = Regex("""<\s*제\s*(\d+)\s*-\s*(\d+)\s*호\s*서식\s*>""")
        private val CHAPTER = Regex("""^\s*제\d+장(\s.*)?$""")

        /** 매뉴얼을 "01 ↵ 제목" 꼴의 절 머리로 나눈다(같은 제목이 다시 나오면 이어 붙인다). */
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

        /** 서식 문서를 "< 제2-4호 서식 >" 문단으로 나눈다. */
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

        /** 한글에서 찾을 시작·끝 표시. 같은 글자가 앞에 또 있으면 몇 번째인지(nth)로 가린다. */
        fun formRange(marks: List<FormMark>, no: String): Pair<Hancom.Mark, Hancom.Mark?>? {
            val i = marks.indexOfFirst { it.no == no }
            if (i < 0) return null
            fun nth(k: Int) = marks.subList(0, k + 1).count { it.text == marks[k].text }
            val j = (i + 1 until marks.size).firstOrNull { marks[it].no != no }
            return Hancom.Mark(marks[i].text, nth(i)) to j?.let { Hancom.Mark(marks[it].text, nth(it)) }
        }
    }
}

/** 작성한 문서를 두는 곳: 문서\FSS_Helper\서식 */
object DocFiles {
    fun safe(s: String) = s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().take(80).ifBlank { "문서" }

    val formsDir: File get() = File(AppDirs.exports, "서식").apply { mkdirs() }

    /** 같은 이름이 있으면 " (2)"를 붙인 새 이름 */
    fun unique(dir: File, name: String): File {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var f = File(dir, name)
        var n = 2
        while (f.exists()) { f = File(dir, "$base ($n)" + if (ext.isNotEmpty()) ".$ext" else ""); n++ }
        return f
    }
}
