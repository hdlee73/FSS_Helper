package io.github.hdlee73.fsssupporter.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * 한글 프로그램 없이 HWPX(압축된 XML) 원본에서 한 서식 부분만 남긴 사본을 만든다(이음오피스만 있는 PC용).
 * 본문 문단(hp:p)을 그대로 두고 앞뒤 문단만 지우므로 표·글꼴·문단 모양은 원본 그대로다.
 * 구역 설정(쪽 크기·여백, hp:secPr)은 남은 첫 문단으로 옮겨 쪽 모양을 지킨다.
 */
object HwpxCut {
    private class Para(val section: Int, val el: Element, val text: String)

    /** [src]에서 [start]가 있는 문단부터 [end]가 있는 문단 앞까지만 남겨 [out]에 쓴다. */
    fun cut(src: File, out: File, start: Hancom.Mark, end: Hancom.Mark?): Hancom.Result = runCatching {
        ZipFile(src).use { zip ->
            val sectionNames = zip.entries().toList().map { it.name }.filter { SECTION.matches(it) }.sortedBy { num(it) }
            if (sectionNames.isEmpty()) return Hancom.Result.Failed("HWPX 본문을 찾지 못했습니다.")
            val docs = sectionNames.map { n -> zip.getInputStream(zip.getEntry(n)).use { builder().parse(it) } }
            val paras = docs.flatMapIndexed { i, d -> d.documentElement.kids().filter { it.local == "p" }.map { Para(i, it, textOf(it)) } }
            val from = findPara(paras, start) ?: return Hancom.Result.Failed("원본에서 '${start.text}'를 찾지 못했습니다.")
            val to = end?.let { findPara(paras, it) }?.takeIf { it > from } ?: paras.size
            val keep = paras.subList(from, to).toSet()

            // 구역마다 남길 문단만 두고, 구역 설정이 든 첫 문단이 지워지면 그 설정을 남은 첫 문단 앞에 옮긴다.
            val keptSections = mutableListOf<Int>()
            docs.forEachIndexed { i, d ->
                val all = paras.filter { it.section == i }
                val kept = all.filter { it in keep }
                if (kept.isEmpty()) return@forEachIndexed
                keptSections += i
                val first = all.first()
                if (first !in keep) {
                    val secRuns = first.el.kids().filter { r -> r.local == "run" && r.descendants().any { it.local == "secPr" } }
                    val target = kept.first().el
                    secRuns.reversed().forEach { r ->
                        val copy = r.cloneNode(true) as Element
                        copy.kids().filter { it.local != "secPr" && it.local != "ctrl" }.forEach { copy.removeChild(it) }
                        target.insertBefore(copy, target.kids().firstOrNull { it.local == "run" } ?: target.firstChild)
                    }
                }
                all.filter { it !in keep }.forEach { it.el.parentNode.removeChild(it.el) }
            }
            if (keptSections.isEmpty()) return Hancom.Result.Failed("남길 내용이 없습니다.")

            // 남은 구역을 section0부터 다시 번호 매기고 목록(content.hpf)·구역 수(header.xml)를 맞춘다.
            val rename = keptSections.withIndex().associate { (newNo, old) -> sectionNames[old] to "Contents/section$newNo.xml" }
            val dropped = sectionNames.filter { it !in rename }
            out.parentFile?.mkdirs()
            val tmp = File(out.parentFile, out.name + ".part")
            ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                val names = zip.entries().toList().map { it.name }
                // mimetype은 맨 앞에 압축하지 않고 넣어야 한다.
                (listOfNotNull(names.firstOrNull { it == "mimetype" }) + names.filter { it != "mimetype" }).forEach { name ->
                    if (name in dropped) return@forEach
                    val bytes: ByteArray = when {
                        name in rename -> xml(docs[sectionNames.indexOf(name)])
                        name.endsWith("content.hpf") -> fixManifest(zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }, rename, dropped)
                        name == "Contents/header.xml" -> fixHeader(zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }, keptSections.size)
                        else -> zip.getInputStream(zip.getEntry(name)).use { it.readBytes() }
                    }
                    val outName = rename[name] ?: name
                    val e = ZipEntry(outName)
                    if (name == "mimetype") {
                        e.method = ZipEntry.STORED; e.size = bytes.size.toLong(); e.compressedSize = bytes.size.toLong()
                        e.crc = CRC32().apply { update(bytes) }.value
                    }
                    zos.putNextEntry(e); zos.write(bytes); zos.closeEntry()
                }
            }
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
            Hancom.Result.Ok(out)
        }
    }.getOrElse { Hancom.Result.Failed("HWPX 서식을 나누지 못했습니다: ${it.message}") }

    /** [m]의 n번째 글자가 들어 있는 문단 번호. 문단 안 띄어쓰기는 무시한다. */
    private fun findPara(paras: List<Para>, m: Hancom.Mark): Int? {
        val q = m.text.filterNot(Char::isWhitespace)
        if (q.isEmpty()) return null
        var seen = 0
        paras.forEachIndexed { i, p ->
            val t = p.text.filterNot(Char::isWhitespace)
            var at = t.indexOf(q)
            while (at >= 0) {
                if (++seen >= m.nth) return i
                at = t.indexOf(q, at + q.length)
            }
        }
        return null
    }

    private fun textOf(p: Element): String = StringBuilder().also { sb -> p.descendants().filter { it.local == "t" }.forEach { sb.append(it.textContent) } }.toString()

    private fun fixManifest(bytes: ByteArray, rename: Map<String, String>, dropped: List<String>): ByteArray {
        val doc = builder().parse(bytes.inputStream())
        val items = doc.documentElement.descendants().filter { it.local == "item" }
        val droppedIds = mutableSetOf<String>()
        items.forEach { item ->
            val href = item.getAttribute("href")
            when {
                href in dropped -> { droppedIds += item.getAttribute("id"); item.parentNode.removeChild(item) }
                href in rename -> item.setAttribute("href", rename.getValue(href))
            }
        }
        doc.documentElement.descendants().filter { it.local == "itemref" && it.getAttribute("idref") in droppedIds }.forEach { it.parentNode.removeChild(it) }
        return xml(doc)
    }

    private fun fixHeader(bytes: ByteArray, count: Int): ByteArray {
        val doc = builder().parse(bytes.inputStream())
        if (!doc.documentElement.hasAttribute("secCnt")) return bytes
        doc.documentElement.setAttribute("secCnt", count.toString())
        return xml(doc)
    }

    private val SECTION = Regex("""Contents/section\d+\.xml""")
    private fun num(name: String) = name.filter(Char::isDigit).toIntOrNull() ?: 0

    private fun builder() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }.newDocumentBuilder()

    private fun xml(doc: Document): ByteArray {
        val t = TransformerFactory.newInstance().newTransformer()
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        t.setOutputProperty(OutputKeys.INDENT, "no")
        doc.xmlStandalone = true
        return ByteArrayOutputStream().also { t.transform(DOMSource(doc), StreamResult(it)) }.toByteArray()
    }

    private fun org.w3c.dom.Node.kids(): List<Element> = (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }
    private fun Element.descendants(): List<Element> = kids().flatMap { listOf(it) + it.descendants() }
    private val Element.local: String get() = localName ?: tagName.substringAfter(':')
}
