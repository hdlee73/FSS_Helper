package io.github.hdlee73.fsssupporter

import io.github.hdlee73.fsssupporter.data.HwpText
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
        val f = File.createTempFile("t", ".hwpx")
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
        val f = File.createTempFile("t", ".hwp").apply { writeText("not a hwp") }
        val e = runCatching { HwpText.extract(f) }.exceptionOrNull()
        assertNotNull(e)
        f.delete()
    }
}
