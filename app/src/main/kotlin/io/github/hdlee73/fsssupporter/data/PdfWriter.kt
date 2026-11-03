package io.github.hdlee73.fsssupporter.data

import com.hdlee73.everylaw.core.domain.HwpxParagraph
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import java.awt.Color
import java.io.File
import java.io.OutputStream

/**
 * 한글 내보내기와 같은 문단·표 목록([HwpxParagraph])을 A4 PDF로 그린다.
 * 글꼴은 Windows의 맑은 고딕을 문서에 필요한 글자만 넣어(부분 포함) 쓴다.
 */
object PdfWriter {
    private val FONT_CANDIDATES = listOf(
        "C:/Windows/Fonts/malgun.ttf", "C:/Windows/Fonts/NanumGothic.ttf", "C:/Windows/Fonts/gulim.ttc",
        "/usr/share/fonts/truetype/nanum/NanumGothic.ttf", "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
    )

    class NoFontException : Exception("한글 글꼴(맑은 고딕)을 찾지 못해 PDF를 만들 수 없습니다.")

    private fun loadFont(doc: PDDocument, extra: File?): PDFont {
        val files = listOfNotNull(extra) + FONT_CANDIDATES.map(::File)
        for (f in files.filter { it.isFile }) {
            val font = runCatching {
                if (f.name.lowercase().endsWith(".ttc")) {
                    var picked: PDFont? = null
                    // 모음 파일은 PDF를 저장할 때까지 열어 둬야 글자를 넣을 수 있다.
                    org.apache.fontbox.ttf.TrueTypeCollection(f).processAllFonts { ttf -> if (picked == null) picked = PDType0Font.load(doc, ttf, true) }
                    picked
                } else PDType0Font.load(doc, f)
            }.getOrNull()
            if (font != null) return font
        }
        throw NoFontException()
    }

    private const val MARGIN = 50f
    private val A4 = PDRectangle.A4

    fun write(title: String, paragraphs: List<HwpxParagraph>, out: OutputStream, footer: String = "", fontFile: File? = null) {
        PDDocument().use { doc ->
            val font = loadFont(doc, fontFile)
            val ok = HashMap<Char, Boolean>()
            fun clean(s: String): String = buildString {
                s.forEach { ch ->
                    val good = ok.getOrPut(ch) { ch == ' ' || runCatching { font.encode(ch.toString()); true }.getOrDefault(false) }
                    append(if (good) ch else if (ch == '\t') ' ' else '·')
                }
            }
            fun width(s: String, size: Float) = font.getStringWidth(s) / 1000f * size
            fun wrap(text: String, size: Float, maxW: Float): List<String> {
                val out = mutableListOf<String>()
                text.split('\n').forEach { raw ->
                    val line = clean(raw)
                    if (line.isEmpty()) { out += ""; return@forEach }
                    var cur = StringBuilder()
                    var lastSpace = -1
                    for (ch in line) {
                        cur.append(ch)
                        if (ch == ' ') lastSpace = cur.length - 1
                        if (width(cur.toString(), size) > maxW && cur.length > 1) {
                            // 띄어쓰기에서 끊을 수 있으면 거기서, 아니면 글자 단위로
                            if (lastSpace > 0 && lastSpace > cur.length / 2) {
                                out += cur.substring(0, lastSpace)
                                cur = StringBuilder(cur.substring(lastSpace + 1))
                            } else {
                                out += cur.substring(0, cur.length - 1)
                                cur = StringBuilder(ch.toString())
                            }
                            lastSpace = -1
                        }
                    }
                    out += cur.toString()
                }
                return out
            }

            val contentW = A4.width - MARGIN * 2
            var page = PDPage(A4)
            doc.addPage(page)
            var cs = PDPageContentStream(doc, page)
            var y = A4.height - MARGIN
            var pageNo = 1
            fun footerLine() {
                if (footer.isBlank() && pageNo == 0) return
                cs.beginText(); cs.setFont(font, 8f); cs.setNonStrokingColor(Color.GRAY)
                cs.newLineAtOffset(MARGIN, 28f); cs.showText(clean("$footer   - $pageNo -".trim())); cs.endText()
                cs.setNonStrokingColor(Color.BLACK)
            }
            fun newPage() {
                footerLine()
                cs.close()
                page = PDPage(A4); doc.addPage(page); pageNo++
                cs = PDPageContentStream(doc, page)
                y = A4.height - MARGIN
            }
            fun ensure(h: Float) { if (y - h < MARGIN + 10) newPage() }
            fun textAt(s: String, x: Float, baseline: Float, size: Float, color: Color = Color.BLACK) {
                if (s.isEmpty()) return
                cs.beginText(); cs.setFont(font, size); cs.setNonStrokingColor(color); cs.newLineAtOffset(x, baseline); cs.showText(s); cs.endText()
                cs.setNonStrokingColor(Color.BLACK)
            }

            // 제목
            wrap(title, 16f, contentW).forEach { l -> ensure(24f); textAt(l, MARGIN, y - 16f, 16f); y -= 24f }
            y -= 6f

            var i = 0
            while (i < paragraphs.size) {
                val p = paragraphs[i]
                if (p.cells != null) {
                    // 이어진 표 줄을 한 표로
                    val rows = mutableListOf<List<String>>()
                    while (i < paragraphs.size && paragraphs[i].cells != null) { rows += paragraphs[i].cells!!; i++ }
                    val cols = rows.maxOf { it.size }.coerceAtLeast(1)
                    val size = 8.5f
                    val lead = size * 1.35f
                    // 칸 너비: 글자 양에 비례하되 너무 좁지 않게
                    val weights = (0 until cols).map { c -> rows.maxOf { r -> width(clean(r.getOrNull(c).orEmpty()), size) }.coerceIn(30f, 220f) }
                    val total = weights.sum()
                    val widths = weights.map { it / total * contentW }
                    rows.forEachIndexed { ri, r ->
                        val lines = (0 until cols).map { c -> wrap(r.getOrNull(c).orEmpty(), size, widths[c] - 6f) }
                        val h = lines.maxOf { it.size } * lead + 6f
                        ensure(h)
                        var x = MARGIN
                        (0 until cols).forEach { c ->
                            if (ri == 0) { cs.setNonStrokingColor(Color(0xE6, 0xF4, 0xEC)); cs.addRect(x, y - h, widths[c], h); cs.fill(); cs.setNonStrokingColor(Color.BLACK) }
                            cs.setStrokingColor(Color(0xB0, 0xB0, 0xB0)); cs.setLineWidth(0.5f)
                            cs.addRect(x, y - h, widths[c], h); cs.stroke()
                            lines[c].forEachIndexed { li, l -> textAt(l, x + 3f, y - 3f - size - li * lead, size) }
                            x += widths[c]
                        }
                        y -= h
                    }
                    y -= 6f
                    continue
                }
                val (size, color, gapBefore) = when (p.style) {
                    HwpxParagraph.Style.TITLE -> Triple(15f, Color.BLACK, 6f)
                    HwpxParagraph.Style.SUBTITLE -> Triple(11f, Color.DARK_GRAY, 2f)
                    HwpxParagraph.Style.SECTION -> Triple(12f, Color(0x0B, 0x6E, 0x4F), 8f)
                    HwpxParagraph.Style.BOLD -> Triple(10.5f, Color.BLACK, 3f)
                    HwpxParagraph.Style.META, HwpxParagraph.Style.GRAY -> Triple(8.5f, Color.GRAY, 0f)
                    HwpxParagraph.Style.BODY -> Triple(10f, Color.BLACK, 0f)
                }
                val lead = size * 1.45f
                val indent = p.depth * 12f
                y -= gapBefore
                wrap(p.text, size, contentW - indent).forEach { l -> ensure(lead); textAt(l, MARGIN + indent, y - size, size, color); y -= lead }
                i++
            }
            footerLine()
            cs.close()
            doc.save(out)
        }
    }
}
