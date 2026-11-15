package com.dontstudyy.pedalcheck.debug

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * debug 빌드 전용. 에뮬레이터에서 길안내를 걸어 출발 전 제안을 시험한다.
 *   adb shell am broadcast -n com.dontstudyy.pedalcheck/.debug.DebugRouteReceiver --ed lat 36.8151 --ed lon 127.1139 --es name "천안"
 *   adb shell am broadcast -n com.dontstudyy.pedalcheck/.debug.DebugRouteReceiver --es cmd cancel
 */
class DebugRouteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val helper = NaviHelper(context.applicationContext)
        helper.initialize(
            {
                try {
                    if (intent.getStringExtra("cmd") == "cancel") {
                        helper.cancelRoute()
                        Log.i(TAG, "cancelRoute")
                    } else {
                        val lat = intent.getDoubleExtra("lat", 37.4979)
                        val lon = intent.getDoubleExtra("lon", 127.0276)
                        val name = intent.getStringExtra("name") ?: "Gangnam"
                        helper.requestRoute(RouteInfo(latitude = lat, longitude = lon, poiName = name, poiId = "", address = name, poiSubId = ""))
                        Log.i(TAG, "requestRoute $name ($lat, $lon)")
                    }
                } finally {
                    pending.finish()
                }
            },
            { e -> Log.w(TAG, "navi init failed: $e"); pending.finish() },
        )
    }

    private companion object {
        const val TAG = "PEDAL_DEBUG"
    }
}
