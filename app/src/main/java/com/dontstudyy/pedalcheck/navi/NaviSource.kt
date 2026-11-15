package com.dontstudyy.pedalcheck.navi

import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * 길안내 시작·종료 이벤트를 받아 목적지 이름과 거리(m)를 전달한다(결과표 N1: `onRouteStarted(duration, distance, tollFare, name)`).
 * 콜백은 메인 스레드로 보낸다. 읽기만 하며 길안내를 직접 걸지 않는다.
 */
class NaviSource(
    private val ctx: Context,
    private val onRoute: (destination: String, distanceMeters: Int) -> Unit,
    private val onCleared: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null

    fun start() {
        if (helper != null) return
        try {
            val n = NaviHelper(ctx)
            n.initialize()
            n.addListener(object : NaviHelperEventListener {
                override fun onRouteStarted(p0: RouteStartInfo) {
                    Log.i(TAG, "onRouteStarted name=${p0.name} distance=${p0.distance} duration=${p0.duration}")
                    main.post { onRoute(p0.name.orEmpty(), p0.distance) }
                }
                override fun onRouteCancelled() { Log.i(TAG, "onRouteCancelled"); main.post { onCleared() } }
                override fun onDestinationArrived(p0: DestinationArrivedInfo) { Log.i(TAG, "onDestinationArrived"); main.post { onCleared() } }
                // 에뮬레이터는 onRouteStarted 직후에 onRouteEnded를 보낸다(경로 계산 완료). 종료로 보면 안 된다.
                override fun onRouteEnded() { Log.i(TAG, "onRouteEnded (무시)") }
            })
            helper = n
        } catch (t: Throwable) {
            Log.w(TAG, "navi start failed: $t")
        }
    }

    fun stop() {
        runCatching { helper?.release() }
        helper = null
    }

    private companion object {
        const val TAG = "PEDAL_NAVI"
    }
}
