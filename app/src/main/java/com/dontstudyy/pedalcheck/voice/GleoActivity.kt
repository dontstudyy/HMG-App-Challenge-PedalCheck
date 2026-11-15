package com.dontstudyy.pedalcheck.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.dontstudyy.pedalcheck.MainActivity

/**
 * Gleo 음성 명령 진입점(화면 없음). intent의 action이 곧 기능 이름이다(결과표 V3).
 * 실제 Gleo 호출은 심사 후 활성화되므로 시연은 adb로 같은 intent를 보낸다.
 *   adb shell am start -a PEDAL_CHECK_START   → 실행 조건 화면
 *   adb shell am start -a MY_PEDAL_TREND      → 추이 화면
 * 두 기능 모두 차량에 아무것도 쓰지 않는다. 주행 중이면 앱이 "주행 중에는 쓸 수 없어요"로 막는다.
 */
class GleoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = when (intent.action) {
            ACTION_START -> MainActivity.TARGET_CONDITIONS
            ACTION_TREND -> MainActivity.TARGET_TREND
            else -> null
        }
        Log.i(TAG, "action=${intent.action} -> $target")
        if (target != null) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(MainActivity.EXTRA_TARGET, target)
            )
        }
        finish()
    }

    private companion object {
        const val TAG = "PEDAL_GLEO"
        const val ACTION_START = "PEDAL_CHECK_START"
        const val ACTION_TREND = "MY_PEDAL_TREND"
    }
}
