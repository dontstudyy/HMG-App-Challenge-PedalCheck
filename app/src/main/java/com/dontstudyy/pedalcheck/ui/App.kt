package com.dontstudyy.pedalcheck.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import com.dontstudyy.pedalcheck.AppViewModel
import com.dontstudyy.pedalcheck.Screen

/** 화면 폭이 이 값(dp)이 되도록 밀도를 맞춘다. 차량 화면은 해상도·밀도가 제각각이라 한 기준으로 설계한다. */
private const val DESIGN_WIDTH_DP = 1280f

@Composable
fun App(vm: AppViewModel) {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val base = LocalDensity.current
    val density = if (widthPx > 0) Density(widthPx / DESIGN_WIDTH_DP, fontScale = 1f) else base

    CompositionLocalProvider(LocalDensity provides density) {
        PedalTheme {
            Surface(Modifier.fillMaxSize(), color = PC.bg) {
                val screen = vm.screen
                // 주행 중이면 어느 화면에서든 숨김 화면. 측정 중이던 경우만 6번(중단) 화면이 먼저 보인다.
                if (vm.driving && screen !is Screen.Aborted) {
                    DrivingScreen()
                } else {
                    when (screen) {
                        Screen.Home -> HomeScreen(vm)
                        Screen.Conditions -> ConditionsScreen(vm)
                        Screen.Measure -> MeasureScreen(vm)
                        Screen.Settings -> SettingsScreen(vm)
                        Screen.Trend -> TrendScreen(vm)
                        Screen.Family -> FamilyScreen(vm)
                        Screen.Notice -> NoticeScreen(vm)
                        is Screen.Result -> ResultScreen(screen.ui, vm)
                        is Screen.Aborted -> AbortedScreen(screen.reason, vm)
                    }
                }
            }
        }
    }
}
