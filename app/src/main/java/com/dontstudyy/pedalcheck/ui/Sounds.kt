package com.dontstudyy.pedalcheck.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.dontstudyy.pedalcheck.R
import com.dontstudyy.pedalcheck.engine.Verdict

/**
 * 앱 효과음. 판정(제대로 밟음, 부정 출발, 참기 성공)과 결과에만 소리를 낸다.
 * **빨강 신호에는 소리가 없다**: 소리를 섞으면 시각+청각 반응이 되어 날마다 조건이 달라진다(설계서 B4).
 * 제대로 밟은 소리는 밟은 뒤(판정 화면)에 나므로 반응시간 측정에는 영향이 없다.
 */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val result = pool.load(context, R.raw.result, 1)
    private val correct = pool.load(context, R.raw.check_tick, 1)
    private val trendNotice = pool.load(context, R.raw.trend_notice, 1)
    private val falseStart = pool.load(context, R.raw.false_start_buzz, 1)
    private val holdOk = pool.load(context, R.raw.hold_ok, 1)

    fun onVerdict(v: Verdict) {
        when (v) {
            Verdict.NORMAL -> play(correct)
            Verdict.FALSE_START, Verdict.TOO_FAST -> play(falseStart)
            Verdict.HELD -> play(holdOk)
            else -> Unit // 놓침·잘못 밟음에는 소리 없음
        }
    }

    fun onResult() = play(result)

    /** 3개월 안내 화면이 열릴 때 */
    fun onTrendNotice() = play(trendNotice)

    private fun play(id: Int) {
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
