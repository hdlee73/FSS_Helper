package io.github.hdlee73.fsssupporter.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import java.util.zip.ZipFile

/**
 * 한글 문서(HWP 5.0, HWPX)에서 글자만 뽑는다. 표 칸은 한 줄씩 나온다.
 * HWP 5.0은 OLE 복합 문서 안의 BodyText/SectionN 스트림(압축된 레코드)에서 문단 글자(HWPTAG_PARA_TEXT)를 읽는다.
 */
object HwpText {
    class HwpException(message: String) : Exception(message)

    fun extract(file: File): String {
        val head = file.inputStream().use { it.readNBytes(8) }
        return when {
            head.size >= 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() -> hwpx(file)
            head.size == 8 && head.contentEquals(OLE_MAGIC) -> hwp5(file.readBytes())
            else -> throw HwpException("한글(HWP·HWPX) 문서가 아닙니다.")
        }
    }

    private val OLE_MAGIC = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())

    // ---------- HWPX ----------
    private fun hwpx(file: File): String = ZipFile(file).use { zip ->
        val sections = zip.entries().toList().filter { Regex("""Contents/section\d+\.xml""").matches(it.name) }
            .sortedBy { it.name.filter(Char::isDigit).toIntOrNull() ?: 0 }
        if (sections.isEmpty()) throw HwpException("HWPX 본문을 찾지 못했습니다.")
        sections.joinToString("\n") { e ->
            val xml = zip.getInputStream(e).readBytes().toString(Charsets.UTF_8)
            xml.split("</hp:p>").joinToString("\n") { p ->
                Regex("""<hp:t(?:\s[^>]*)?>([^<]*)</hp:t>""").findAll(p).joinToString("") { unescape(it.groupValues[1]) }
            }
        }
    }

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    // ---------- HWP 5.0 ----------
    fun hwp5(bytes: ByteArray): String {
        val cfb = Cfb(bytes)
        val header = cfb.stream("FileHeader") ?: throw HwpException("HWP 머리 정보를 찾지 못했습니다.")
        val flags = if (header.size >= 40) ByteBuffer.wrap(header, 36, 4).order(ByteOrder.LITTLE_ENDIAN).int else 0
        if (flags and 0x2 != 0) throw HwpException("암호가 걸린 문서는 읽을 수 없습니다.")
        if (flags and 0x4 != 0) throw HwpException("배포용 문서는 읽을 수 없습니다. 한글에서 일반 문서로 저장해 주세요.")
        val compressed = flags and 0x1 != 0
        val sections = cfb.names().mapNotNull { n -> Regex("""BodyText/Section(\d+)""").matchEntire(n)?.let { it.groupValues[1].toInt() to n } }.sortedBy { it.first }
        if (sections.isEmpty()) throw HwpException("본문을 찾지 못했습니다.")
        val sb = StringBuilder()
        sections.forEach { (_, name) ->
            var data = cfb.stream(name) ?: return@forEach
            if (compressed) data = inflate(data)
            readRecords(data, sb)
        }
        return sb.toString()
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

    private fun readRecords(data: ByteArray, sb: StringBuilder) {
        val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var i = 0
        while (i + 4 <= data.size) {
            val h = bb.getInt(i); i += 4
            val tag = h and 0x3FF
            var size = (h ushr 20) and 0xFFF
            if (size == 0xFFF) { if (i + 4 > data.size) break; size = bb.getInt(i); i += 4 }
            if (size < 0 || i + size > data.size) break
            if (tag == 67) paraText(bb, i, size, sb)
            i += size
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
