package com.dontstudyy.pedalcheck.vehicle

/** AAOS `GEAR_SELECTION`(0x11400400) 값. 확인된 값: P=4, D=8 (결과표 A2). R=2, N=1은 AAOS 표준값. */
enum class GearPosition {
    UNKNOWN, PARK, REVERSE, NEUTRAL, DRIVE, OTHER;

    companion object {
        fun fromAaos(value: Int): GearPosition = when (value) {
            4 -> PARK
            2 -> REVERSE
            1 -> NEUTRAL
            8 -> DRIVE
            else -> OTHER
        }
    }
}

/**
 * 차량에서 읽은 값을 하나로 모은 것. 읽을 수 없는 값은 null(주차 브레이크처럼 환경에 따라 못 읽을 수 있다).
 * 이 앱은 차량에 아무것도 쓰지 않는다.
 */
data class VehicleState(
    val connected: Boolean = false,
    /** 연결·구독 실패 메시지(있으면 화면에 "차량 신호를 받을 수 없어요") */
    val error: String? = null,
    val gear: GearPosition = GearPosition.UNKNOWN,
    /** 차속(m/s) */
    val speedMps: Float? = null,
    /** 주차 브레이크 켜짐. null이면 읽을 수 없음 */
    val parkingBrake: Boolean? = null,
    /** 주행 규제 켜짐(`CarUxRestrictionsManager`). null이면 아직 모름 */
    val uxRestricted: Boolean? = null,
    /** 브레이크 페달을 밟고 있는가 */
    val brakePressed: Boolean? = null,
)
