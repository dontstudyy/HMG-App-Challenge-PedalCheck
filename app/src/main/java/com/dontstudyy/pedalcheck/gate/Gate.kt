package com.dontstudyy.pedalcheck.gate

import com.dontstudyy.pedalcheck.engine.AbortReason
import com.dontstudyy.pedalcheck.vehicle.GearPosition
import com.dontstudyy.pedalcheck.vehicle.VehicleState

enum class Check { GEAR_PARK, PARKING_BRAKE, STOPPED }

enum class CheckStatus {
    OK,
    FAIL,
    /** 이 환경에서는 읽을 수 없어 확인하지 못함. 시작을 막지 않고, 확인한 척 표시하지도 않는다. */
    UNKNOWN,
}

data class CheckItem(val check: Check, val status: CheckStatus)

sealed interface GateResult {
    val items: List<CheckItem>

    data class Ready(override val items: List<CheckItem>) : GateResult
    data class NotReady(override val items: List<CheckItem>) : GateResult
    data class Abort(val reason: AbortReason, override val items: List<CheckItem> = emptyList()) : GateResult
}

/**
 * 실행 조건 판정. 설정은 단계 0 실험 결과로 정한다.
 * @param parkingBrakeUsable 주차 브레이크 값을 믿고 조건에 쓸 수 있는가(아니면 "기어 P, 정차" 2개만 쓴다)
 */
data class GateConfig(
    val parkingBrakeUsable: Boolean = false,
    /** 이 속도(m/s) 이하면 정차. 0.3m/s ≈ 1km/h (설계서 B3) */
    val stoppedMaxMps: Float = 0.3f,
)

object Gate {

    fun items(s: VehicleState, cfg: GateConfig): List<CheckItem> {
        val gear = if (s.gear == GearPosition.PARK) CheckStatus.OK else CheckStatus.FAIL
        val brake = when {
            !cfg.parkingBrakeUsable || s.parkingBrake == null -> CheckStatus.UNKNOWN
            s.parkingBrake -> CheckStatus.OK
            else -> CheckStatus.FAIL
        }
        val speedOk = s.speedMps != null && s.speedMps <= cfg.stoppedMaxMps
        val stopped = if (speedOk && s.uxRestricted == false) CheckStatus.OK else CheckStatus.FAIL
        return listOf(
            CheckItem(Check.GEAR_PARK, gear),
            CheckItem(Check.PARKING_BRAKE, brake),
            CheckItem(Check.STOPPED, stopped),
        )
    }

    /** 시작 버튼을 켤 수 있는가. */
    fun evaluateStart(s: VehicleState, cfg: GateConfig): GateResult {
        val items = items(s, cfg)
        val ok = s.connected && items.none { it.status == CheckStatus.FAIL }
        return if (ok) GateResult.Ready(items) else GateResult.NotReady(items)
    }

    /** 측정 중 조건이 깨졌는가. 깨졌으면 즉시 중단(기록하지 않음). */
    fun evaluateRunning(s: VehicleState, cfg: GateConfig): GateResult {
        val items = items(s, cfg)
        return when {
            s.gear != GearPosition.UNKNOWN && s.gear != GearPosition.PARK -> GateResult.Abort(AbortReason.GEAR_CHANGED, items)
            s.uxRestricted == true -> GateResult.Abort(AbortReason.DRIVING_RESTRICTION, items)
            cfg.parkingBrakeUsable && s.parkingBrake == false -> GateResult.Abort(AbortReason.PARKING_BRAKE_RELEASED, items)
            else -> GateResult.Ready(items)
        }
    }

    /** "주행 중에는 쓸 수 없어요" 화면을 띄울 상태인가(어느 화면에서든). */
    fun isDriving(s: VehicleState): Boolean =
        s.uxRestricted == true || s.gear == GearPosition.DRIVE || s.gear == GearPosition.REVERSE
}
