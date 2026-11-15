package com.dontstudyy.pedalcheck.ui

import com.dontstudyy.pedalcheck.engine.Verdict
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** 판정 연출의 종류. 놓침은 밟은 것이 아니므로 연출이 없다. */
enum class FxKind { NONE, GOOD, BAD }

/**
 * 판정 연출 곡선(목업 영상 `pedal.html`의 `okGlow`·`falseGlow`·`bounce`와 같은 모양).
 * 깜빡임 없이 **한 번 켜졌다 서서히 꺼지고**, 흔들림은 한 번 감쇠하는 약한 진동이다.
 * 시간 `t`는 판정 순간부터의 초.
 */
object FeedbackFx {
    /**
     * 연출이 끝나는 시각(초). 가장 짧은 판정 화면([com.dontstudyy.pedalcheck.engine.PedalConfig.REST_MS], 1.0초) 안에
     * 모든 연출이 끝나야 한다. 다음 스텝이 시작될 때 중간 상태로 남지 않게 하기 위해서다.
     */
    const val DURATION_SEC = 1.0f

    fun kindOf(v: Verdict?): FxKind = when (v) {
        Verdict.NORMAL, Verdict.HELD -> FxKind.GOOD
        Verdict.FALSE_START, Verdict.TOO_FAST, Verdict.WRONG_PRESS -> FxKind.BAD
        Verdict.MISSED, null -> FxKind.NONE
    }

    /** 0.06초에 켜지고, 제대로 밟음은 0.5초 유지 후 0.4초에 걸쳐, 틀린 입력은 0.6초 유지 후 0.4초에 걸쳐 꺼진다. */
    fun glow(kind: FxKind, t: Float): Float = when (kind) {
        FxKind.GOOD -> rise(t, 0.06f) * (1f - rise(t - 0.5f, 0.4f))
        FxKind.BAD -> rise(t, 0.06f) * (1f - rise(t - 0.6f, 0.4f))
        FxKind.NONE -> 0f
    }

    /** 틀린 입력의 빨간 진동선. 1초에 걸쳐 사라진다. */
    fun vibration(kind: FxKind, t: Float): Float =
        if (kind == FxKind.BAD && t in 0f..1f) 1f - t else 0f

    /** 틀린 입력일 때만 화면이 흔들린다(0.6초, 감쇠). [amp]는 단위 그대로 돌려준다. */
    fun shake(kind: FxKind, t: Float, amp: Float, freq: Float = 13f, decay: Float = 4.5f): Float =
        if (kind == FxKind.BAD) bounce(t, amp, freq, decay) else 0f

    fun bounce(d: Float, amp: Float, freq: Float, decay: Float): Float =
        if (d < 0f || d > 0.6f) 0f else amp * sin(2f * PI.toFloat() * freq * d) * exp(-decay * d)

    /** 0→1 부드러운 상승(smoothstep). */
    private fun rise(x: Float, duration: Float): Float {
        val k = (x / duration).coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }
}
