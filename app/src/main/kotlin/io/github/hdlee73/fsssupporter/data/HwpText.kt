package io.github.hdlee73.fsssupporter.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.ZipFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 한글 문서의 한 덩어리: 문단 또는 표 */
@Serializable
sealed interface HBlock

/** 문단. [hasText]가 false면 글자 레코드가 없는 빈 문단(빈 줄) */
@Serializable @SerialName("p")
data class HPara(val text: String, val hasText: Boolean = true) : HBlock

/** 표. 칸은 (행, 열)과 합친 칸 수를 가진다. */
@Serializable @SerialName("t")
data class HTable(val rows: Int, val cols: Int, val cells: List<HCell>) : HBlock

@Serializable
data class HCell(
    val row: Int,
    val col: Int,
    val rowSpan: Int = 1,
    val colSpan: Int = 1,
    val blocks: List<HBlock> = emptyList(),
    /** 칸 너비(HWPUNIT, 1/7200인치). 0이면 모름 */
    val width: Int = 0,
)

/**
 * 한글 문서(HWP 5.0, HWPX)를 문단·표 구조([HBlock])로 읽거나 글자만 뽑는다([extract], 표 칸은 한 줄씩).
 * HWP 5.0은 OLE 복합 문서 안의 BodyText/SectionN 스트림(압축된 레코드)에서 문단 글자(HWPTAG_PARA_TEXT)를 읽는다.
 */
object HwpText {
    class HwpException(message: String) : Exception(message)

    fun extract(file: File): String = flatten(read(file))

    /** 문단·표 구조로 읽는다(표 칸의 행·열·합치기 포함). */
    fun read(file: File): List<HBlock> {
        val head = file.inputStream().use { it.readNBytes(8) }
        return when {
            head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() -> hwpx(file)
            head.size == 8 && head.contentEquals(OLE_MAGIC) -> hwp5Blocks(file.readBytes())
            else -> throw HwpException("한글(HWP·HWPX) 문서가 아닙니다.")
        }
    }

    private val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    // ---------- HWPX ----------
    private fun hwpx(file: File): List<HBlock> = ZipFile(file).use { zip ->
        val sections = zip.entries().toList().filter { Regex("""Contents/section\d+\.xml""").matches(it.name) }
            .sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: 0 }
        if (sections.isEmpty()) throw HwpException("HWPX 본문을 찾지 못했습니다.")
        val f = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        sections.flatMap { e ->
            val doc = zip.getInputStream(e).use { f.newDocumentBuilder().parse(it) }
            xmlParas(doc.documentElement)
        }
    }

    private fun org.w3c.dom.Node.kids(): List<org.w3c.dom.Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? org.w3c.dom.Element }

    private val org.w3c.dom.Element.local: String get() = localName ?: tagName.substringAfter(':')

    /** 컨테이너 바로 아래 문단(hp:p)들을 블록으로 */
    private fun xmlParas(container: org.w3c.dom.Element): List<HBlock> {
        val out = mutableListOf<HBlock>()
        container.kids().filter { it.local == "p" }.forEach { p ->
            val text = StringBuilder()
            val tables = mutableListOf<org.w3c.dom.Element>()
            fun walk(el: org.w3c.dom.Element) {
                when (el.local) {
                    "t" -> text.append(el.textContent)
                    "lineBreak" -> text.append('\n')
                    "tab" -> text.append('\t')
                    "tbl" -> tables += el
                    "subList", "secPr", "ctrl" -> Unit
                    else -> el.kids().forEach(::walk)
                }
            }
            p.kids().forEach(::walk)
            out += HPara(text.toString())
            tables.forEach { tbl ->
                val cells = tbl.kids().filter { it.local == "tr" }.flatMap { it.kids().filter { c -> c.local == "tc" } }.map { tc ->
                    val addr = tc.kids().firstOrNull { it.local == "cellAddr" }
                    val span = tc.kids().firstOrNull { it.local == "cellSpan" }
                    val sub = tc.kids().firstOrNull { it.local == "subList" }
                    val sz = tc.kids().firstOrNull { it.local == "cellSz" }
                    HCell(
                        row = addr?.getAttribute("rowAddr")?.toIntOrNull() ?: 0,
                        col = addr?.getAttribute("colAddr")?.toIntOrNull() ?: 0,
                        rowSpan = span?.getAttribute("rowSpan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        colSpan = span?.getAttribute("colSpan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        blocks = sub?.let(::xmlParas).orEmpty(),
                        width = sz?.getAttribute("width")?.toIntOrNull() ?: 0,
                    )
                }
                out += HTable(tbl.getAttribute("rowCnt").toIntOrNull() ?: (cells.maxOfOrNull { it.row + it.rowSpan } ?: 0),
                    tbl.getAttribute("colCnt").toIntOrNull() ?: (cells.maxOfOrNull { it.col + it.colSpan } ?: 0), cells)
            }
        }
        return out
    }

    // ---------- HWP 5.0 ----------
    fun hwp5(bytes: ByteArray): String = flatten(hwp5Blocks(bytes))

    fun hwp5Blocks(bytes: ByteArray): List<HBlock> {
        val cfb = Cfb(bytes)
        val header = cfb.stream("FileHeader") ?: throw HwpException("HWP 머리 정보를 찾지 못했습니다.")
        val flags = if (header.size >= 40) ByteBuffer.wrap(header, 36, 4).order(ByteOrder.LITTLE_ENDIAN).int else 0
        if (flags and 0x2 != 0) throw HwpException("암호가 걸린 문서는 읽을 수 없습니다.")
        if (flags and 0x4 != 0) throw HwpException("배포용 문서는 읽을 수 없습니다. 한글에서 일반 문서로 저장해 주세요.")
        val compressed = flags and 0x1 != 0
        val sections = cfb.names().mapNotNull { n -> Regex("""BodyText/Section(\d+)""").matchEntire(n)?.let { it.groupValues[1].toInt() to n } }.sortedBy { it.first }
        if (sections.isEmpty()) throw HwpException("본문을 찾지 못했습니다.")
        return sections.flatMap { (_, name) ->
            var data = cfb.stream(name) ?: return@flatMap emptyList()
            if (compressed) data = inflate(data)
            parseRecords(data)
        }
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inf = Inflater(true)
        inf.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buf = ByteArray(64 * 1024)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        return out.toByteArray()
    }

    /** 레코드 머리(태그·수준·크기)를 차례로 읽어 문단·표 구조로 만든다. 표 칸은 LIST_HEADER(72)로 시작하고 그 칸의 문단은 같은 수준에 놓인다. */
    private fun parseRecords(data: ByteArray): List<HBlock> {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val root = Frame.Body(0)
        val stack = ArrayDeque<Frame>().apply { addLast(root) }
        fun container(): Frame.Container = stack.lastOrNull { it is Frame.Container } as Frame.Container
        fun closeTable(t: Frame.Table) {
            stack.removeLast()
            container().blocks += HTable(t.rows, t.cols, t.cells.toList())
        }
        var i = 0
        while (i + 4 <= data.size) {
            val h = bb.getInt(i); i += 4
            val tag = h and 0x3FF
            val level = (h ushr 10) and 0x3FF
            var size = (h ushr 20) and 0xFFF
            if (size == 0xFFF) { if (i + 4 > data.size) break; size = bb.getInt(i); i += 4 }
            if (size < 0 || i + size > data.size) break
            // 이 레코드를 담지 않는 칸·표를 닫는다
            while (true) {
                val top = stack.last()
                when {
                    top is Frame.Cell && (level < top.level || (level == top.level && tag == TAG_LIST_HEADER)) -> {
                        stack.removeLast()
                        (stack.last() as Frame.Table).cells += HCell(top.row, top.col, top.rowSpan, top.colSpan, top.blocks.toList(), top.width)
                    }
                    top is Frame.Table && level <= top.level -> closeTable(top)
                    else -> break
                }
            }
            when (tag) {
                TAG_PARA_HEADER -> container().blocks += HPara("", hasText = false)
                TAG_PARA_TEXT -> {
                    val sb = StringBuilder()
                    paraText(bb, i, size, sb)
                    val c = container()
                    val text = sb.toString().removeSuffix("\n")
                    val last = c.blocks.lastOrNull()
                    if (last is HPara && !last.hasText) c.blocks[c.blocks.size - 1] = HPara(text) else c.blocks += HPara(text)
                }
                TAG_CTRL_HEADER -> if (size >= 4 && bb.getInt(i) == TBL_ID) stack.addLast(Frame.Table(level))
                TAG_TABLE -> (stack.last() as? Frame.Table)?.let { t ->
                    if (size >= 8) { t.rows = bb.getShort(i + 4).toInt() and 0xFFFF; t.cols = bb.getShort(i + 6).toInt() and 0xFFFF }
                }
                TAG_LIST_HEADER -> (stack.last() as? Frame.Table)?.let { t ->
                    if (level == t.level + 1 && size >= 16) {
                        fun u16(o: Int) = bb.getShort(i + o).toInt() and 0xFFFF
                        val width = if (size >= 20) bb.getInt(i + 16) else 0
                        stack.addLast(Frame.Cell(level, row = u16(10), col = u16(8), colSpan = u16(12).coerceAtLeast(1), rowSpan = u16(14).coerceAtLeast(1), width = width.coerceAtLeast(0)))
                    }
                }
            }
            i += size
        }
        while (stack.size > 1) {
            when (val top = stack.last()) {
                is Frame.Cell -> { stack.removeLast(); (stack.last() as Frame.Table).cells += HCell(top.row, top.col, top.rowSpan, top.colSpan, top.blocks.toList(), top.width) }
                is Frame.Table -> closeTable(top)
                else -> stack.removeLast()
            }
        }
        return root.blocks
    }

    private sealed class Frame(val level: Int) {
        sealed class Container(level: Int) : Frame(level) { val blocks = mutableListOf<HBlock>() }
        class Body(level: Int) : Container(level)
        class Cell(level: Int, val row: Int, val col: Int, val colSpan: Int, val rowSpan: Int, val width: Int) : Container(level)
        class Table(level: Int) : Frame(level) { var rows = 0; var cols = 0; val cells = mutableListOf<HCell>() }
    }

    private const val TAG_PARA_HEADER = 66
    private const val TAG_PARA_TEXT = 67
    private const val TAG_CTRL_HEADER = 71
    private const val TAG_LIST_HEADER = 72
    private const val TAG_TABLE = 77
    /** 'tbl ' 컨트롤 id(리틀 엔디언으로 읽은 값) */
    private const val TBL_ID = ('t'.code shl 24) or ('b'.code shl 16) or ('l'.code shl 8) or ' '.code

    /** 문단·표 구조를 예전과 같은 글자 흐름으로 편다(문단마다 줄바꿈, 표는 칸 순서대로). */
    fun flatten(blocks: List<HBlock>): String = StringBuilder().also { flattenTo(blocks, it) }.toString()

    private fun flattenTo(blocks: List<HBlock>, sb: StringBuilder) {
        blocks.forEach { b ->
            when (b) {
                is HPara -> if (b.hasText) sb.append(b.text).append('\n')
                is HTable -> b.cells.forEach { flattenTo(it.blocks, sb) }
            }
        }
    }

    private fun paraText(bb: ByteBuffer, start: Int, size: Int, sb: StringBuilder) {
        var j = start
        val end = start + size
        while (j + 1 < end) {
            val c = bb.getShort(j).toInt() and 0xFFFF
            if (c < 32) {
                when (c) {
                    0, 10, 13, in 24..31 -> { if (c == 10 || c == 13) sb.append('\n'); j += 2 }
                    else -> { if (c == 9) sb.append('\t'); j += 16 }
                }
            } else {
                sb.append(c.toChar()); j += 2
            }
        }
    }

    /** 최소한의 OLE 복합 문서(Compound File Binary) 읽기 */
    private class Cfb(private val b: ByteArray) {
        private val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        private val sectorSize = 1 shl (bb.getShort(0x1E).toInt() and 0xFFFF)
        private val miniSectorSize = 1 shl (bb.getShort(0x20).toInt() and 0xFFFF)
        private val miniCutoff = bb.getInt(0x38).toLong() and 0xFFFFFFFFL
        private val fat: IntArray
        private val miniFat: IntArray
        private val entries = mutableListOf<Entry>()
        private val paths = HashMap<String, Entry>()
        private var miniStream = ByteArray(0)

        class Entry(val name: String, val type: Int, val left: Int, val right: Int, val child: Int, val start: Int, val size: Long)

        init {
            val difat = mutableListOf<Int>()
            for (k in 0 until 109) bb.getInt(0x4C + k * 4).let { if (it >= 0) difat += it }
            var next = bb.getInt(0x44)
            var guard = 0
            while (next >= 0 && guard++ < 10_000) {
                val off = offset(next)
                val per = sectorSize / 4 - 1
                for (k in 0 until per) bb.getInt(off + k * 4).let { if (it >= 0) difat += it }
                next = bb.getInt(off + per * 4)
            }
            fat = IntArray(difat.size * sectorSize / 4)
            difat.forEachIndexed { s, sid -> val off = offset(sid); for (k in 0 until sectorSize / 4) fat[s * sectorSize / 4 + k] = bb.getInt(off + k * 4) }
            val dir = chain(bb.getInt(0x30), fat, sectorSize) { offset(it) }
            for (k in 0 until dir.size / 128) {
                val e = ByteBuffer.wrap(dir, k * 128, 128).order(ByteOrder.LITTLE_ENDIAN)
                val nameLen = (e.getShort(k * 128 + 64).toInt() and 0xFFFF).coerceIn(0, 64)
                val name = String(dir, k * 128, (nameLen - 2).coerceAtLeast(0), Charsets.UTF_16LE)
                entries += Entry(name, dir[k * 128 + 66].toInt(), e.getInt(k * 128 + 68), e.getInt(k * 128 + 72), e.getInt(k * 128 + 76),
                    e.getInt(k * 128 + 116), e.getInt(k * 128 + 120).toLong() and 0xFFFFFFFFL)
            }
            val mf = chain(bb.getInt(0x3C), fat, sectorSize) { offset(it) }
            miniFat = IntArray(mf.size / 4) { ByteBuffer.wrap(mf).order(ByteOrder.LITTLE_ENDIAN).getInt(it * 4) }
            entries.firstOrNull { it.type == 5 }?.let { root ->
                miniStream = chain(root.start, fat, sectorSize) { offset(it) }
                walk(root.child, "")
            }
        }

        private fun offset(sid: Int) = (sid + 1) * sectorSize

        private fun walk(id: Int, prefix: String, depth: Int = 0) {
            if (id < 0 || id >= entries.size || depth > 64) return
            val e = entries[id]
            walk(e.left, prefix, depth + 1)
            walk(e.right, prefix, depth + 1)
            val path = if (prefix.isEmpty()) e.name else "$prefix/${e.name}"
            paths[path] = e
            if (e.type == 1) walk(e.child, path, depth + 1)
        }

        private fun chain(start: Int, table: IntArray, size: Int, at: (Int) -> Int): ByteArray {
            val out = ByteArrayOutputStream()
            var sid = start
            var guard = 0
            while (sid >= 0 && sid < table.size && guard++ < 1_000_000) {
                val off = at(sid)
                if (off < 0 || off >= b.size) break
                out.write(b, off, minOf(size, b.size - off))
                sid = table[sid]
            }
            return out.toByteArray()
        }

        fun names(): Set<String> = paths.keys

        fun stream(path: String): ByteArray? {
            val e = paths[path] ?: return null
            val data = if (e.size < miniCutoff) {
                val out = ByteArrayOutputStream()
                var sid = e.start
                var guard = 0
                while (sid >= 0 && sid < miniFat.size && guard++ < 1_000_000) {
                    val off = sid * miniSectorSize
                    if (off >= miniStream.size) break
                    out.write(miniStream, off, minOf(miniSectorSize, miniStream.size - off))
                    sid = miniFat[sid]
                }
                out.toByteArray()
            } else chain(e.start, fat, sectorSize) { offset(it) }
            return data.copyOf(minOf(data.size.toLong(), e.size).toInt())
        }
    }
}
