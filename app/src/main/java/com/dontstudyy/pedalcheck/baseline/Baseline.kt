package com.dontstudyy.pedalcheck.baseline

import kotlin.math.abs
import kotlin.math.roundToInt

enum class Direction { SLOWER, FASTER, SAME }

/** 오늘 평균과 기준선의 비교. 화면 문구는 UI가 고른다(합격/불합격 표현 없음). */
data class Comparison(
    /** 부호 없는 반올림 정수 %. 방향은 [direction] */
    val percent: Int,
    val direction: Direction,
    /** 화면에 보이는(반올림한) %가 15 이하면 평소 범위 */
    val withinUsual: Boolean,
    /** 반올림한 %가 16 이상(기획서 "15% 넘게") */
    val suggestBreak: Boolean,
    /** 쉬어 가기 제안일 때만: 늦은 시간(초) */
    val extraSeconds: Double?,
    /** 쉬어 가기 제안일 때만: 시속 60km 기준 늦게 멈추는 거리(m) */
    val extraMeters: Double?,
) {
    companion object {
        const val USUAL_MAX_PERCENT = 15

        fun of(todayMs: Int, baselineMs: Int): Comparison {
            require(baselineMs > 0)
            val signed = ((todayMs - baselineMs) * 100.0 / baselineMs).roundToInt()
            val pct = abs(signed)
            val dir = when {
                signed > 0 -> Direction.SLOWER
                signed < 0 -> Direction.FASTER
                else -> Direction.SAME
            }
            val suggest = dir == Direction.SLOWER && pct > USUAL_MAX_PERCENT
            val delta = todayMs - baselineMs
            return Comparison(
                percent = pct,
                direction = dir,
                withinUsual = !suggest,
                suggestBreak = suggest,
                extraSeconds = if (suggest) delta / 1000.0 else null,
                extraMeters = if (suggest) Distance.extraMeters(delta) else null,
            )
        }
    }
}

object Distance {
    /** 시속 60km = 초속 16.67m. 소수 첫째 자리 반올림. */
    fun extraMeters(deltaMs: Int, speedKmh: Int = 60): Double {
        val m = deltaMs / 1000.0 * speedKmh / 3.6
        return (m * 10).roundToInt() / 10.0
    }
}

object Format {
    /** 정수 ms → "0.62" (반올림). 화면에서만 초로 바꾼다. */
    fun seconds(ms: Int): String {
        val centi = (ms + 5) / 10
        return "%d.%02d".format(centi / 100, centi % 100)
    }
}

/** 기준선에 쓸 세션 하나(세션 평균과 시작 시각). */
data class SessionPoint(val startedAtMillis: Long, val meanMs: Int)

sealed interface BaselineStatus {
    /** 기준선 만드는 중: [count]개 쌓임(5개 필요). 14일이 안 지났으면 [daysLeft] > 0 */
    data class Building(val count: Int, val daysLeft: Int) : BaselineStatus
    data class Ready(val baselineMs: Int, val sessionCount: Int) : BaselineStatus
}

object BaselineBuilder {
    const val NEEDED = 5
    const val WINDOW_DAYS = 14
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 기준선 = 처음 완료한 세션부터 14일 동안의 세션 평균들의 중앙값. 5개 미만이면 만들지 않는다.
     * 14일 안에 5개가 안 모이면 다섯 번째 세션이 생기는 시점까지 기간이 늘어난다.
     */
    fun status(sessions: List<SessionPoint>, nowMillis: Long): BaselineStatus {
        val sorted = sessions.sortedBy { it.startedAtMillis }
        if (sorted.isEmpty()) return BaselineStatus.Building(0, WINDOW_DAYS)
        val first = sorted.first().startedAtMillis
        val windowEnd = maxOf(first + WINDOW_DAYS * DAY_MS, sorted.getOrNull(NEEDED - 1)?.startedAtMillis ?: Long.MAX_VALUE)
        val inWindow = sorted.filter { it.startedAtMillis <= windowEnd }
        if (sorted.size < NEEDED || nowMillis < windowEnd) {
            val left = if (nowMillis >= windowEnd) 0 else ((windowEnd - nowMillis + DAY_MS - 1) / DAY_MS).toInt()
            return BaselineStatus.Building(minOf(sorted.size, 99), left)
        }
        return BaselineStatus.Ready(median(inWindow.map { it.meanMs }), inWindow.size)
    }

    /** 짝수 개면 가운데 두 값의 평균(반올림). */
    fun median(values: List<Int>): Int {
        require(values.isNotEmpty())
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else Math.round((s[n / 2 - 1] + s[n / 2]) / 2.0).toInt()
    }
}

/** yyyyMM 달의 세션 평균 하나. */
data class MonthPoint(val yearMonth: Int, val meanMs: Int)

data class MonthValue(val yearMonth: Int, val medianMs: Int, val sessionCount: Int) {
    /** 세션 3개 미만인 달은 그래프에서 점선 */
    val dashed: Boolean get() = sessionCount < 3
}

object Trend {
    fun monthly(points: List<MonthPoint>): List<MonthValue> =
        points.groupBy { it.yearMonth }.toSortedMap().map { (ym, list) ->
            MonthValue(ym, BaselineBuilder.median(list.map { it.meanMs }), list.size)
        }

    /** 최근 3개월 월별 값이 매달 앞 달보다 커졌을 때(엄격히). */
    fun threeMonthNotice(monthlyMedians: List<Int>): Boolean {
        if (monthlyMedians.size < 3) return false
        val (a, b, c) = monthlyMedians.takeLast(3)
        return a < b && b < c
    }

    /**
     * 3개월 안내를 띄울 조건: 기록이 있는 가장 최근 3개월이 **이어진 달**이고, 마지막 달이 **이번 달**이며,
     * 월별 값이 매달 커졌을 때. 오래전 기록으로 지금 안내가 뜨거나, 달이 비어 있는데 "석 달"이라고 하지 않게 한다.
     */
    fun notice(months: List<MonthValue>, currentYearMonth: Int): Boolean {
        val last3 = months.sortedBy { it.yearMonth }.takeLast(3)
        if (last3.size < 3) return false
        if (last3.last().yearMonth != currentYearMonth) return false
        if (nextMonth(last3[0].yearMonth) != last3[1].yearMonth || nextMonth(last3[1].yearMonth) != last3[2].yearMonth) return false
        return threeMonthNotice(last3.map { it.medianMs })
    }

    fun nextMonth(yearMonth: Int): Int =
        if (yearMonth % 100 == 12) (yearMonth / 100 + 1) * 100 + 1 else yearMonth + 1
}

/** 밀리초 시각 → yyyyMM(기기의 현지 시간대). */
object YearMonth {
    fun of(millis: Long): Int {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(java.util.Calendar.YEAR) * 100 + (c.get(java.util.Calendar.MONTH) + 1)
    }
}
