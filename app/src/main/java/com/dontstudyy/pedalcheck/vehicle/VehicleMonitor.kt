package com.dontstudyy.pedalcheck.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import com.dontstudyy.pedalcheck.engine.PedalEvent
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "PEDAL"

/**
 * 차량 신호를 읽어 [VehicleState]와 브레이크 이벤트 큐로 내보낸다. **읽기 전용**이다.
 *  - 브레이크: Pleos Vehicle `registerBrakePedalStatus` (결과표 R1)
 *  - 기어·차속·주차 브레이크: AAOS `CarPropertyManager`
 *  - 주행 규제: `CarUxRestrictionsManager`
 */
class VehicleMonitor(private val context: Context) {

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    /** 브레이크 이벤트(시각순). 화면의 프레임 루프가 꺼내 엔진에 넣는다. */
    val pedalQueue = ConcurrentLinkedQueue<PedalEvent>()

    /** 지금 밟고 있는가(큐와 별개로 마지막 값). */
    @Volatile
    var brakeDown: Boolean = false
        private set

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var brakeListener: BrakePedalStatusListener? = null
    private var propertyCallback: CarPropertyManager.CarPropertyEventCallback? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        val problems = mutableListOf<String>()
        try {
            startBrake()
        } catch (t: Throwable) {
            problems += "브레이크: ${t.javaClass.simpleName} ${t.message}"
            Log.w(TAG, "brake start FAIL", t)
        }
        try {
            startCarProperties()
        } catch (t: Throwable) {
            problems += "차량 속성: ${t.javaClass.simpleName} ${t.message}"
            Log.w(TAG, "car props start FAIL", t)
        }
        _state.update { it.copy(connected = problems.isEmpty(), error = problems.firstOrNull()) }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { brakeListener?.let { vehicle?.getBrake()?.unregisterBrakePedalStatus(it) } }
        runCatching { vehicle?.release() }
        runCatching { propertyCallback?.let { cb ->
            (car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.unregisterCallback(cb)
        } }
        runCatching { car?.disconnect() }
        vehicle = null
        car = null
        brakeListener = null
        propertyCallback = null
    }

    // ---------------- 브레이크 (Pleos) ----------------

    /**
     * 키 입력 경로가 차량 신호를 보낼 수 없을 때(Virtual Console 연결 실패) 쓰는 직접 입력.
     * 시각은 키 이벤트를 받은 순간의 `System.nanoTime()`이다. 차량 신호가 아니므로 화면에 표시한다.
     */
    fun injectDirect(pressed: Boolean, tNanos: Long) = handleBrake(pressed, tNanos)

    private fun handleBrake(pressed: Boolean, t: Long) {
        Log.d(TAG, "BRAKE pressed=$pressed t=$t") // 검증 로그
        pedalQueue.add(PedalEvent(pressed, t))
        if (pedalQueue.size > 256) pedalQueue.poll() // 측정 중이 아닐 때 쌓이는 것 방지
        brakeDown = pressed
        _state.update { it.copy(brakePressed = pressed) }
    }

    private fun startBrake() {
        val v = Vehicle(context).also { it.initialize() }
        vehicle = v
        val listener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                // 반응시간 측정: 시각은 리스너의 첫 줄에서 찍는다. 다른 작업은 그 뒤에.
                val t = System.nanoTime()
                val pressed = status ?: return
                handleBrake(pressed, t)
            }

            override fun onFailed(e: Exception) {
                Log.w(TAG, "brake listener onFailed $e")
            }
        }
        brakeListener = listener
        v.getBrake().registerBrakePedalStatus(listener)
        v.getBrake().getCurrentBrakePedalStatus(
            { b ->
                if (b != null) {
                    brakeDown = b
                    _state.update { it.copy(brakePressed = b) }
                }
            },
            { e -> Log.w(TAG, "getCurrentBrakePedalStatus FAIL $e") },
        )
    }

    // ---------------- 기어·차속·주차 브레이크·주행 규제 (AAOS) ----------------

    private fun startCarProperties() {
        val c = Car.createCar(context)
        car = c
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager

        // 처음 값 읽기. 하나가 실패해도(권한 등) 나머지는 계속한다.
        readOnce("gear") { _state.update { s -> s.copy(gear = GearPosition.fromAaos(pm.getIntProperty(PROP_GEAR, 0))) } }
        readOnce("speed") { _state.update { s -> s.copy(speedMps = pm.getFloatProperty(PROP_SPEED, 0)) } }
        readOnce("parkingBrake") { _state.update { s -> s.copy(parkingBrake = pm.getBooleanProperty(PROP_PARKING_BRAKE, 0)) } }

        val cb = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                when (value.propertyId) {
                    PROP_GEAR -> (value.value as? Int)?.let { g ->
                        _state.update { it.copy(gear = GearPosition.fromAaos(g)) }
                    }
                    PROP_SPEED -> (value.value as? Float)?.let { sp -> _state.update { it.copy(speedMps = sp) } }
                    PROP_PARKING_BRAKE -> (value.value as? Boolean)?.let { b -> _state.update { it.copy(parkingBrake = b) } }
                }
            }

            override fun onErrorEvent(propId: Int, zone: Int) {
                Log.w(TAG, "car property error prop=0x%08X zone=$zone".format(propId))
            }
        }
        propertyCallback = cb
        register(pm, cb, PROP_GEAR, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        register(pm, cb, PROP_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        register(pm, cb, PROP_PARKING_BRAKE, CarPropertyManager.SENSOR_RATE_ONCHANGE)

        try {
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            _state.update { it.copy(uxRestricted = ux.currentCarUxRestrictions.isRequiresDistractionOptimization) }
            ux.registerListener { r: CarUxRestrictions ->
                _state.update { it.copy(uxRestricted = r.isRequiresDistractionOptimization) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ux restrictions FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun register(pm: CarPropertyManager, cb: CarPropertyManager.CarPropertyEventCallback, id: Int, rate: Float) {
        try {
            val ok = pm.registerCallback(cb, id, rate)
            Log.i(TAG, "register 0x%08X -> $ok".format(id))
        } catch (t: Throwable) {
            Log.w(TAG, "register 0x%08X FAIL ${t.javaClass.simpleName}: ${t.message}".format(id))
        }
    }

    private inline fun readOnce(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "read $name FAIL ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    companion object {
        /** AAOS `PERF_VEHICLE_SPEED` (m/s) — 결과표 A1, 권한 `CAR_SPEED` */
        const val PROP_SPEED = 0x11600207
        /** AAOS `GEAR_SELECTION` (P=4, D=8) — 결과표 A2, 권한 `CAR_POWERTRAIN` */
        const val PROP_GEAR = 0x11400400
        /** AAOS `PARKING_BRAKE_ON` — 결과표 R10 대안. 읽을 수 있는지는 단계 0에서 확인 */
        const val PROP_PARKING_BRAKE = 0x11200402
    }
}
