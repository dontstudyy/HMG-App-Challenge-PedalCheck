package com.dontstudyy.pedalcheck.data

import com.dontstudyy.pedalcheck.baseline.BaselineBuilder
import com.dontstudyy.pedalcheck.baseline.MonthPoint
import com.dontstudyy.pedalcheck.baseline.SessionPoint
import com.dontstudyy.pedalcheck.baseline.Trend
import com.dontstudyy.pedalcheck.baseline.YearMonth
import java.io.File
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoHistoryTest {
    private fun plan(): DemoPlan {
        val f = listOf("src/main/assets/demo_history.json", "app/src/main/assets/demo_history.json").map(::File).first { it.exists() }
        return DemoHistory.parse(f.readText(Charsets.UTF_8))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, 0, 0) }.timeInMillis

    @Test fun 세션_구성_10_6_5개_기준선은_2달_전_중앙값() {
        val p = plan()
        val byMonth = p.sessions.groupBy { it.monthsAgo }
        assertEquals(10, byMonth[2]!!.size)
        assertEquals(6, byMonth[1]!!.size)
        assertEquals(5, byMonth[0]!!.size)
        assertEquals(p.baselineMs, BaselineBuilder.median(byMonth[2]!!.map { it.meanMs }))
        // 모든 달이 세션 3개 이상이라 그래프가 실선
        assertTrue(byMonth.values.all { it.size >= 3 })
    }

    @Test fun 시연용_기록은_월별_값이_매달_커져서_3개월_안내가_뜬다() {
        val now = at(2026, 11, 15)
        val p = plan()
        val ts = DemoHistory.timestamps(p, now)
        val monthly = Trend.monthly(p.sessions.indices.map { MonthPoint(YearMonth.of(ts[it]), p.sessions[it].meanMs) })
        assertEquals(listOf(202609, 202610, 202611), monthly.map { it.yearMonth })
        assertTrue(monthly[0].medianMs < monthly[1].medianMs && monthly[1].medianMs < monthly[2].medianMs)
        assertTrue(Trend.notice(monthly, 202611))
        assertFalse(monthly.any { it.dashed })
    }

    @Test fun 지우면_안내가_사라진다() {
        // 시연용 기록이 없는 상태(월별 값 없음)에서는 안내가 없다
        assertFalse(Trend.notice(emptyList(), 202611))
    }

    @Test fun 기준선_기간_10세션은_14일_안에_들어가고_기준선이_만들어진다() {
        val now = at(2026, 11, 15)
        val p = plan()
        val ts = DemoHistory.timestamps(p, now)
        val sep = p.sessions.indices.filter { p.sessions[it].monthsAgo == 2 }
        val pts = sep.map { SessionPoint(ts[it], p.sessions[it].meanMs) }
        val span = pts.maxOf { it.startedAtMillis } - pts.minOf { it.startedAtMillis }
        assertTrue(span <= 14L * 24 * 60 * 60 * 1000)
        val st = BaselineBuilder.status(pts, now) as com.dontstudyy.pedalcheck.baseline.BaselineStatus.Ready
        assertEquals(p.baselineMs, st.baselineMs)
    }

    @Test fun 미래_기록은_만들지_않고_순서는_유지한다() {
        // 이번 달 2일에 시연 — 이번 달 3·5·7·9일 기록은 아직 오지 않은 날짜
        val now = at(2026, 11, 2, hour = 10)
        val p = plan()
        val ts = DemoHistory.timestamps(p, now)
        assertTrue(ts.all { it <= now })
        // 같은 달 안에서 입력 순서(날짜순)가 뒤바뀌지 않는다
        val thisMonth = p.sessions.indices.filter { p.sessions[it].monthsAgo == 0 }.map { ts[it] }
        assertEquals(thisMonth.sorted(), thisMonth)
        assertEquals(thisMonth.size, thisMonth.toSet().size)
    }

    @Test fun 해가_바뀌어도_달_계산이_맞다() {
        val now = at(2027, 1, 20)
        val p = plan()
        val ts = DemoHistory.timestamps(p, now)
        val months = p.sessions.indices.map { YearMonth.of(ts[it]) }.toSortedSet().toList()
        assertEquals(listOf(202611, 202612, 202701), months)
    }
}
