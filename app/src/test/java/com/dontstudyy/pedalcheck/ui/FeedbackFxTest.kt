package com.dontstudyy.pedalcheck.ui

import com.dontstudyy.pedalcheck.engine.PedalConfig
import com.dontstudyy.pedalcheck.engine.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackFxTest {

    @Test fun 제대로_밟음과_참기_성공은_초록_틀린_입력은_빨강_놓침은_없음() {
        assertEquals(FxKind.GOOD, FeedbackFx.kindOf(Verdict.NORMAL))
        assertEquals(FxKind.GOOD, FeedbackFx.kindOf(Verdict.HELD))
        assertEquals(FxKind.BAD, FeedbackFx.kindOf(Verdict.FALSE_START))
        assertEquals(FxKind.BAD, FeedbackFx.kindOf(Verdict.TOO_FAST))
        assertEquals(FxKind.BAD, FeedbackFx.kindOf(Verdict.WRONG_PRESS))
        assertEquals(FxKind.NONE, FeedbackFx.kindOf(Verdict.MISSED))
        assertEquals(FxKind.NONE, FeedbackFx.kindOf(null))
    }

    @Test fun 테두리는_한_번_켜져서_유지되다_꺼진다_깜빡임_없음() {
        for (kind in listOf(FxKind.GOOD, FxKind.BAD)) {
            val samples = (0..100).map { FeedbackFx.glow(kind, it * 0.01f) }
            assertEquals(0f, samples.first(), 1e-6f)
            assertEquals(0f, samples.last(), 1e-6f)
            assertTrue(samples.all { it in 0f..1f })
            // 한 번 올라가고 한 번 내려간다: 최대값 이전엔 줄지 않고, 이후엔 늘지 않는다
            val peak = samples.indexOf(samples.max())
            for (i in 1..peak) assertTrue(samples[i] >= samples[i - 1] - 1e-6f)
            for (i in peak + 1 until samples.size) assertTrue(samples[i] <= samples[i - 1] + 1e-6f)
        }
    }

    @Test fun 제대로_밟음은_0_5초까지_틀린_입력은_0_6초까지_유지된다() {
        assertEquals(1f, FeedbackFx.glow(FxKind.GOOD, 0.4f), 1e-6f)
        assertEquals(1f, FeedbackFx.glow(FxKind.BAD, 0.5f), 1e-6f)
        assertTrue(FeedbackFx.glow(FxKind.GOOD, 0.7f) < 1f)
        assertTrue(FeedbackFx.glow(FxKind.BAD, 0.8f) < 1f)
    }

    /** 다음 스텝이 시작될 때 중간 상태로 남지 않도록, 가장 짧은 판정 화면 안에 모든 연출이 끝나야 한다. */
    @Test fun 가장_짧은_판정_화면_안에_모든_연출이_끝난다() {
        val shortest = PedalConfig.REST_MS / 1000f
        assertTrue(FeedbackFx.DURATION_SEC <= shortest)
        for (kind in listOf(FxKind.GOOD, FxKind.BAD)) {
            assertEquals(0f, FeedbackFx.glow(kind, shortest), 1e-6f)
            assertEquals(0f, FeedbackFx.vibration(kind, shortest), 1e-6f)
            assertEquals(0f, FeedbackFx.shake(kind, shortest, 12f), 1e-6f)
        }
    }

    @Test fun 놓침은_연출이_없다() {
        assertEquals(0f, FeedbackFx.glow(FxKind.NONE, 0.3f), 0f)
        assertEquals(0f, FeedbackFx.shake(FxKind.NONE, 0.1f, 12f), 0f)
    }

    @Test fun 흔들림은_틀린_입력에만_있고_0_6초_뒤_멈춘다() {
        assertEquals(0f, FeedbackFx.shake(FxKind.GOOD, 0.05f, 12f), 0f)
        assertTrue(FeedbackFx.shake(FxKind.BAD, 0.02f, 12f) != 0f)
        assertEquals(0f, FeedbackFx.shake(FxKind.BAD, 0.61f, 12f), 0f)
        assertEquals(0f, FeedbackFx.shake(FxKind.BAD, 1.5f, 12f), 0f)
    }

    @Test fun 흔들림은_진폭을_넘지_않고_점점_줄어든다() {
        val amp = 12f
        val xs = (1..60).map { FeedbackFx.shake(FxKind.BAD, it * 0.01f, amp) }
        assertTrue(xs.all { kotlin.math.abs(it) <= amp })
        val early = xs.take(20).maxOf { kotlin.math.abs(it) }
        val late = xs.takeLast(20).maxOf { kotlin.math.abs(it) }
        assertTrue(late < early)
    }

    @Test fun 진동선은_틀린_입력에만_1초에_걸쳐_사라진다() {
        assertEquals(1f, FeedbackFx.vibration(FxKind.BAD, 0f), 1e-6f)
        assertEquals(0.5f, FeedbackFx.vibration(FxKind.BAD, 0.5f), 1e-6f)
        assertEquals(0f, FeedbackFx.vibration(FxKind.BAD, 1.2f), 0f)
        assertEquals(0f, FeedbackFx.vibration(FxKind.GOOD, 0.2f), 0f)
    }
}
