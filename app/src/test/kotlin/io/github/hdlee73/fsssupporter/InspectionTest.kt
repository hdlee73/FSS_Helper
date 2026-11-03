package io.github.hdlee73.fsssupporter

import io.github.hdlee73.fsssupporter.cases.Actor
import io.github.hdlee73.fsssupporter.cases.ActorKind
import io.github.hdlee73.fsssupporter.cases.ActorRole
import io.github.hdlee73.fsssupporter.cases.FinePick
import io.github.hdlee73.fsssupporter.cases.Finding
import io.github.hdlee73.fsssupporter.cases.FindingStore
import io.github.hdlee73.fsssupporter.cases.LevelPick
import io.github.hdlee73.fsssupporter.data.HCell
import io.github.hdlee73.fsssupporter.data.HPara
import io.github.hdlee73.fsssupporter.data.HTable
import io.github.hdlee73.fsssupporter.data.Hancom
import io.github.hdlee73.fsssupporter.data.HwpText
import io.github.hdlee73.fsssupporter.data.KeyFile
import io.github.hdlee73.fsssupporter.inspection.FormMark
import io.github.hdlee73.fsssupporter.inspection.StepState
import io.github.hdlee73.fsssupporter.sanction.SanctionStore
import io.github.hdlee73.fsssupporter.inspection.Anchor
import io.github.hdlee73.fsssupporter.inspection.DueRule
import io.github.hdlee73.fsssupporter.inspection.InspectionGuide
import io.github.hdlee73.fsssupporter.inspection.InspectionProgress
import io.github.hdlee73.fsssupporter.inspection.InspectionStore
import io.github.hdlee73.fsssupporter.inspection.addBusinessDays
import io.github.hdlee73.fsssupporter.opinion.OpinionModel
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InspectionTest {
    @Test fun businessDaysSkipWeekends() {
        val mon = LocalDate.of(2026, 11, 2)
        assertEquals(LocalDate.of(2026, 10, 19), addBusinessDays(mon, -10))
        assertEquals(LocalDate.of(2026, 10, 28), addBusinessDays(mon, -3))
        assertEquals(LocalDate.of(2026, 11, 3), addBusinessDays(mon, 1))
    }

    @Test fun dueRuleUsesRegularVariant() {
        val r = DueRule(Anchor.START, -7, regularDays = -30)
        val start = LocalDate.of(2026, 12, 1)
        assertEquals(LocalDate.of(2026, 11, 24), r.due(false, start, null))
        assertEquals(LocalDate.of(2026, 11, 1), r.due(true, start, null))
        assertEquals("착수 30일 전", r.label(true))
        assertEquals(null, DueRule(Anchor.END, 0).due(false, start, null))
    }

    @Test fun guideIsConsistent() {
        val forms = InspectionGuide.FORMS.map { it.no }.toSet()
        assertEquals(InspectionGuide.FORMS.size, forms.size, "서식 번호 중복")
        InspectionGuide.ALL_STEPS.forEach { s -> s.forms.forEach { assertTrue(it in forms, "${s.id} → $it") } }
        assertEquals(InspectionGuide.ALL_STEPS.size, InspectionGuide.ALL_STEPS.map { it.id }.toSet().size)
        val templates = OpinionModel.BUILT_IN.map { it.id }.toSet()
        InspectionGuide.TEMPLATE_FOR_FORM.values.forEach { assertTrue(it in templates, it) }
        assertTrue(InspectionGuide.steps(false).none { it.regularOnly })
    }

    @Test fun progressTracksCurrentStep() {
        val first = InspectionGuide.steps(false).first()
        val p = InspectionProgress("t", done = setOf(first.id))
        assertEquals(InspectionGuide.steps(false)[1].id, p.current()?.id)
        assertTrue(p.ratio() > 0f)
    }

    @Test fun splitsManualAndForms() {
        val manual = "제1장 검사준비\n01\n검사실시 결정\n□ 검사실시부서는 연간 계획에 따라 검사 실시를 결정한다.\n02\n검사의 사전예고 및 검사사전자료 요구\n□ 사전예고 통지서를 착수 전에 통지한다. 자료는 시스템으로 요구한다.\n"
        val m = InspectionStore.splitManual(manual)
        assertEquals(listOf("검사실시 결정", "검사의 사전예고 및 검사사전자료 요구"), m.keys.toList())
        assertTrue(m.getValue("검사실시 결정").startsWith("□ 검사실시부서"))
        val forms = InspectionStore.splitForms("목차\n< 제2-4호 서식 >\n검사사전예고 통지서 본문\n<제3-5호 서식>\n확인서\n< 제3-5호 서식 >\n접수대장")
        assertEquals(setOf("2-4", "3-5"), forms.keys)
        assertTrue(forms.getValue("3-5").contains("확인서") && forms.getValue("3-5").contains("접수대장"))
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

    @Test fun hwp5RejectsNonHwp() {
        val f = File.createTempFile("fss-test", ".hwp").apply { writeText("not a hwp") }
        val e = runCatching { HwpText.extract(f) }.exceptionOrNull()
        assertNotNull(e)
        f.delete()
    }
}

class CaseFeatureTest {
    private fun hwpx(xml: String): File {
        val f = File.createTempFile("fss-test", ".hwpx")
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("mimetype")); z.write("application/hwp+zip".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("Contents/section0.xml")); z.write(xml.toByteArray()); z.closeEntry()
        }
        return f
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
        val (forms, marks) = InspectionStore.splitFormBlocks(blocks)
        assertEquals(setOf("2-4", "3-5"), forms.keys)
        assertTrue(HwpText.flatten(forms.getValue("2-4")).contains("기관장"))
        assertTrue(HwpText.flatten(forms.getValue("3-5")).let { it.contains("확인서") && it.contains("접수대장") })
        // 2-4는 다음 번호(3-5)의 첫 표시 앞까지, 3-5는 같은 번호가 두 번 나와도 문서 끝까지
        val r24 = InspectionStore.formRange(marks, "2-4")!!
        assertEquals("< 제2-4호 서식 >", r24.first.text)
        assertEquals(Hancom.Mark("< 제3-5호 서식 >", 1), r24.second)
        assertEquals(null, InspectionStore.formRange(marks, "3-5")!!.second)
        f.delete()
    }

    @Test fun nthCountsRepeatedMarks() {
        val marks = listOf(FormMark("3-5", "< 제3-5호 서식 >"), FormMark("3-5", "< 제3-5호 서식 >"), FormMark("3-6", "< 제3-6호 서식 >"), FormMark("3-6", "< 제3-6호 서식 >"), FormMark("3-7", "< 제3-7호 서식 >"))
        val r = InspectionStore.formRange(marks, "3-6")!!
        assertEquals(Hancom.Mark("< 제3-6호 서식 >", 1), r.first)
        assertEquals(Hancom.Mark("< 제3-7호 서식 >", 1), r.second)
    }

    @Test fun manualBlocksSplitLikeText() {
        val blocks = listOf(HPara("제1장 검사준비"), HPara("01"), HPara("검사실시 결정"), HPara("□ 검사실시부서는 연간 계획에 따라 검사 실시를 결정한다."),
            HTable(1, 1, listOf(HCell(0, 0, blocks = listOf(HPara("표 안의 설명입니다. 충분히 긴 글입니."))))), HPara("02"), HPara(""), HPara("현물검사(선택)"), HPara("□ 현물검사는 우선 시재의 일치 여부를 확인한다."))
        val m = InspectionStore.splitManualBlocks(blocks)
        assertEquals(listOf("검사실시 결정", "현물검사(선택)"), m.keys.toList())
        assertTrue(m.getValue("검사실시 결정").any { it is HTable })
    }

    @Test fun stepStatesLeaveProgress() {
        val steps = InspectionGuide.steps(false)
        val p = InspectionProgress("t", skipped = setOf(steps[0].id), others = mapOf(steps[1].id to "총괄팀"))
        assertEquals(StepState.SKIPPED, p.stateOf(steps[0].id))
        assertEquals(StepState.OTHER, p.stateOf(steps[1].id))
        assertEquals(steps.size - 2, p.steps().size)
        assertEquals(steps[2].id, p.current()?.id)
    }

    @Test fun keyFileRoundTrip() {
        val text = KeyFile.seal(KeyFile.Keys("hong", "a".repeat(40)), "secret12")
        assertTrue("hong" !in text)
        assertEquals("hong", KeyFile.open(text, "secret12").lawOc)
        assertTrue(runCatching { KeyFile.open(text, "wrong123") }.isFailure)
    }

    @Test fun findingsBecomeActionRows() {
        val store = SanctionStore(File.createTempFile("fss-test", ".json").apply { delete() })
        val f = Finding(
            title = "설명의무 위반",
            institution = LevelPick("lv-inst", baseIndex = 3),
            penalty = FinePick(enabled = true, profileId = "fine-penalty", fixed = 12_000_000),
            actors = listOf(
                Actor(kind = ActorKind.STAFF, name = "홍길동", position = "팀장", role = ActorRole.ACTOR, level = LevelPick("lv-staff", baseIndex = 2), fine = FinePick(enabled = true, profileId = "fine-fine", fixed = 1_000_000)),
                Actor(kind = ActorKind.EXEC, name = "김감독", position = "상무", role = ActorRole.SUPERVISOR, retired = true, level = LevelPick("lv-exec", baseIndex = 3)),
            ),
        )
        val rows = FindingStore.rows(listOf(f), store)
        assertEquals(3, rows.size)
        assertEquals("기관주의", rows[0].sanction)
        assertEquals("12,000,000원", rows[0].penalty)
        assertEquals("감봉", rows[1].sanction)
        assertEquals("1,000,000원", rows[1].fine)
        assertEquals("주의적 경고 상당", rows[2].sanction)
        val out = java.io.ByteArrayOutputStream()
        FindingStore.writeHwpx(io.github.hdlee73.fsssupporter.tasks.WorkTask(title = "A사 검사"), listOf(f), store, out)
        assertTrue(out.size() > 1000)
    }
}
