package com.dontstudyy.pedalcheck.data

import android.content.Context
import androidx.room.withTransaction
import com.dontstudyy.pedalcheck.baseline.BaselineBuilder
import com.dontstudyy.pedalcheck.baseline.BaselineStatus
import com.dontstudyy.pedalcheck.baseline.Comparison
import com.dontstudyy.pedalcheck.baseline.Direction
import com.dontstudyy.pedalcheck.baseline.MonthPoint
import com.dontstudyy.pedalcheck.baseline.MonthValue
import com.dontstudyy.pedalcheck.baseline.SessionPoint
import com.dontstudyy.pedalcheck.baseline.Trend
import com.dontstudyy.pedalcheck.baseline.YearMonth
import com.dontstudyy.pedalcheck.engine.SessionResult
import com.dontstudyy.pedalcheck.engine.TrialRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** 기준선 쪽 화면 정보. */
sealed interface BaselineView {
    /** 기준선 만드는 중 — 오늘 평균만 보여 주고 비교 문구는 없다 */
    data class Building(val count: Int, val daysLeft: Int) : BaselineView

    data class Ready(val baselineMs: Int, val comparison: Comparison, val method: String) : BaselineView
}

/** 결과 화면(4·5번)에 필요한 것. */
data class ResultUi(
    val meanMs: Int,
    val trials: List<TrialRecord>,
    val baseline: BaselineView,
)

/** 설정(시연 모드)은 단순해서 SharedPreferences, 기록은 Room. 외부 전송 없음. */
class Repository(context: Context) {
    private val appContext = context.applicationContext
    private val db = PedalDatabase.get(context)
    private val dao = db.dao()
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val sessions: Flow<List<SessionEntity>> = dao.observeSessions()
    val baseline: Flow<BaselineEntity?> = dao.observeBaseline()

    /** 월별 값(그 달 세션 평균의 중앙값). 시연용 기록도 포함한다. */
    val monthly: Flow<List<MonthValue>> =
        sessions.map { list -> Trend.monthly(list.map { MonthPoint(YearMonth.of(it.startedAtMillis), it.meanMs) }) }

    private val _demoMode = MutableStateFlow(prefs.getBoolean(KEY_DEMO, false))
    val demoMode: StateFlow<Boolean> = _demoMode.asStateFlow()

    // ---- 가족 공유(미리보기만, 실제 전송 없음) · 이름 · 3개월 안내를 마지막으로 보여 준 달 ----
    private val _familyConsent = MutableStateFlow(prefs.getBoolean(KEY_FAMILY, false))
    val familyConsent: StateFlow<Boolean> = _familyConsent.asStateFlow()

    fun setFamilyConsent(on: Boolean) {
        prefs.edit().putBoolean(KEY_FAMILY, on).apply()
        _familyConsent.value = on
    }

    private val _userName = MutableStateFlow(prefs.getString(KEY_NAME, DEFAULT_NAME) ?: DEFAULT_NAME)
    val userName: StateFlow<String> = _userName.asStateFlow()

    fun setUserName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
        _userName.value = name
    }

    /** 출발 전 제안 거리 기준(km). 기획서에 없는 값이라 설정에서 바꾼다. */
    private val _suggestKm = MutableStateFlow(prefs.getInt(KEY_SUGGEST_KM, com.dontstudyy.pedalcheck.navi.RouteSuggestion.DEFAULT_KM))
    val suggestKm: StateFlow<Int> = _suggestKm.asStateFlow()

    fun setSuggestKm(km: Int) {
        val v = km.coerceIn(com.dontstudyy.pedalcheck.navi.RouteSuggestion.MIN_KM, com.dontstudyy.pedalcheck.navi.RouteSuggestion.MAX_KM)
        prefs.edit().putInt(KEY_SUGGEST_KM, v).apply()
        _suggestKm.value = v
    }

    /** 오늘 실제로 측정한 기록이 있는가(시연용 기록은 제외). */
    suspend fun measuredToday(nowMillis: Long): Boolean {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        val y = c.get(java.util.Calendar.YEAR); val d = c.get(java.util.Calendar.DAY_OF_YEAR)
        return dao.allSessions().any {
            if (it.demo) return@any false
            val s = java.util.Calendar.getInstance().apply { timeInMillis = it.startedAtMillis }
            s.get(java.util.Calendar.YEAR) == y && s.get(java.util.Calendar.DAY_OF_YEAR) == d
        }
    }

    /** 3개월 안내를 마지막으로 보여 준 달(yyyyMM, 없으면 0). 같은 달에는 다시 띄우지 않는다. */
    private val _noticeMonth = MutableStateFlow(prefs.getInt(KEY_NOTICE_MONTH, 0))
    val noticeShownMonth: StateFlow<Int> = _noticeMonth.asStateFlow()

    fun markNoticeShown(yearMonth: Int) {
        prefs.edit().putInt(KEY_NOTICE_MONTH, yearMonth).apply()
        _noticeMonth.value = yearMonth
    }

    // ---- 시연용 기록 ----

    /**
     * `assets/demo_history.json`의 시연용 기록을 넣는다(이전 시연용 기록은 먼저 지움). 기준선도 그 값으로 정한다.
     * 실제로 잰 기록은 건드리지 않는다. 넣은 개수를 돌려준다.
     */
    suspend fun insertDemoHistory(nowMillis: Long): Int {
        val plan = DemoHistory.parse(appContext.assets.open(DemoHistory.ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() })
        val times = DemoHistory.timestamps(plan, nowMillis)
        db.withTransaction {
            dao.clearDemoSessions()
            plan.sessions.forEachIndexed { i, s ->
                dao.insertSession(
                    SessionEntity(
                        startedAtMillis = times[i], meanMs = s.meanMs, percentVsBaseline = null,
                        falseStarts = 0, wrongPresses = 0, misses = 0, demo = true,
                    )
                )
            }
            dao.setBaseline(BaselineEntity(valueMs = plan.baselineMs, createdAtMillis = nowMillis, sessionCount = 0, method = METHOD_DEMO))
        }
        _noticeMonth.value = 0
        prefs.edit().putInt(KEY_NOTICE_MONTH, 0).apply() // 새로 넣었으니 안내를 다시 볼 수 있게
        return plan.sessions.size
    }

    suspend fun clearDemoHistory() = dao.clearDemoSessions()

    fun setDemoMode(on: Boolean) {
        prefs.edit().putBoolean(KEY_DEMO, on).apply()
        _demoMode.value = on
    }

    /** 완료한 세션을 저장하고 기준선과 비교한 결과 화면 정보를 만든다. */
    suspend fun saveSession(result: SessionResult, startedAtMillis: Long, nowMillis: Long): ResultUi {
        val existing = dao.baseline()
        val cmp = existing?.let { Comparison.of(result.meanMs, it.valueMs) }
        val signed = cmp?.let {
            when (it.direction) {
                Direction.SLOWER -> it.percent
                Direction.FASTER -> -it.percent
                Direction.SAME -> 0
            }
        }
        db.withTransaction {
            val id = dao.insertSession(
                SessionEntity(
                    startedAtMillis = startedAtMillis,
                    meanMs = result.meanMs,
                    percentVsBaseline = signed,
                    falseStarts = result.falseStarts,
                    wrongPresses = result.wrongPresses,
                    misses = result.misses,
                )
            )
            dao.insertTrials(result.trials.mapIndexed { i, t ->
                TrialEntity(
                    sessionId = id, attempt = i, slot = t.slot, kind = t.kind.name, waitMs = t.waitMs,
                    shownAtNanos = t.shownAtNanos, pressedAtNanos = t.pressedAtNanos, reactionMs = t.reactionMs,
                    verdict = t.verdict.name, excludedFromMean = t.excludedFromMean,
                )
            })
        }
        val b = existing ?: ensureBaseline(nowMillis)
        return ResultUi(result.meanMs, result.trials, viewOf(result.meanMs, b, nowMillis))
    }

    private suspend fun viewOf(meanMs: Int, b: BaselineEntity?, nowMillis: Long): BaselineView =
        if (b != null) {
            BaselineView.Ready(b.valueMs, Comparison.of(meanMs, b.valueMs), b.method)
        } else {
            val st = BaselineBuilder.status(points(), nowMillis) as? BaselineStatus.Building
                ?: BaselineStatus.Building(0, 0)
            BaselineView.Building(st.count, st.daysLeft)
        }

    /** 기준선이 아직 없고 조건(5개 이상, 14일)이 되면 만들어 저장한다. */
    suspend fun ensureBaseline(nowMillis: Long): BaselineEntity? {
        dao.baseline()?.let { return it }
        val st = BaselineBuilder.status(points(), nowMillis)
        if (st is BaselineStatus.Ready) {
            val b = BaselineEntity(valueMs = st.baselineMs, createdAtMillis = nowMillis, sessionCount = st.sessionCount, method = METHOD_WINDOW)
            dao.setBaseline(b)
            return b
        }
        return null
    }

    suspend fun buildingStatus(nowMillis: Long): BaselineStatus = BaselineBuilder.status(points(), nowMillis)

    /** 시연: 14일을 기다리지 않고 지금까지 기록(5개 이상)으로 기준선을 만든다. */
    suspend fun rebuildBaselineNow(): Boolean {
        val pts = points()
        if (pts.size < BaselineBuilder.NEEDED) return false
        dao.setBaseline(BaselineEntity(
            valueMs = BaselineBuilder.median(pts.map { it.meanMs }),
            createdAtMillis = System.currentTimeMillis(), sessionCount = pts.size, method = METHOD_DEMO,
        ))
        return true
    }

    /** 시연: 기준선 값을 직접 정한다(키보드로 재 본 중앙값을 넣는 용도). */
    suspend fun setDemoBaseline(ms: Int) {
        dao.setBaseline(BaselineEntity(valueMs = ms, createdAtMillis = System.currentTimeMillis(), sessionCount = 0, method = METHOD_DEMO))
    }

    suspend fun clearBaseline() = dao.clearBaseline()

    suspend fun clearAll() {
        dao.clearSessions()
        dao.clearBaseline()
    }

    private suspend fun points(): List<SessionPoint> =
        dao.allSessions().map { SessionPoint(it.startedAtMillis, it.meanMs) }

    companion object {
        private const val KEY_DEMO = "demo_mode"
        private const val KEY_FAMILY = "family_consent"
        private const val KEY_NAME = "user_name"
        private const val KEY_NOTICE_MONTH = "notice_month"
        private const val KEY_SUGGEST_KM = "suggest_km"
        /** 시나리오(기획서 7번)의 사용자. 설정에서 바꿀 수 있다. */
        const val DEFAULT_NAME = "아버지"
        const val METHOD_WINDOW = "WINDOW"
        const val METHOD_DEMO = "DEMO"
    }
}
