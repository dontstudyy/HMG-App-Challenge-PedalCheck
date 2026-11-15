package com.dontstudyy.pedalcheck.input

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dontstudyy.pedalcheck.vehicle.VehicleMonitor
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private const val TAG = "PEDAL"

/**
 * 에뮬레이터 창의 스페이스바를 브레이크 페달 입력으로 바꾼다(`tools/key_bridge.py` 없이도 동작).
 *
 * 1. **차량 신호 경로(우선):** 에뮬레이터 안의 Virtual Console(`127.0.0.1:8088`)에 `key_bridge.py`와 같은 요청
 *    (`/vehicle/controls?brake=100|0`)을 보낸다. 차량 신호가 바뀌고 Pleos Brake 리스너가 받아 측정에 들어간다
 *    (설계서가 말한 "실제 브레이크 페달 신호" 경로).
 * 2. **직접 입력(대비):** 요청이 실패하면(Virtual Console 연결 없음 등) 같은 키를 [VehicleMonitor.injectDirect]로
 *    바로 넣는다. 이 경우 차량 신호가 아니므로 [onDirectChanged]로 화면에 알린다.
 *
 * 한 번의 키 입력은 두 길 중 하나로만 처리해 입력이 중복되지 않는다. 모든 메서드는 메인 스레드에서 부른다.
 * 시각은 키 이벤트를 받은 순간(`tNanos`, `System.nanoTime()`)을 쓴다.
 */
class PedalKeyRouter(
    private val monitor: VehicleMonitor,
    private val onDirectChanged: (Boolean) -> Unit,
    private val vcBase: String = "http://127.0.0.1:8088",
) {
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "vc-keys").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private var down = false
    private var viaVehicle = false
    /** 키를 누른 횟수. 실패한 요청이 지금도 유효한 입력의 것인지 가리는 데 쓴다. */
    private var seq = 0
    private var directUntilNanos = 0L

    fun onDown(tNanos: Long) {
        if (down) return // 누르고 있을 때의 반복 입력 무시
        down = true
        if (System.nanoTime() < directUntilNanos) {
            viaVehicle = false
            monitor.injectDirect(true, tNanos)
            return
        }
        onDirectChanged(false)
        viaVehicle = true
        val mine = ++seq
        exec.execute {
            if (!put(PRESS)) {
                main.post {
                    directUntilNanos = System.nanoTime() + (DIRECT_HOLD_SEC * 1e9).toLong()
                    // 그 사이 키를 다시 눌렀다면 그 입력이 알아서 처리한다
                    if (seq == mine) {
                        Log.i(TAG, "KEYS fallback direct")
                        onDirectChanged(true)
                        if (down) viaVehicle = false // 아직 누르고 있으면 뗄 때도 직접 입력으로
                        monitor.injectDirect(true, tNanos)
                        // 전달이 실패하는 사이 키를 이미 뗐어도 짧게 눌린 입력으로 살려 준다
                        if (!down) monitor.injectDirect(false, System.nanoTime())
                    }
                }
            }
        }
    }

    fun onUp(tNanos: Long) {
        if (!down) return
        down = false
        if (viaVehicle) {
            viaVehicle = false
            exec.execute { put(RELEASE) }
        } else {
            monitor.injectDirect(false, tNanos)
        }
    }

    /** 화면을 벗어날 때, 차량 쪽에 눌러 둔 브레이크를 0으로 돌려 놓는다. */
    fun releaseAll() {
        if (down && viaVehicle) exec.execute { put(RELEASE) }
        down = false
        viaVehicle = false
    }

    /** Virtual Console에 PUT. 성공(2xx)이면 true. 실패를 바로 알아야 하므로 시간 제한을 짧게 둔다. */
    private fun put(path: String): Boolean = try {
        val c = URL(vcBase + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "PUT"
            c.connectTimeout = 400
            c.readTimeout = 800
            c.responseCode in 200..299
        } finally {
            c.disconnect()
        }
    } catch (e: Exception) {
        Log.i(TAG, "KEYS put failed: $path ${e.javaClass.simpleName}")
        false
    }

    private companion object {
        const val PRESS = "/vehicle/controls?brake=100"
        const val RELEASE = "/vehicle/controls?brake=0"
        const val DIRECT_HOLD_SEC = 3.0
    }
}
