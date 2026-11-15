package com.dontstudyy.pedalcheck

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dontstudyy.pedalcheck.data.Repository
import com.dontstudyy.pedalcheck.data.ResultUi
import com.dontstudyy.pedalcheck.engine.AbortReason
import com.dontstudyy.pedalcheck.engine.EngineState
import com.dontstudyy.pedalcheck.engine.MeasureEngine
import com.dontstudyy.pedalcheck.engine.RandomRng
import com.dontstudyy.pedalcheck.engine.TrialRecord
import com.dontstudyy.pedalcheck.gate.Gate
import com.dontstudyy.pedalcheck.gate.GateConfig
import com.dontstudyy.pedalcheck.gate.GateResult
import com.dontstudyy.pedalcheck.input.PedalKeyRouter
import com.dontstudyy.pedalcheck.navi.NaviSource
import com.dontstudyy.pedalcheck.navi.RouteSuggestion
import com.dontstudyy.pedalcheck.navi.Suggestion
import com.dontstudyy.pedalcheck.voice.Speaker
import com.dontstudyy.pedalcheck.vehicle.VehicleMonitor
import com.dontstudyy.pedalcheck.ui.Sounds
import com.dontstudyy.pedalcheck.vehicle.VehicleState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PEDAL"

sealed interface Screen {
    data object Home : Screen
    data object Conditions : Screen
    data object Measure : Screen
    data object Settings : Screen
    data object Trend : Screen
    data object Family : Screen
    data object Notice : Screen
    data class Result(val ui: ResultUi) : Screen
    data class Aborted(val reason: AbortReason) : Screen
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val monitor = VehicleMonitor(app)
    val repo = Repository(app)
    private val sounds = Sounds(app)

    /** 스페이스바 → 브레이크. 차량 신호 경로가 막혔을 때만 직접 입력으로 바뀌고 [keysDirect]가 켜진다. */
    private val keys = PedalKeyRouter(monitor, onDirectChanged = { keysDirect = it })
    private val engine = MeasureEngine(RandomRng()).also { it.logger = { msg -> Log.d(TAG, msg) } }

    val vehicle: StateFlow<VehicleState> = monitor.state

    /** 단계 0 결과로 정한다. 지금은 주차 브레이크 값을 조건에 쓰지 않는다(읽는 값은 화면에 '확인 불가'). */
    var gateConfig by mutableStateOf(GateConfig(parkingBrakeUsable = false))
        private set

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set

    /** 엔진 상태(화면이 읽는 값). 프레임 루프에서 갱신한다. */
    var engineState by mutableStateOf<EngineState>(EngineState.Idle)
        private set

    var attempts by mutableStateOf<List<TrialRecord>>(emptyList())
        private set

    /** 주행 중(기어 D/R 또는 주행 규제)이면 어느 화면에서든 "주행 중에는 쓸 수 없어요" */
    var driving by mutableStateOf(false)
        private set

    /** 장거리 길안내를 시작했고 오늘 측정이 없을 때의 출발 전 제안(홈 카드). 길안내가 끝나거나 측정하면 사라진다. */
    var suggestion by mutableStateOf<Suggestion?>(null)
        private set

    private val navi = NaviSource(
        app,
        onRoute = { dest, meters -> onRoute(dest, meters) },
        onCleared = { suggestion = null },
    )

    /** 키 입력이 차량 신호가 아닌 직접 입력으로 처리되는 중(Virtual Console 연결 실패) */
    var keysDirect by mutableStateOf(false)
        private set

    private var startedAtMillis = 0L
    private var saving = false

    init {
        monitor.start()
        viewModelScope.launch { monitor.state.collect { onVehicle(it) } }
        viewModelScope.launch { repo.ensureBaseline(System.currentTimeMillis()) }
        navi.start()
        // 앱을 열었을 때 시작 질문을 음성으로 한 번만 시도한다(말풍선은 실행 조건 화면에 항상 표시). 오늘 이미 쟀으면 하지 않는다.
        viewModelScope.launch {
            delay(1500)
            if (!repo.measuredToday(System.currentTimeMillis())) Speaker.speak(app, "오늘 30초 체크할까요?")
        }
    }

    private fun onRoute(destination: String, distanceMeters: Int) {
        viewModelScope.launch {
            val measured = repo.measuredToday(System.currentTimeMillis())
            suggestion = if (RouteSuggestion.shouldSuggest(distanceMeters, repo.suggestKm.value, measured)) {
                Suggestion(destination, RouteSuggestion.km(distanceMeters))
            } else null
        }
    }

    fun dismissSuggestion() { suggestion = null }

    /** Gleo 음성 명령 등 밖에서 온 이동 요청. 측정 중에는 끊지 않는다. */
    fun routeTo(target: String) {
        if (screen is Screen.Measure) return
        when (target) {
            "conditions" -> openConditions()
            "trend" -> openTrend()
        }
    }

    /** 권한(CAR_SPEED)이 나중에 허용됐을 때 다시 연결한다. */
    fun restartMonitor() {
        monitor.stop()
        monitor.start()
    }

    // ---------------- 키보드 (MainActivity가 스페이스바 이벤트를 넘김) ----------------

    fun keyDown(tNanos: Long) = keys.onDown(tNanos)
    fun keyUp(tNanos: Long) = keys.onUp(tNanos)
    fun releaseKeys() = keys.releaseAll()

    override fun onCleared() {
        keys.releaseAll()
        navi.stop()
        Speaker.release()
        monitor.stop()
        sounds.release()
    }

    // ---------------- 화면 이동 ----------------

    fun goHome() { screen = Screen.Home }
    fun openConditions() { screen = Screen.Conditions }
    fun openSettings() { screen = Screen.Settings }
    fun openTrend() { screen = Screen.Trend }
    fun openFamily() { screen = Screen.Family }

    /** 3개월 안내(본인에게만). 열면 이번 달에는 다시 띄우지 않도록 표시한다. */
    fun openNotice() {
        repo.markNoticeShown(com.dontstudyy.pedalcheck.baseline.YearMonth.of(System.currentTimeMillis()))
        sounds.onTrendNotice()
        screen = Screen.Notice
    }

    /** 실행 조건이 모두 통과했을 때만 측정을 시작한다. */
    fun startMeasure() {
        if (Gate.evaluateStart(vehicle.value, gateConfig) !is GateResult.Ready) return
        monitor.pedalQueue.clear()
        startedAtMillis = System.currentTimeMillis()
        saving = false
        engine.start(System.nanoTime(), monitor.brakeDown)
        engineState = engine.state
        attempts = emptyList()
        screen = Screen.Measure
    }

    /** 사용자가 직접 멈춤. 기록하지 않는다. */
    fun cancelMeasure() {
        engine.abort(AbortReason.CANCELED)
        engineState = engine.state
        screen = Screen.Home
    }

    // ---------------- 프레임 루프 (측정 화면이 매 프레임 호출) ----------------

    /**
     * [frameNanos]는 `withFrameNanos`가 준 프레임 시각(`System.nanoTime()`과 같은 시계).
     * 신호가 켜진 시각 = 이 프레임 시각. 같은 프레임 안에서 상태가 바뀌어 그려진다.
     */
    fun onFrame(frameNanos: Long) {
        if (screen !is Screen.Measure) return
        val q = monitor.pedalQueue
        // 이 프레임보다 먼저 일어난 밟음을 먼저 처리해야 "신호 전에 밟음"이 정확히 판정된다.
        while (true) {
            val e = q.peek() ?: break
            if (e.tNanos >= frameNanos) break
            q.poll()
            engine.onPedal(e)
        }
        engine.tick(frameNanos)
        while (true) {
            val e = q.poll() ?: break
            engine.onPedal(e)
        }
        val s = engine.state
        if (s != engineState) {
            // 판정 소리는 신호 시각 측정이 끝난 뒤(피드백으로 넘어간 프레임)에만 낸다
            if (s is EngineState.Feedback) sounds.onVerdict(s.verdict)
            engineState = s
        }
        attempts = engine.trials
        when (s) {
            is EngineState.Finished -> finish(s)
            is EngineState.Aborted -> screen = Screen.Aborted(s.reason)
            else -> Unit
        }
    }

    private fun finish(s: EngineState.Finished) {
        if (saving) return
        saving = true
        val started = startedAtMillis
        viewModelScope.launch {
            val ui = repo.saveSession(s.result, started, System.currentTimeMillis())
            Log.d(TAG, "SESSION saved mean=${s.result.meanMs}")
            sounds.onResult()
            suggestion = null // 오늘 측정했으니 출발 전 제안은 더 필요 없다
            screen = Screen.Result(ui)
        }
    }

    // ---------------- 차량 상태 ----------------

    private fun onVehicle(s: VehicleState) {
        Log.d(TAG, "STATE gear=${s.gear} speed=${s.speedMps} parkingBrake=${s.parkingBrake} ux=${s.uxRestricted} brake=${s.brakePressed}")
        // 측정 중 조건이 깨지면 즉시 중단(기록하지 않음)
        if (screen is Screen.Measure && engine.isRunning) {
            val g = Gate.evaluateRunning(s, gateConfig)
            if (g is GateResult.Abort) {
                engine.abort(g.reason)
                engineState = engine.state
                screen = Screen.Aborted(g.reason)
            }
        }
        val nowDriving = Gate.isDriving(s)
        if (driving && !nowDriving && screen !is Screen.Aborted) screen = Screen.Home // 다시 P면 홈
        driving = nowDriving
    }

    // ---------------- 시연·설정 ----------------

    fun setDemoMode(on: Boolean) = repo.setDemoMode(on)
    fun rebuildBaselineNow(onDone: (Boolean) -> Unit) { viewModelScope.launch { onDone(repo.rebuildBaselineNow()) } }
    fun setDemoBaseline(ms: Int) { viewModelScope.launch { repo.setDemoBaseline(ms) } }
    fun clearBaseline() { viewModelScope.launch { repo.clearBaseline() } }
    fun insertDemoHistory(onDone: (Int) -> Unit) { viewModelScope.launch { onDone(repo.insertDemoHistory(System.currentTimeMillis())) } }
    fun clearDemoHistory() { viewModelScope.launch { repo.clearDemoHistory() } }
    fun clearAll() { viewModelScope.launch { repo.clearAll() } }
}
