package io.github.hdlee73.fsssupporter

import io.github.hdlee73.fsssupporter.criteria.Calc
import io.github.hdlee73.fsssupporter.criteria.FineCriteria
import io.github.hdlee73.fsssupporter.criteria.FineInput
import io.github.hdlee73.fsssupporter.criteria.LevelCriteria
import io.github.hdlee73.fsssupporter.criteria.PenaltyCriteria
import io.github.hdlee73.fsssupporter.criteria.PenaltyInput
import io.github.hdlee73.fsssupporter.criteria.koreanWon
import io.github.hdlee73.fsssupporter.data.HCell
import io.github.hdlee73.fsssupporter.data.HPara
import io.github.hdlee73.fsssupporter.data.HTable
import io.github.hdlee73.fsssupporter.data.Hancom
import io.github.hdlee73.fsssupporter.data.HwpText
import io.github.hdlee73.fsssupporter.data.KeyFile
import io.github.hdlee73.fsssupporter.library.FormCatalog
import io.github.hdlee73.fsssupporter.library.FormMark
import io.github.hdlee73.fsssupporter.library.LibraryStore
import io.github.hdlee73.fsssupporter.library.PageSpan
import io.github.hdlee73.fsssupporter.library.TocEntry
import io.github.hdlee73.fsssupporter.todo.parseDue
import io.github.hdlee73.fsssupporter.update.Updater
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.LocalDate
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoreLogicTest {
    @Test fun versionCompare() {
        assertTrue(Updater.compareVersions("0.1.10", "0.1.9") > 0)
        assertEquals(0, Updater.compareVersions("v1.0", "1.0.0"))
        assertTrue(Updater.compareVersions("1.0.0", "1.0.1-beta") < 0)
    }


    @Test fun signatureVerification() {
        val kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val pub = Base64.getEncoder().encodeToString(kp.public.encoded)
        val data = """{"version":"9.9.9"}""".toByteArray()
        val sig = Signature.getInstance("Ed25519").run { initSign(kp.private); update(data); sign() }
        assertTrue(Updater.verify(data, sig, pub))
        assertFalse(Updater.verify("""{"version":"9.9.8"}""".toByteArray(), sig, pub))
        // 내장 공개키로는 남이 만든 서명이 통과하지 않는다.
        assertFalse(Updater.verify(data, sig))
        assertEquals(64, Updater.decodeSignature(Base64.getEncoder().encode(sig)).size)
    }


    @Test fun penaltyFollowsBaseRateAndNetAdjustment() {
        val c = PenaltyCriteria()
        // 모두 '상'이면 3.0점 → 매우 중대(100%)
        val high = Calc.penalty(c, PenaltyInput(100_000_000, c.items.associate { it.id to 3 }))
        assertEquals("매우 중대한 위반", high.band.label)
        assertEquals(100_000_000, high.base)
        // 모두 '중'이면 2.0점 → 중대(75%), 자진신고 30% 감경 → 7,500만 − 2,250만 = 5,250만
        val mid = c.mitigations.first { it.label == "자진신고" }
        val r = Calc.penalty(c, PenaltyInput(100_000_000, c.items.associate { it.id to 2 }, mitigations = setOf(mid.id)))
        assertEquals(75_000_000, r.base)
        assertEquals(52_500_000, r.final)
        // 모두 '하'(1.0점) → 50%, 기준율 -10%p → 40%
        val low = Calc.penalty(c, PenaltyInput(100_000_000, c.items.associate { it.id to 1 }, rateAdjust = -10.0))
        assertEquals(40_000_000, low.base)
    }

    @Test fun penaltyCapsByCapitalAndGain() {
        val c = PenaltyCriteria()
        val r = Calc.penalty(c, PenaltyInput(1_000_000_000, c.items.associate { it.id to 3 }, capital = 2_000_000_000))
        assertEquals(200_000_000, r.final) // 자본금 10%
        val g = Calc.penalty(c, PenaltyInput(1_000_000_000, c.items.associate { it.id to 1 }, unjustGain = 30_000_000))
        assertEquals(300_000_000, g.final) // 부당이득 10배
    }

    @Test fun fineUsesMatrixAndExemption() {
        val c = FineCriteria()
        val r = Calc.fine(c, FineInput(50_000_000, result = 0, motive = 0))
        assertEquals(50_000_000, r.final)
        val low = Calc.fine(c, FineInput(300_000, result = 2, motive = 2))
        assertEquals(60_000, low.base)
        assertTrue(low.exemptable)
        val m = c.mitigations.first { it.label == "자진신고" }
        val a = c.aggravations.first()
        val mixed = Calc.fine(c, FineInput(10_000_000, result = 1, motive = 1, aggravations = setOf(a.id), mitigations = setOf(m.id)))
        // 600만 × 1.2 = 720만 × 0.7 = 504만
        assertEquals(5_040_000, mixed.final)
    }

    @Test fun levelStepsWithinCaps() {
        val c = LevelCriteria()
        val staff = c.targets.first { it.id == "staff" }
        val base = Calc.level(staff, c, 1, 1, null, emptySet(), emptySet())
        assertEquals("감봉", base.final)
        val all = Calc.level(staff, c, 1, 1, null, emptySet(), staff.mitigations.map { it.id }.toSet())
        assertEquals("주의", all.final) // 감경은 최대 2단계
        val worse = Calc.level(staff, c, 0, 0, null, staff.aggravations.map { it.id }.toSet(), emptySet())
        assertEquals("면직", worse.final)
        assertEquals("감봉(견책 ~ 정직)", base.range)
    }

    @Test fun koreanWonReadsLargeAmounts() {
        assertEquals("1억 2,345만 원", koreanWon(123_450_000))
        assertEquals("5,000 원", koreanWon(5_000))
    }

    @Test fun dueDatesParse() {
        val today = LocalDate.of(2026, 11, 3)
        assertEquals("2026-11-04", parseDue("내일", today))
        assertEquals("2026-11-20", parseDue("11/20", today))
        assertEquals("2026-12-01", parseDue("20261201", today))
        assertEquals(null, parseDue("abc", today))
    }
}

class LibraryTest {
    private fun hwpx(xml: String): File {
        val f = File.createTempFile("fss-test", ".hwpx")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("mimetype")); z.write("application/hwp+zip".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("Contents/section0.xml")); z.write(xml.toByteArray()); z.closeEntry()
        }
        return f
    }

    @Test fun readsHwpxText() {
        val f = File.createTempFile("fss-test", ".hwpx")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("mimetype")); z.write("application/hwp+zip".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("Contents/section0.xml"))
            z.write("""<hs:sec><hp:p><hp:run><hp:t>검사 &amp; 제재</hp:t></hp:run></hp:p><hp:p><hp:run><hp:t>둘째 줄</hp:t></hp:run></hp:p></hs:sec>""".toByteArray())
            z.closeEntry()
        }
        val text = HwpText.extract(f)
        assertTrue(text.contains("검사 & 제재") && text.contains("둘째 줄"), text)
        f.delete()
    }


    @Test fun hwpxCutKeepsOnlyOneFormAndSectionSetup() {
        val ns = """xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section" xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph""""
        fun p(t: String) = """<hp:p><hp:run><hp:t>${t.replace("<", "&lt;").replace(">", "&gt;")}</hp:t></hp:run></hp:p>"""
        val sec0 = """<hs:sec $ns><hp:p><hp:run><hp:secPr id="s"/><hp:t>표지</hp:t></hp:run></hp:p>${p("< 제1-1호 서식 >")}${p("첫 서식 내용")}${p("< 제1-2 호 서식 >")}${p("둘째 서식 내용")}</hs:sec>"""
        val sec1 = """<hs:sec $ns><hp:p><hp:run><hp:secPr id="s2"/><hp:t>&lt; 제2-1호 서식 &gt;</hp:t></hp:run></hp:p>${p("셋째")}</hs:sec>"""
        val hpf = """<opf:package xmlns:opf="http://www.idpf.org/2007/opf/"><opf:manifest><opf:item id="section0" href="Contents/section0.xml"/><opf:item id="section1" href="Contents/section1.xml"/></opf:manifest><opf:spine><opf:itemref idref="section0"/><opf:itemref idref="section1"/></opf:spine></opf:package>"""
        val src = File.createTempFile("fss-cut", ".hwpx")
        ZipOutputStream(src.outputStream()).use { z ->
            listOf("mimetype" to "application/hwp+zip", "Contents/content.hpf" to hpf, "Contents/section0.xml" to sec0, "Contents/section1.xml" to sec1).forEach { (n, t) ->
                z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry()
            }
        }
        val out = File(src.parentFile, "fss-cut-out-${System.nanoTime()}.hwpx")
        val r = io.github.hdlee73.fsssupporter.data.HwpxCut.cut(src, out, Hancom.Mark("< 제1-2호 서식 >"), Hancom.Mark("< 제2-1호 서식 >"))
        assertTrue(r is Hancom.Result.Ok, r.toString())
        val text = HwpText.extract(out)
        assertTrue("둘째 서식 내용" in text && "첫 서식" !in text && "셋째" !in text && "표지" !in text, text)
        java.util.zip.ZipFile(out).use { z ->
            assertEquals("mimetype", z.entries().nextElement().name)
            assertTrue(z.getEntry("Contents/section1.xml") == null)
            val sec = z.getInputStream(z.getEntry("Contents/section0.xml")).readBytes().decodeToString()
            assertTrue("secPr" in sec, sec)
            val man = z.getInputStream(z.getEntry("Contents/content.hpf")).readBytes().decodeToString()
            assertTrue("section1" !in man, man)
        }
        src.delete(); out.delete()
    }

    @Test fun hwp5RejectsNonHwp() {
        val f = File.createTempFile("fss-test", ".hwp").apply { writeText("not a hwp") }
        val e = runCatching { HwpText.extract(f) }.exceptionOrNull()
        assertNotNull(e)
        f.delete()
    }

    @Test fun readsHwpxTablesAsCells() {
        val ns = """xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph" xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section""""
        val f = hwpx("""<hs:sec $ns><hp:p><hp:run><hp:t>&lt; 제2-4호 서식 &gt;</hp:t></hp:run></hp:p>
            <hp:p><hp:run><hp:tbl rowCnt="2" colCnt="2">
              <hp:tr><hp:tc><hp:subList><hp:p><hp:run><hp:t>수신</hp:t></hp:run></hp:p></hp:subList><hp:cellAddr colAddr="0" rowAddr="0"/><hp:cellSpan colSpan="1" rowSpan="1"/><hp:cellSz width="1000" height="100"/></hp:tc>
                     <hp:tc><hp:subList><hp:p><hp:run><hp:t>기관장</hp:t></hp:run></hp:p></hp:subList><hp:cellAddr colAddr="1" rowAddr="0"/><hp:cellSpan colSpan="1" rowSpan="1"/><hp:cellSz width="3000" height="100"/></hp:tc></hp:tr>
              <hp:tr><hp:tc><hp:subList><hp:p><hp:run><hp:t>본문</hp:t></hp:run></hp:p></hp:subList><hp:cellAddr colAddr="0" rowAddr="1"/><hp:cellSpan colSpan="2" rowSpan="1"/></hp:tc></hp:tr>
            </hp:tbl></hp:run></hp:p>
            <hp:p><hp:run><hp:t>&lt; 제3-5호 서식 &gt;</hp:t></hp:run></hp:p><hp:p><hp:run><hp:t>확인서</hp:t></hp:run></hp:p>
            <hp:p><hp:run><hp:t>&lt; 제3-5호 서식 &gt;</hp:t></hp:run></hp:p><hp:p><hp:run><hp:t>접수대장</hp:t></hp:run></hp:p></hs:sec>""")
        val blocks = HwpText.read(f)
        val table = blocks.filterIsInstance<HTable>().single()
        assertEquals(3, table.cells.size)
        assertEquals(2, table.cells.first { it.row == 1 }.colSpan)
        assertEquals(3000, table.cells.first { it.col == 1 }.width)
        val (forms, marks) = LibraryStore.splitFormBlocks(blocks)
        assertEquals(setOf("2-4", "3-5"), forms.keys)
        assertTrue(HwpText.flatten(forms.getValue("2-4")).contains("기관장"))
        assertTrue(HwpText.flatten(forms.getValue("3-5")).let { it.contains("확인서") && it.contains("접수대장") })
        // 2-4는 다음 번호(3-5)의 첫 표시 앞까지, 3-5는 같은 번호가 두 번 나와도 문서 끝까지
        val r24 = LibraryStore.formRange(marks, "2-4")!!
        assertEquals("< 제2-4호 서식 >", r24.first.text)
        assertEquals(Hancom.Mark("< 제3-5호 서식 >", 1), r24.second)
        assertEquals(null, LibraryStore.formRange(marks, "3-5")!!.second)
        f.delete()
    }


    @Test fun nthCountsRepeatedMarks() {
        val marks = listOf(FormMark("3-5", "< 제3-5호 서식 >"), FormMark("3-5", "< 제3-5호 서식 >"), FormMark("3-6", "< 제3-6호 서식 >"), FormMark("3-6", "< 제3-6호 서식 >"), FormMark("3-7", "< 제3-7호 서식 >"))
        val r = LibraryStore.formRange(marks, "3-6")!!
        assertEquals(Hancom.Mark("< 제3-6호 서식 >", 1), r.first)
        assertEquals(Hancom.Mark("< 제3-7호 서식 >", 1), r.second)
    }


    @Test fun manualBlocksSplitLikeText() {
        val blocks = listOf(HPara("제1장 검사준비"), HPara("01"), HPara("검사실시 결정"), HPara("□ 검사실시부서는 연간 계획에 따라 검사 실시를 결정한다."),
            HTable(1, 1, listOf(HCell(0, 0, blocks = listOf(HPara("표 안의 설명입니다. 충분히 긴 글입니."))))), HPara("02"), HPara(""), HPara("현물검사(선택)"), HPara("□ 현물검사는 우선 시재의 일치 여부를 확인한다."))
        val m = LibraryStore.splitManualBlocks(blocks)
        assertEquals(listOf("검사실시 결정", "현물검사(선택)"), m.keys.toList())
        assertTrue(m.getValue("검사실시 결정").any { it is HTable })
    }


    @Test fun keyFileRoundTrip() {
        val text = KeyFile.seal(KeyFile.Keys("hong", "a".repeat(40)), "secret12")
        assertTrue("hong" !in text)
        assertEquals("hong", KeyFile.open(text, "secret12").lawOc)
        assertTrue(runCatching { KeyFile.open(text, "wrong123") }.isFailure)
    }


    @Test fun formPagesFollowMarks() {
        val pages = listOf(
            "목차",
            "< 제1-1호 서식 >\n면담 사전통지서",
            "이어지는 둘째 쪽",
            "< 제1-2호 서식 >\n조사출장 통지서\n" + "가".repeat(60) + "\n< 제1-3호 서식 >\n품의서",
            "< 제1-1호 서식 >\n붙임",
        )
        val spans = LibraryStore.formSpans(pages)
        assertEquals(PageSpan(1, 4), spans["1-1"]) // 같은 번호가 뒤에 또 나오면 넓힌다
        assertEquals(PageSpan(3, 3), spans["1-2"]) // 다음 서식이 같은 쪽 중간에서 시작
        assertEquals(PageSpan(3, 3), spans["1-3"]) // 다음 서식이 쪽 맨 위면 그 앞쪽까지
        assertEquals(listOf("1-1", "1-2", "1-3", "1-1"), LibraryStore.marksFromPages(pages).map { it.no })
    }

    @Test fun tocSkipsContentsPage() {
        val titles = listOf("검사실시 결정", "사전예고", "자료요구", "현장검사", "검사결과 보고")
        val pages = listOf(titles.joinToString("\n"), "01 검사실시 결정 본문", "본문 계속", "02 사 전 예 고", "03 자료요구 04 현장검사", "05 검사결과 보고")
        assertEquals(listOf(TocEntry("검사실시 결정", 1), TocEntry("사전예고", 3), TocEntry("자료요구", 4), TocEntry("현장검사", 4), TocEntry("검사결과 보고", 5)), LibraryStore.mapToc(titles, pages))
    }

    @Test fun catalogOrdersNumbers() {
        assertEquals(listOf("1-2", "1-9", "1-10", "2-1"), listOf("1-10", "2-1", "1-9", "1-2").sortedWith(FormCatalog.noOrder))
        assertTrue(FormCatalog.form("1-1").name.isNotBlank())
    }

    @Test fun searchIgnoresSpaces() {
        assertEquals(3, LibraryStore.indexIgnoringSpace("가나 다 라 마", "다라"))
    }
}
