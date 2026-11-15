package com.dontstudyy.pedalcheck.gate

import com.dontstudyy.pedalcheck.engine.AbortReason
import com.dontstudyy.pedalcheck.vehicle.GearPosition
import com.dontstudyy.pedalcheck.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GateTest {
    private val parked = VehicleState(
        connected = true, gear = GearPosition.PARK, speedMps = 0f, uxRestricted = false, parkingBrake = null,
    )
    private val cfg = GateConfig(parkingBrakeUsable = false)

    @Test fun 기어P_정차면_시작_가능_주차브레이크는_확인불가로_표시() {
        val r = Gate.evaluateStart(parked, cfg) as GateResult.Ready
        assertEquals(CheckStatus.UNKNOWN, r.items.first { it.check == Check.PARKING_BRAKE }.status)
    }

    @Test fun 기어가_P가_아니면_시작_불가() {
        val r = Gate.evaluateStart(parked.copy(gear = GearPosition.NEUTRAL), cfg)
        assertTrue(r is GateResult.NotReady)
    }

    @Test fun 속도가_0_3_넘으면_정차가_아님() {
        assertTrue(Gate.evaluateStart(parked.copy(speedMps = 0.31f), cfg) is GateResult.NotReady)
        assertTrue(Gate.evaluateStart(parked.copy(speedMps = 0.3f), cfg) is GateResult.Ready)
    }

    @Test fun 주행_규제가_켜져_있으면_정차가_아님() {
        assertTrue(Gate.evaluateStart(parked.copy(uxRestricted = true), cfg) is GateResult.NotReady)
    }

    @Test fun 속도를_못_읽으면_시작_불가() {
        assertTrue(Gate.evaluateStart(parked.copy(speedMps = null), cfg) is GateResult.NotReady)
    }

    @Test fun 연결이_안_되면_시작_불가() {
        assertTrue(Gate.evaluateStart(parked.copy(connected = false), cfg) is GateResult.NotReady)
    }

    @Test fun 주차브레이크를_쓰는_설정이면_꺼짐은_시작_불가_켜짐은_통과() {
        val c = GateConfig(parkingBrakeUsable = true)
        assertTrue(Gate.evaluateStart(parked.copy(parkingBrake = false), c) is GateResult.NotReady)
        assertTrue(Gate.evaluateStart(parked.copy(parkingBrake = true), c) is GateResult.Ready)
        // 값을 못 읽으면 막지 않는다
        assertTrue(Gate.evaluateStart(parked.copy(parkingBrake = null), c) is GateResult.Ready)
    }

    @Test fun 측정_중_기어가_D로_바뀌면_즉시_중단() {
        val r = Gate.evaluateRunning(parked.copy(gear = GearPosition.DRIVE), cfg) as GateResult.Abort
        assertEquals(AbortReason.GEAR_CHANGED, r.reason)
    }

    @Test fun 측정_중_주행_규제가_켜지면_중단() {
        val r = Gate.evaluateRunning(parked.copy(uxRestricted = true), cfg) as GateResult.Abort
        assertEquals(AbortReason.DRIVING_RESTRICTION, r.reason)
    }

    @Test fun 측정_중_주차브레이크_해제는_쓰는_설정일_때만_중단() {
        assertTrue(Gate.evaluateRunning(parked.copy(parkingBrake = false), cfg) is GateResult.Ready)
        val r = Gate.evaluateRunning(parked.copy(parkingBrake = false), GateConfig(parkingBrakeUsable = true)) as GateResult.Abort
        assertEquals(AbortReason.PARKING_BRAKE_RELEASED, r.reason)
    }

    @Test fun 측정_중_속도_정보가_잠깐_없어도_중단하지_않는다() {
        assertTrue(Gate.evaluateRunning(parked.copy(speedMps = null), cfg) is GateResult.Ready)
    }

    @Test fun 주행_화면_조건() {
        assertFalse(Gate.isDriving(parked))
        assertTrue(Gate.isDriving(parked.copy(gear = GearPosition.DRIVE)))
        assertTrue(Gate.isDriving(parked.copy(uxRestricted = true)))
        assertFalse(Gate.isDriving(parked.copy(gear = GearPosition.NEUTRAL)))
    }
}
