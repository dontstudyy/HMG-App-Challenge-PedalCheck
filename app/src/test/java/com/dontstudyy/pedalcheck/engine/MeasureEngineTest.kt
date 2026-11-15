package com.dontstudyy.pedalcheck.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 정해 둔 값을 차례로 돌려주는 가짜 난수. 요청된 범위도 기록한다. */
class ScriptedRng(vararg values: Int) : Rng {
    private val queue = ArrayDeque(values.toList())
    val requests = mutableListOf<Pair<Int, Int>>()
    var fallbackWaitMs = 2000

    override fun nextInt(from: Int, until: Int): Int {
        requests += from to until
        return queue.removeFirstOrNull() ?: fallbackWaitMs
    }
}

private const val MS = 1_000_000L

/**
 * 가짜 시계로 엔진을 한 단계씩 굴리는 도우미.
 * 난수 호출 순서: 시작할 때 초록 위치 1번, 그 뒤 대기마다 1번(대기 ms).
 */
private class Driver(val engine: MeasureEngine, var now: Long = 10_000 * MS) {
    fun start(pedalDown: Boolean = false) = engine.start(now, pedalDown)

    /** Waiting 상태의 신호 시각까지 시간을 보내 신호를 켜고, 신호가 켜진 시각을 돌려준다. */
    fun showSignal(): Long {
        val w = engine.state as EngineState.Waiting
        now = w.dueAtNanos
        engine.tick(now)
        val s = engine.state as EngineState.Signal
        assertEquals(now, s.shownAtNanos)
        return s.shownAtNanos
    }

    fun press(atNanos: Long) { now = atNanos; engine.onPedal(PedalEvent(true, atNanos)) }
    fun release(atNanos: Long) { now = atNanos; engine.onPedal(PedalEvent(false, atNanos)) }
    fun tick(atNanos: Long) { now = atNanos; engine.tick(atNanos) }

    /** 피드백 화면이 끝나는 시각까지 보내고(손은 뗀 상태) 다음 대기로 넘어가게 한다. */
    fun finishFeedback() {
        val f = engine.state as EngineState.Feedback
        release(f.untilNanos - 1)
        tick(f.untilNanos)
    }

    /** 빨강 회차를 지정한 반응시간으로 정상 처리한다. */
    fun normalRed(rtMs: Int) {
        val shown = showSignal()
        press(shown + rtMs * MS)
        finishFeedback()
    }

    fun heldGreen() {
        val shown = showSignal()
        tick(shown + (PedalConfig.GREEN_HOLD_MS + PedalConfig.GREEN_GRACE_MS) * MS)
        finishFeedback()
    }
}

class MeasureEngineTest {

    // 첫 값은 초록 위치(슬롯 인덱스 1~4). 1이면 두 번째, 2이면 세 번째 회차가 초록
    private fun engineWith(vararg rng: Int) = MeasureEngine(ScriptedRng(*rng))

    // ---------- 평균 ----------

    @Test fun trimmedMean_목업값_620() {
        val r = TrimmedMean.of(listOf(590, 620, 610, 630, 680))
        assertEquals(620, r.meanMs)
        assertEquals(setOf(0, 4), r.excludedIndices)
    }

    @Test fun trimmedMean_동률은_앞_회차를_제외() {
        val r = TrimmedMean.of(listOf(600, 600, 610, 620, 620))
        assertEquals(setOf(0, 3), r.excludedIndices)
        assertEquals(610, r.meanMs) // (600 + 610 + 620) / 3
    }

    @Test fun trimmedMean_모두_같아도_서로_다른_두_개를_뺀다() {
        val r = TrimmedMean.of(listOf(600, 600, 600, 600, 600))
        assertEquals(2, r.excludedIndices.size)
        assertEquals(600, r.meanMs)
    }

    // ---------- 회차 구성 ----------

    @Test fun 회차는_빨강5_초록1_초록은_2에서_5번째() {
        for (g in 1..4) {
            val e = engineWith(g, 2000)
            Driver(e).start()
            assertEquals(6, e.plan.size)
            assertEquals(5, e.plan.count { it == SignalKind.RED })
            assertEquals(SignalKind.GREEN, e.plan[g])
        }
    }

    @Test fun 난수_범위_초록위치와_대기시간() {
        val rng = ScriptedRng(1, 1500)
        val e = MeasureEngine(rng)
        Driver(e).start()
        assertEquals(1 to 5, rng.requests[0])
        assertEquals(1500 to 4001, rng.requests[1])
    }

    // ---------- 한 세션 ----------

    @Test fun 정상_완주_목업값_620() {
        val e = engineWith(2) // 초록은 세 번째
        val d = Driver(e)
        d.start()
        d.normalRed(590)
        d.normalRed(620)
        d.heldGreen()
        d.normalRed(610)
        d.normalRed(630)
        d.normalRed(680)
        val done = e.state as EngineState.Finished
        assertEquals(620, done.result.meanMs)
        assertEquals(0, done.result.falseStarts)
        assertEquals(0, done.result.wrongPresses)
        assertEquals(6, done.result.trials.size)
        assertEquals(listOf(Verdict.NORMAL, Verdict.NORMAL, Verdict.HELD, Verdict.NORMAL, Verdict.NORMAL, Verdict.NORMAL),
            done.result.trials.map { it.verdict })
        // 590과 680이 평균에서 제외로 표시된다
        val excluded = done.result.trials.filter { it.excludedFromMean }.mapNotNull { it.reactionMs }
        assertEquals(setOf(590, 680), excluded.toSet())
    }

    // ---------- 판정 규칙 ----------

    @Test fun 부정출발_같은_회차를_처음부터_다시() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        val w = e.state as EngineState.Waiting
        d.press(w.dueAtNanos - 300 * MS)
        val f = e.state as EngineState.Feedback
        assertEquals(Verdict.FALSE_START, f.verdict)
        assertTrue(f.repeat)
        d.finishFeedback()
        val again = e.state as EngineState.Waiting
        assertEquals(w.slot, again.slot) // 같은 회차
        assertEquals(1, e.faults)
    }

    @Test fun 너무_빠름_100ms_미만은_부정출발과_같게() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        val shown = d.showSignal()
        d.press(shown + 99 * MS)
        val f = e.state as EngineState.Feedback
        assertEquals(Verdict.TOO_FAST, f.verdict)
        assertTrue(f.repeat)
        assertEquals(1, e.faults)
    }

    @Test fun 정확히_100ms는_정상() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        val shown = d.showSignal()
        d.press(shown + 100 * MS)
        val f = e.state as EngineState.Feedback
        assertEquals(Verdict.NORMAL, f.verdict)
        assertEquals(100, f.reactionMs)
        assertEquals(0, e.faults)
    }

    @Test fun 놓침_1500ms_지나면_같은_회차_다시() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        val shown = d.showSignal()
        d.tick(shown + 1499 * MS)
        assertTrue(e.state is EngineState.Signal) // 아직 기다림
        d.tick(shown + 1500 * MS)
        val f = e.state as EngineState.Feedback
        assertEquals(Verdict.MISSED, f.verdict)
        assertTrue(f.repeat)
        assertEquals(1, e.faults)
    }

    @Test fun 초록에서_밟으면_잘못_밟음_회차는_넘어간다() {
        val e = engineWith(1) // 초록은 두 번째
        val d = Driver(e)
        d.start()
        d.normalRed(600)
        val shown = d.showSignal()
        assertEquals(SignalKind.GREEN, (e.state as EngineState.Signal).kind)
        d.press(shown + 200 * MS)
        val f = e.state as EngineState.Feedback
        assertEquals(Verdict.WRONG_PRESS, f.verdict)
        assertFalse(f.repeat)
        assertEquals(0, e.faults) // 잘못 밟음은 중단 횟수에 넣지 않는다
    }

    @Test fun 초록을_끝까지_참으면_참기_성공() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        d.normalRed(600)
        val shown = d.showSignal()
        d.tick(shown + 1799 * MS)
        assertTrue(e.state is EngineState.Signal)
        d.tick(shown + 1800 * MS)
        assertEquals(Verdict.HELD, (e.state as EngineState.Feedback).verdict)
    }

    @Test fun 세_번까지는_다시_네_번째에서_중단() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        repeat(3) {
            val w = e.state as EngineState.Waiting
            d.press(w.dueAtNanos - 100 * MS)
            d.finishFeedback()
        }
        assertEquals(3, e.faults)
        assertTrue(e.state is EngineState.Waiting)
        val w = e.state as EngineState.Waiting
        d.press(w.dueAtNanos - 100 * MS)
        val a = e.state as EngineState.Aborted
        assertEquals(AbortReason.TOO_MANY_FAULTS, a.reason)
    }

    @Test fun 부정출발_너무빠름_놓침이_합쳐서_센다() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        val w = e.state as EngineState.Waiting
        d.press(w.dueAtNanos - 100 * MS); d.finishFeedback()   // 부정 출발
        val s1 = d.showSignal(); d.press(s1 + 50 * MS); d.finishFeedback() // 너무 빠름
        val s2 = d.showSignal(); d.tick(s2 + 1500 * MS); d.finishFeedback() // 놓침
        assertEquals(3, e.faults)
        val w2 = e.state as EngineState.Waiting
        d.press(w2.dueAtNanos - 1 * MS)
        assertTrue(e.state is EngineState.Aborted)
    }

    // ---------- 시작·손 떼기 ----------

    @Test fun 시작할_때_밟고_있으면_뗄_때까지_기다린다() {
        val e = engineWith(1, 2000)
        val d = Driver(e)
        d.start(pedalDown = true)
        assertTrue(e.state is EngineState.WaitRelease)
        d.tick(d.now + 5_000 * MS)
        assertTrue(e.state is EngineState.WaitRelease) // 시간이 가도 그대로
        val releaseAt = d.now + 1000 * MS
        d.release(releaseAt)
        val w = e.state as EngineState.Waiting
        assertEquals(releaseAt + 2000 * MS, w.dueAtNanos) // 뗀 시각부터 대기 시작
    }

    @Test fun 회차가_끝났는데_밟고_있으면_뗄_때까지_다음을_안_시작() {
        val e = engineWith(1, 2000)
        val d = Driver(e)
        d.start()
        val shown = d.showSignal()
        d.press(shown + 600 * MS)
        val f = e.state as EngineState.Feedback
        d.tick(f.untilNanos) // 아직 발을 안 뗌
        assertTrue(e.state is EngineState.WaitRelease)
        d.release(d.now + 10 * MS)
        assertTrue(e.state is EngineState.Waiting)
    }

    // ---------- 중단 ----------

    @Test fun 기어_이탈_등으로_중단하면_기록을_만들지_않는다() {
        for (reason in listOf(AbortReason.GEAR_CHANGED, AbortReason.PARKING_BRAKE_RELEASED, AbortReason.DRIVING_RESTRICTION)) {
            val e = engineWith(1)
            val d = Driver(e)
            d.start()
            d.normalRed(600)
            e.abort(reason)
            assertEquals(reason, (e.state as EngineState.Aborted).reason)
        }
    }

    @Test fun 신호_중에도_중단된다() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        d.showSignal()
        e.abort(AbortReason.GEAR_CHANGED)
        assertTrue(e.state is EngineState.Aborted)
        // 중단 뒤에 오는 밟음은 무시된다
        e.onPedal(PedalEvent(true, d.now + 100 * MS))
        assertTrue(e.state is EngineState.Aborted)
    }

    @Test fun 끝난_뒤_중단_요청은_무시() {
        val e = engineWith(2)
        val d = Driver(e)
        d.start()
        d.normalRed(590); d.normalRed(620); d.heldGreen(); d.normalRed(610); d.normalRed(630); d.normalRed(680)
        e.abort(AbortReason.GEAR_CHANGED)
        assertTrue(e.state is EngineState.Finished)
    }

    @Test fun 신호가_켜진_시각은_tick_시각() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        val w = e.state as EngineState.Waiting
        e.tick(w.dueAtNanos - 1)
        assertTrue(e.state is EngineState.Waiting) // 아직
        e.tick(w.dueAtNanos + 7 * MS) // 프레임이 늦게 와도
        assertEquals(w.dueAtNanos + 7 * MS, (e.state as EngineState.Signal).shownAtNanos)
    }

    @Test fun 완료_결과에서_반응시간_계산_ns_to_ms() {
        val e = engineWith(1)
        val d = Driver(e)
        d.start()
        val shown = d.showSignal()
        d.press(shown + 623_400_000L) // 623.4ms → 623
        assertEquals(623, (e.state as EngineState.Feedback).reactionMs)
    }
}
