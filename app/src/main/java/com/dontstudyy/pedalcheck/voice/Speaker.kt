package com.dontstudyy.pedalcheck.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.util.Log
import java.util.Locale

/**
 * 음성은 덧붙이는 기능이다. 실패해도 흐름은 멈추지 않고, 같은 말풍선이 항상 화면에 표시된다(설계서 B5).
 * 에뮬레이터에서 재부팅 후 첫 호출만 이벤트가 확실했으므로(결과표 V1) 시작 질문 **한 번만** 시도하고,
 * 측정 중에는 음성 안내를 쓰지 않는다. 결과는 로그 태그 PEDAL_VOICE.
 */
object Speaker {
    private const val TAG = "PEDAL_VOICE"
    private var tts: TextToSpeech? = null

    fun speak(ctx: Context, text: String) {
        try {
            runCatching { tts?.release() }
            val t = TextToSpeech(ctx.applicationContext, Mode.ON_DEVICE)
            tts = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    Log.i(TAG, "init ok")
                    t.addEventListener(object : EventListener {
                        override fun onStart() { Log.i(TAG, "onStart") }
                        override fun onReady() {}
                        override fun onDone() { Log.i(TAG, "onDone") }
                        override fun onError(p0: ErrorType) { Log.w(TAG, "onError $p0") }
                        override fun onStop() {}
                        override fun onUpdatedRms(p0: Double) {}
                    })
                    t.setLocale(Locale.KOREA)
                    t.speak(text, false)
                    Log.i(TAG, "speak: $text")
                }

                override fun onFailure(p0: ErrorType) { Log.w(TAG, "init fail $p0") }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "speak failed: $t")
        }
    }

    fun release() {
        runCatching { tts?.release() }
        tts = null
    }
}
