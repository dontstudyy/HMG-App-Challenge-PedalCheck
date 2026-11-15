package com.dontstudyy.pedalcheck.engine

private const val NS_PER_MS = 1_000_000L

/**
 * 30초 측정 엔진. Android에 의존하지 않는 순수 Kotlin이다.
 *
 * 시계는 밖에서 넣는다. 모든 시각은 같은 시계(`System.nanoTime()`)의 나노초 값이어야 한다.
 *  - [tick]에 넣는 시각: 화면이 그려지는 **프레임 시각**(`withFrameNanos`). 신호 시각(shownAt)도 이 값이다.
 *    같은 프레임 안에서 상태가 바뀌어 그려지므로 "상태를 바꾼 코드 시각"의 최대 16ms 흔들림이 생기지 않는다.
 *  - [onPedal]에 넣는 시각: Brake 리스너 첫 줄에서 찍은 시각.
 *
 * 호출 순서 규칙: 화면은 프레임마다 `시각 < 프레임 시각`인 밟음 이벤트를 먼저 [onPedal]로 넣고 그다음 [tick]을 부른다.
 */
class MeasureEngine(private val rng: Rng) {
    var state: EngineState = EngineState.Idle
        private set

    /** 6회차 구성(빨강 5 + 초록 1). [start] 뒤에 채워진다. */
    var plan: List<SignalKind> = emptyList()
        private set

    /** 부정 출발·너무 빠름·놓침을 합친 횟수 */
    var faults: Int = 0
        private set

    private var pedalDown = false
    private var slot = 0
    private val attempts = mutableListOf<TrialRecord>()

    /** 지금까지의 시도(다시 잰 것 포함). 화면의 회차별 막대용. */
    val trials: List<TrialRecord> get() = attempts.toList()

    /** 디버그 로그용. 태그 `PEDAL`로 찍는다: SIGNAL_ON / PEDAL / RESULT */
    var logger: ((String) -> Unit)? = null

    val isRunning: Boolean
        get() = state is EngineState.WaitRelease || state is EngineState.Waiting ||
            state is EngineState.Signal || state is EngineState.Feedback

    fun start(nowNanos: Long, pedalDown: Boolean) {
        this.pedalDown = pedalDown
        faults = 0
        slot = 0
        attempts.clear()
        val greenSlot = rng.nextInt(PedalConfig.GREEN_SLOT_MIN, PedalConfig.GREEN_SLOT_MAX_EXCLUSIVE)
        plan = List(PedalConfig.RED_TRIALS + 1) { if (it == greenSlot) SignalKind.GREEN else SignalKind.RED }
        beginWaiting(nowNanos)
    }

    /** 사용자가 취소했거나 기어·주행 규제 같은 조건이 깨졌을 때. 끝난/멈춘 뒤에는 무시. */
    fun abort(reason: AbortReason) {
        if (!isRunning) return
        state = EngineState.Aborted(reason)
    }

    fun onPedal(event: PedalEvent) {
        val wasDown = pedalDown
        pedalDown = event.pressed
        if (event.pressed == wasDown) return // 같은 상태가 또 오면 무시(반복 입력)
        logger?.invoke("PEDAL pressed=${event.pressed} t=${event.tNanos}")

        if (!event.pressed) {
            if (state is EngineState.WaitRelease) beginWaiting(event.tNanos)
            return
        }
        when (val s = state) {
            is EngineState.Waiting -> fault(Verdict.FALSE_START, s.slot, s.kind, s.waitMs, null, event.tNanos, null, event.tNanos)
            is EngineState.Signal -> onPressDuringSignal(s, event.tNanos)
            else -> Unit // 쉼·손 떼기 대기·끝난 뒤의 밟음은 무시
        }
    }

    fun tick(nowNanos: Long) {
        when (val s = state) {
            is EngineState.Waiting -> if (nowNanos >= s.dueAtNanos) {
                state = EngineState.Signal(s.slot, s.kind, nowNanos, s.waitMs)
                logger?.invoke("SIGNAL_ON kind=${s.kind.name.lowercase()} t=$nowNanos")
            }
            is EngineState.Signal -> {
                val elapsedMs = (nowNanos - s.shownAtNanos) / NS_PER_MS
                if (s.kind == SignalKind.RED) {
                    if (elapsedMs >= PedalConfig.MISS_MS) {
                        fault(Verdict.MISSED, s.slot, s.kind, s.waitMs, s.shownAtNanos, null, null, nowNanos)
                    }
                } else if (elapsedMs >= PedalConfig.GREEN_HOLD_MS + PedalConfig.GREEN_GRACE_MS) {
                    finishTrial(Verdict.HELD, s, null, null, nowNanos)
                }
            }
            is EngineState.Feedback -> if (nowNanos >= s.untilNanos) advance(s, nowNanos)
            else -> Unit
        }
    }

    // ------------------------------------------------------------------

    private fun onPressDuringSignal(s: EngineState.Signal, pressedAt: Long) {
        if (s.kind == SignalKind.GREEN) {
            finishTrial(Verdict.WRONG_PRESS, s, pressedAt, null, pressedAt)
            return
        }
        val rtMs = Math.round((pressedAt - s.shownAtNanos).toDouble() / NS_PER_MS).toInt()
        when {
            rtMs < PedalConfig.TOO_FAST_MS ->
                fault(Verdict.TOO_FAST, s.slot, s.kind, s.waitMs, s.shownAtNanos, pressedAt, rtMs, pressedAt)
            rtMs > PedalConfig.MISS_MS ->
                fault(Verdict.MISSED, s.slot, s.kind, s.waitMs, s.shownAtNanos, null, null, pressedAt)
            else -> finishTrial(Verdict.NORMAL, s, pressedAt, rtMs, pressedAt)
        }
    }

    /** 다시 해야 하는 판정(부정 출발·너무 빠름·놓침). 합쳐서 [PedalConfig.MAX_FAULTS]번째면 중단. */
    private fun fault(
        verdict: Verdict, slot: Int, kind: SignalKind, waitMs: Int,
        shownAt: Long?, pressedAt: Long?, rtMs: Int?, nowNanos: Long,
    ) {
        faults++
        attempts += TrialRecord(slot, kind, waitMs, shownAt, pressedAt, rtMs, verdict)
        logger?.invoke("RESULT rt=${rtMs ?: "-"} verdict=${verdict.name}")
        if (faults >= PedalConfig.MAX_FAULTS) {
            state = EngineState.Aborted(AbortReason.TOO_MANY_FAULTS)
            return
        }
        state = EngineState.Feedback(slot, kind, verdict, rtMs, repeat = true,
            untilNanos = nowNanos + PedalConfig.FAULT_PAUSE_MS * NS_PER_MS)
    }

    private fun finishTrial(verdict: Verdict, s: EngineState.Signal, pressedAt: Long?, rtMs: Int?, nowNanos: Long) {
        attempts += TrialRecord(s.slot, s.kind, s.waitMs, s.shownAtNanos, pressedAt, rtMs, verdict)
        logger?.invoke("RESULT rt=${rtMs ?: "-"} verdict=${verdict.name}")
        state = EngineState.Feedback(s.slot, s.kind, verdict, rtMs, repeat = false,
            untilNanos = nowNanos + PedalConfig.REST_MS * NS_PER_MS)
    }

    private fun advance(f: EngineState.Feedback, nowNanos: Long) {
        if (!f.repeat) slot++
        if (slot >= plan.size) {
            state = EngineState.Finished(buildResult())
        } else {
            beginWaiting(nowNanos)
        }
    }

    private fun beginWaiting(nowNanos: Long) {
        if (pedalDown) {
            state = EngineState.WaitRelease
            return
        }
        val waitMs = rng.nextInt(PedalConfig.WAIT_MIN_MS, PedalConfig.WAIT_MAX_MS + 1)
        state = EngineState.Waiting(slot, plan[slot], nowNanos + waitMs * NS_PER_MS, waitMs)
    }

    private fun buildResult(): SessionResult {
        val normal = attempts.withIndex().filter { it.value.verdict == Verdict.NORMAL }
        val trim = TrimmedMean.of(normal.map { it.value.reactionMs!! })
        val excludedAttemptIdx = trim.excludedIndices.map { normal[it].index }.toSet()
        val trials = attempts.mapIndexed { i, t -> if (i in excludedAttemptIdx) t.copy(excludedFromMean = true) else t }
        return SessionResult(trim.meanMs, trials)
    }
}
