package com.dontstudyy.pedalcheck.data

import java.util.Calendar
import org.json.JSONObject

/** 시연용 과거 기록 한 건. 날짜는 "몇 달 전 몇 일"로 적어 두어 어느 달에 시연해도 마지막 달이 이번 달이 된다. */
data class DemoSession(val monthsAgo: Int, val day: Int, val hour: Int, val minute: Int, val meanMs: Int)

data class DemoPlan(val baselineMs: Int, val sessions: List<DemoSession>)

/** `tools/seed_history.py`가 만든 `assets/demo_history.json`을 읽어 실제 시각으로 바꾼다. */
object DemoHistory {
    const val ASSET_NAME = "demo_history.json"

    fun parse(json: String): DemoPlan {
        val o = JSONObject(json)
        val arr = o.getJSONArray("sessions")
        val list = List(arr.length()) { i ->
            val s = arr.getJSONObject(i)
            DemoSession(s.getInt("monthsAgo"), s.getInt("day"), s.getInt("hour"), s.getInt("minute"), s.getInt("meanMs"))
        }
        return DemoPlan(o.getInt("baselineMs"), list)
    }

    /**
     * 각 기록의 시작 시각(밀리초). 이번 달의 아직 오지 않은 날짜는 지금보다 앞으로 당겨(순서는 유지) 미래 기록이 생기지 않게 한다.
     * 입력 순서와 같은 순서로 돌려준다.
     */
    fun timestamps(plan: DemoPlan, nowMillis: Long): List<Long> {
        val raw = plan.sessions.map { s ->
            val c = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, -s.monthsAgo)
                set(Calendar.DAY_OF_MONTH, minOf(s.day, getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, s.hour)
                set(Calendar.MINUTE, s.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            c.timeInMillis
        }
        // 시간순으로 뒤에서부터 지금 이전으로 눌러 담는다
        val order = raw.indices.sortedByDescending { raw[it] }
        val result = raw.toMutableList()
        var cap = nowMillis - HOUR_MS
        for (i in order) {
            if (result[i] > cap) result[i] = cap
            cap = result[i] - HOUR_MS
        }
        return result
    }

    private const val HOUR_MS = 60L * 60 * 1000
}
