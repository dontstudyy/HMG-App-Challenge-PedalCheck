package com.dontstudyy.pedalcheck.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSuggestionTest {
    @Test fun 기준_거리_이상이고_오늘_측정이_없으면_제안() {
        assertTrue(RouteSuggestion.shouldSuggest(30_000, 30, measuredToday = false))
        assertTrue(RouteSuggestion.shouldSuggest(82_400, 30, measuredToday = false))
    }

    @Test fun 기준_거리_미만이면_제안하지_않는다() {
        assertFalse(RouteSuggestion.shouldSuggest(29_999, 30, measuredToday = false))
        assertFalse(RouteSuggestion.shouldSuggest(13_308, 30, measuredToday = false)) // 에뮬레이터 강남 길안내(13km)
    }

    @Test fun 오늘_이미_측정했으면_제안하지_않는다() {
        assertFalse(RouteSuggestion.shouldSuggest(82_400, 30, measuredToday = true))
    }

    @Test fun 거리_기준은_설정으로_바꿀_수_있다() {
        assertTrue(RouteSuggestion.shouldSuggest(13_308, 10, measuredToday = false))
        assertFalse(RouteSuggestion.shouldSuggest(82_400, 100, measuredToday = false))
    }

    @Test fun 카드의_km_표시는_반올림하고_최소_1() {
        assertEquals(82, RouteSuggestion.km(82_400))
        assertEquals(13, RouteSuggestion.km(13_308))
        assertEquals(1, RouteSuggestion.km(200))
    }
}
