package io.github.hdlee73.fsssupporter

import io.github.hdlee73.fsssupporter.opinion.CitationVerifier
import io.github.hdlee73.fsssupporter.opinion.OpinionModel
import io.github.hdlee73.fsssupporter.sanction.FineInput
import io.github.hdlee73.fsssupporter.sanction.FineProfile
import io.github.hdlee73.fsssupporter.sanction.Factor
import io.github.hdlee73.fsssupporter.sanction.Grade
import io.github.hdlee73.fsssupporter.sanction.SanctionCalc
import io.github.hdlee73.fsssupporter.sanction.SanctionStore
import io.github.hdlee73.fsssupporter.update.Updater
import com.hdlee73.everylaw.core.domain.HwpxParagraph
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreLogicTest {
    private val profile = FineProfile(
        id = "t", name = "테스트", statutoryMax = 100_000_000,
        grades = listOf(Grade("중대", 50.0)),
        aggravations = listOf(Factor("a1", "반복", 30.0), Factor("a2", "은폐", 30.0)),
        mitigations = listOf(Factor("m1", "자진", 20.0)),
        aggravationCap = 50.0, mitigationCap = 50.0, roundUnit = 10_000,
    )

    @Test fun fineAppliesAggravationThenMitigationWithCap() {
        val r = SanctionCalc.fine(profile, FineInput(gradeIndex = 0, aggravations = setOf("a1", "a2"), mitigations = setOf("m1")))
        assertEquals(50_000_000, r.base)
        assertEquals(50.0, r.aggravationPercent)
        assertTrue(r.aggravationCapped)
        assertEquals(75_000_000, r.afterAggravation)
        assertEquals(60_000_000, r.final)
    }

    @Test fun fineUsesViolationAmountAndStatutoryLimit() {
        val r = SanctionCalc.fine(profile, FineInput(violationAmount = 300_000_000, gradeIndex = 0, aggravations = setOf("a1")))
        assertEquals(150_000_000, r.base)
        assertTrue(r.limited)
        assertEquals(100_000_000, r.final)
    }

    @Test fun fineRoundsDown() {
        val r = SanctionCalc.fine(profile, FineInput(directBase = 12_345_678))
        assertEquals(12_340_000, r.final)
    }

    @Test fun levelMovesWithinRange() {
        val p = SanctionStore.BUILT_IN_LEVELS.first()
        val r = SanctionCalc.level(p, 2, setOf("agg-repeat"), setOf("mit-self", "mit-coop", "mit-repair"))
        // 가중 1단계, 감경 3단계 → 상한 2단계 → 순 1단계 가볍게
        assertEquals(p.levels[3], r.final)
        val top = SanctionCalc.level(p, 0, setOf("agg-repeat"), emptySet())
        assertTrue(top.clipped)
        assertEquals(p.levels[0], top.final)
    }

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

    @Test fun splitLevelAndParagraphs() {
        assertEquals("자본시장법" to "시행령", CitationVerifier.splitLevel("자본시장법 시행령"))
        assertEquals("은행법" to null, CitationVerifier.splitLevel("은행법"))
        val ps = OpinionModel.toParagraphs("검 사 서\n\nⅠ. 개요\n  1. 목적\n※ 참고")
        assertEquals(HwpxParagraph.Style.TITLE, ps[0].style)
        assertEquals(HwpxParagraph.Style.SECTION, ps[2].style)
        assertEquals(1, ps[3].depth)
        assertEquals(HwpxParagraph.Style.GRAY, ps[4].style)
    }
}
