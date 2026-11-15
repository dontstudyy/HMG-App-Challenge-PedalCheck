package com.dontstudyy.pedalcheck.baseline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY = 24L * 60 * 60 * 1000

class ComparisonTest {

    @Test fun 기준선600_오늘620_3퍼센트_평소범위() {
        val c = Comparison.of(todayMs = 620, baselineMs = 600)
        assertEquals(3, c.percent)
        assertEquals(Direction.SLOWER, c.direction)
        assertTrue(c.withinUsual)
        assertFalse(c.suggestBreak)
        assertNull(c.extraMeters) // 거리 문구 없음
        assertNull(c.extraSeconds)
    }

    @Test fun 기준선600_오늘710_18퍼센트_거리_쉬어가기() {
        val c = Comparison.of(todayMs = 710, baselineMs = 600)
        assertEquals(18, c.percent)
        assertEquals(Direction.SLOWER, c.direction)
        assertFalse(c.withinUsual)
        assertTrue(c.suggestBreak)
        assertEquals(0.11, c.extraSeconds!!, 1e-9)
        assertEquals(1.8, c.extraMeters!!, 1e-9)
    }

    @Test fun 기준선600_오늘690_15퍼센트는_평소범위() {
        val c = Comparison.of(todayMs = 690, baselineMs = 600)
        assertEquals(15, c.percent)
        assertTrue(c.withinUsual)
        assertFalse(c.suggestBreak) // 15% '넘게'가 아니다
    }

    @Test fun 반올림해서_16이면_쉬어가기() {
        assertEquals(16, Comparison.of(696, 600).percent)
        assertTrue(Comparison.of(696, 600).suggestBreak)
        assertFalse(Comparison.of(692, 600).suggestBreak) // 15.33 → 15
    }

    @Test fun 빨라진_경우() {
        val c = Comparison.of(todayMs = 570, baselineMs = 600)
        assertEquals(5, c.percent)
        assertEquals(Direction.FASTER, c.direction)
        assertTrue(c.withinUsual)
        assertFalse(c.suggestBreak)
    }

    @Test fun 같은_경우() {
        val c = Comparison.of(todayMs = 600, baselineMs = 600)
        assertEquals(0, c.percent)
        assertEquals(Direction.SAME, c.direction)
    }

    @Test fun 반올림해서_0이면_평소와_같아요() {
        assertEquals(Direction.SAME, Comparison.of(602, 600).direction) // 0.33 → 0
    }

    @Test fun 거리환산_008초_늦음_13미터() {
        assertEquals(1.3, Distance.extraMeters(80), 1e-9) // 0.08 x 16.67 = 1.33
        assertEquals(1.8, Distance.extraMeters(110), 1e-9)
    }

    @Test fun 초_두자리_표시() {
        assertEquals("0.62", Format.seconds(620))
        assertEquals("0.71", Format.seconds(710))
        assertEquals("0.60", Format.seconds(600))
        assertEquals("0.63", Format.seconds(625)) // 반올림
        assertEquals("1.05", Format.seconds(1050))
    }
}

class BaselineBuilderTest {

    private fun s(day: Int, ms: Int) = SessionPoint(startedAtMillis = day * DAY, meanMs = ms)

    @Test fun 네_개면_만드는_중_4_of_5() {
        val st = BaselineBuilder.status(listOf(s(0, 600), s(1, 610), s(2, 590), s(3, 600)), nowMillis = 20 * DAY)
        st as BaselineStatus.Building
        assertEquals(4, st.count)
    }

    @Test fun 세션이_없으면_0_of_5() {
        val st = BaselineBuilder.status(emptyList(), nowMillis = 0)
        assertEquals(0, (st as BaselineStatus.Building).count)
    }

    @Test fun 다섯_개여도_14일이_안_지났으면_아직() {
        val sessions = listOf(s(0, 600), s(1, 610), s(2, 590), s(3, 600), s(4, 620))
        val st = BaselineBuilder.status(sessions, nowMillis = 7 * DAY) as BaselineStatus.Building
        assertEquals(5, st.count)
        assertEquals(7, st.daysLeft)
    }

    @Test fun 첫_세션부터_14일이_지나고_5개_이상이면_중앙값() {
        val sessions = listOf(s(0, 600), s(2, 640), s(4, 590), s(6, 610), s(8, 620))
        val st = BaselineBuilder.status(sessions, nowMillis = 14 * DAY) as BaselineStatus.Ready
        assertEquals(610, st.baselineMs) // 590 600 610 620 640
        assertEquals(5, st.sessionCount)
    }

    @Test fun 짝수_개는_가운데_두_값의_평균() {
        val sessions = listOf(s(0, 600), s(1, 610), s(2, 620), s(3, 630), s(4, 640), s(5, 650))
        val st = BaselineBuilder.status(sessions, nowMillis = 14 * DAY) as BaselineStatus.Ready
        assertEquals(625, st.baselineMs)
    }

    @Test fun 기준_14일_뒤의_세션은_기준선에_들어가지_않는다() {
        val sessions = listOf(s(0, 600), s(1, 600), s(2, 600), s(3, 600), s(4, 600), s(20, 900))
        val st = BaselineBuilder.status(sessions, nowMillis = 30 * DAY) as BaselineStatus.Ready
        assertEquals(600, st.baselineMs)
        assertEquals(5, st.sessionCount)
    }

    @Test fun 기준_14일_안에_5개가_안_되면_다섯_번째_세션까지_기다린다() {
        val four = listOf(s(0, 600), s(3, 610), s(6, 620), s(9, 630))
        assertTrue(BaselineBuilder.status(four, nowMillis = 18 * DAY) is BaselineStatus.Building)
        val five = four + s(20, 640)
        val st = BaselineBuilder.status(five, nowMillis = 20 * DAY) as BaselineStatus.Ready
        assertEquals(620, st.baselineMs)
        assertEquals(5, st.sessionCount)
    }
}

class TrendTest {

    @Test fun 월별_600_610_620_이면_3개월_안내() {
        assertTrue(Trend.threeMonthNotice(listOf(600, 610, 620)))
    }

    @Test fun 월별_600_620_610_이면_안내_없음() {
        assertFalse(Trend.threeMonthNotice(listOf(600, 620, 610)))
    }

    @Test fun 같은_값이_섞이면_안내_없음() {
        assertFalse(Trend.threeMonthNotice(listOf(600, 600, 610)))
    }

    @Test fun 달이_3개_미만이면_안내_없음() {
        assertFalse(Trend.threeMonthNotice(listOf(600, 620)))
    }

    @Test fun 최근_3개월만_본다() {
        assertTrue(Trend.threeMonthNotice(listOf(700, 600, 610, 620)))
    }

    private fun mv(ym: Int, v: Int, n: Int = 5) = MonthValue(ym, v, n)

    @Test fun 안내는_이어진_세_달이_이번_달까지_매달_커질_때만() {
        val up = listOf(mv(202609, 600), mv(202610, 610), mv(202611, 620))
        assertTrue(Trend.notice(up, currentYearMonth = 202611))
        // 이번 달이 아니면(오래전 기록) 띄우지 않는다
        assertFalse(Trend.notice(up, currentYearMonth = 202612))
        // 달이 비어 있으면(9, 10, 12월) "석 달 연속"이 아니다
        assertFalse(Trend.notice(listOf(mv(202609, 600), mv(202610, 610), mv(202612, 620)), 202612))
        // 내려갔다 올라가면 안 뜬다
        assertFalse(Trend.notice(listOf(mv(202609, 600), mv(202610, 620), mv(202611, 610)), 202611))
        assertFalse(Trend.notice(up.take(2), 202611))
    }

    @Test fun 안내_판정은_해가_바뀌어도_이어진_달로_본다() {
        val up = listOf(mv(202511, 600), mv(202512, 610), mv(202601, 620))
        assertTrue(Trend.notice(up, 202601))
        assertEquals(202601, Trend.nextMonth(202512))
        assertEquals(202610, Trend.nextMonth(202609))
    }

    @Test fun 안내는_정렬되지_않은_입력도_최근_3개월로_판단() {
        val shuffled = listOf(mv(202611, 620), mv(202608, 900), mv(202609, 600), mv(202610, 610))
        assertTrue(Trend.notice(shuffled, 202611))
    }

    @Test fun 월별_중앙값() {
        val pts = listOf(
            MonthPoint(202609, 590), MonthPoint(202609, 600), MonthPoint(202609, 610),
            MonthPoint(202610, 620), MonthPoint(202610, 600),
        )
        val m = Trend.monthly(pts)
        assertEquals(listOf(202609, 202610), m.map { it.yearMonth })
        assertEquals(600, m[0].medianMs)
        assertEquals(3, m[0].sessionCount)
        assertEquals(610, m[1].medianMs) // 짝수: (600 + 620) / 2
    }
}
