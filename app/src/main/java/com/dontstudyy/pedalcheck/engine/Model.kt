package com.dontstudyy.pedalcheck.engine

/** 신호 원의 색. */
enum class SignalKind { RED, GREEN }

/** 한 회차의 판정. 저장할 때 이름(`name`)을 그대로 쓴다. */
enum class Verdict {
    /** 빨강 신호 뒤 정상 범위에서 밟음 */
    NORMAL,
    /** 신호 전에 밟음 */
    FALSE_START,
    /** 신호 직후 [PedalConfig.TOO_FAST_MS] 안에 밟음(예측) */
    TOO_FAST,
    /** [PedalConfig.MISS_MS] 안에 밟지 않음 */
    MISSED,
    /** 초록 신호를 끝까지 참음 */
    HELD,
    /** 초록 신호에서 밟음 */
    WRONG_PRESS,
}

/** 측정이 중간에 멈춘 이유. */
enum class AbortReason {
    GEAR_CHANGED,
    PARKING_BRAKE_RELEASED,
    DRIVING_RESTRICTION,
    TOO_MANY_FAULTS,
    CANCELED,
}

/** 브레이크 리스너가 받은 한 번의 변화. 시각은 `System.nanoTime()` (리스너 첫 줄에서 찍는다). */
data class PedalEvent(val pressed: Boolean, val tNanos: Long)

/** 난수 주입용. 범위는 [from, until). */
interface Rng {
    fun nextInt(from: Int, until: Int): Int
}

class RandomRng(private val random: kotlin.random.Random = kotlin.random.Random.Default) : Rng {
    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)
}

/**
 * 측정 상수. 설계서 6장의 값 중 목업·기획서에 없는 것(100ms, 1.5초, 4번째 중단)은
 * 키보드로 직접 재 본 뒤 여기만 고치면 된다.
 */
object PedalConfig {
    const val RED_TRIALS = 5
    /** 초록 위치는 6회차 중 2~5번째 */
    const val GREEN_SLOT_MIN = 1
    const val GREEN_SLOT_MAX_EXCLUSIVE = 5
    const val WAIT_MIN_MS = 1500
    const val WAIT_MAX_MS = 4000
    /** 이보다 빨리 밟으면 신호를 보고 밟은 것이 아니다 */
    const val TOO_FAST_MS = 100
    const val MISS_MS = 1500
    const val GREEN_HOLD_MS = 1500
    /** 초록이 꺼진 뒤에도 이만큼은 '초록을 보고 밟은 것'으로 본다 */
    const val GREEN_GRACE_MS = 300
    /** 회차 사이 쉼(결과 숫자를 보여 주는 시간 포함) */
    const val REST_MS = 1000
    /** 부정 출발·놓침 안내를 보여 주는 시간 */
    const val FAULT_PAUSE_MS = 1500
    /** 부정 출발·너무 빠름·놓침이 합쳐서 이 횟수가 되면 중단 */
    const val MAX_FAULTS = 4
}

/** 한 번 시도의 기록. 다시 재는 회차는 시도마다 따로 남는다. */
data class TrialRecord(
    val slot: Int,
    val kind: SignalKind,
    /** 신호 전 대기 시간. 부정 출발이면 실제로 기다린 시간이 아니라 정해졌던 값 */
    val waitMs: Int,
    val shownAtNanos: Long?,
    val pressedAtNanos: Long?,
    val reactionMs: Int?,
    val verdict: Verdict,
    val excludedFromMean: Boolean = false,
)

/** 완료된 한 번의 테스트. */
data class SessionResult(
    val meanMs: Int,
    val trials: List<TrialRecord>,
) {
    val falseStarts: Int get() = trials.count { it.verdict == Verdict.FALSE_START || it.verdict == Verdict.TOO_FAST }
    val wrongPresses: Int get() = trials.count { it.verdict == Verdict.WRONG_PRESS }
    val misses: Int get() = trials.count { it.verdict == Verdict.MISSED }
    val normalReactions: List<Int> get() = trials.filter { it.verdict == Verdict.NORMAL }.mapNotNull { it.reactionMs }
}

sealed interface EngineState {
    data object Idle : EngineState

    /** 발을 뗄 때까지 기다림(시작 전, 또는 회차 사이) */
    data object WaitRelease : EngineState

    /** 회색 원 "기다리세요". [dueAtNanos]에 신호를 켠다. */
    data class Waiting(val slot: Int, val kind: SignalKind, val dueAtNanos: Long, val waitMs: Int) : EngineState

    /** 신호가 켜진 상태. [shownAtNanos]는 신호가 그려진 프레임의 시각. */
    data class Signal(val slot: Int, val kind: SignalKind, val shownAtNanos: Long, val waitMs: Int) : EngineState

    /** 판정 결과를 보여 주는 쉼. [repeat]이면 같은 회차를 다시 한다. */
    data class Feedback(
        val slot: Int,
        val kind: SignalKind,
        val verdict: Verdict,
        val reactionMs: Int?,
        val repeat: Boolean,
        val untilNanos: Long,
    ) : EngineState

    data class Finished(val result: SessionResult) : EngineState

    data class Aborted(val reason: AbortReason) : EngineState
}

/** 정상 빨강 5개에서 가장 빠른/느린 값을 빼고 남은 3개의 평균. */
class TrimmedMean private constructor(val meanMs: Int, val excludedIndices: Set<Int>) {
    companion object {
        /** 같은 값이 여러 개면 앞 회차를 뺀다. 모두 같아도 서로 다른 두 회차를 뺀다. */
        fun of(reactionsMs: List<Int>): TrimmedMean {
            require(reactionsMs.size >= 3) { "평균을 내려면 3개 이상 필요" }
            val minIdx = reactionsMs.indices.minBy { reactionsMs[it] } // 동률이면 앞쪽
            val maxIdx = reactionsMs.indices.filter { it != minIdx }.maxBy { reactionsMs[it] }
            // maxBy는 동률이면 처음 만난 것(앞 회차)을 돌려준다
            val kept = reactionsMs.filterIndexed { i, _ -> i != minIdx && i != maxIdx }
            val mean = Math.round(kept.sum().toDouble() / kept.size).toInt()
            return TrimmedMean(mean, setOf(minIdx, maxIdx))
        }
    }
}
