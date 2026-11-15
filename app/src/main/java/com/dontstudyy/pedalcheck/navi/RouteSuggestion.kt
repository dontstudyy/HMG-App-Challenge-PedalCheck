package com.dontstudyy.pedalcheck.navi

/** 출발 전 제안 카드에 쓰는 정보. */
data class Suggestion(val destination: String, val distanceKm: Int)

/**
 * 장거리 길안내를 시작했고 오늘 아직 측정하지 않았으면 "출발 전에 30초 체크할까요?"를 제안한다(설계서 B8).
 * 거리 기준(기본 30km)은 기획서에 없는 값이라 설정에서 바꿀 수 있다. 거리는 길안내 이벤트의 값(미터).
 */
object RouteSuggestion {
    const val DEFAULT_KM = 30
    const val MIN_KM = 1
    const val MAX_KM = 999

    fun shouldSuggest(distanceMeters: Int, thresholdKm: Int, measuredToday: Boolean): Boolean =
        !measuredToday && distanceMeters >= thresholdKm * 1000

    /** 카드에 쓰는 km(반올림, 최소 1). */
    fun km(distanceMeters: Int): Int = maxOf(1, Math.round(distanceMeters / 1000.0).toInt())
}
