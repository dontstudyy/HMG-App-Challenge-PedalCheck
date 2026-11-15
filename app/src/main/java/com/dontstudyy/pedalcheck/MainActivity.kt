package com.dontstudyy.pedalcheck

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.dontstudyy.pedalcheck.ui.App

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    // CAR_SPEED는 설치 시 -g로 주거나, 아니면 여기서 요청한다. 허용되면 차량 연결을 다시 한다.
    private val speedPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.restartMonitor()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(vm) }
        handleTarget(intent)
        if (checkSelfPermission(CAR_SPEED) != PackageManager.PERMISSION_GRANTED) {
            speedPermission.launch(CAR_SPEED)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTarget(intent)
    }

    /** `GleoActivity`가 넘긴 이동 요청(PEDAL_CHECK_START, MY_PEDAL_TREND). 한 번 처리하면 지워 다시 처리되지 않게 한다. */
    private fun handleTarget(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: return
        intent.removeExtra(EXTRA_TARGET)
        vm.routeTo(target)
    }

    /**
     * 스페이스바 = 브레이크 페달. 화면 포커스와 상관없이 항상 받는다(`tools/key_bridge.py` 없이 동작).
     * 시각은 이벤트를 받은 첫 줄에서 찍는다.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_SPACE) {
            val t = System.nanoTime()
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) vm.keyDown(t)
                KeyEvent.ACTION_UP -> vm.keyUp(t)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        vm.releaseKeys() // 눌러 둔 브레이크를 0으로
        super.onStop()
    }

    companion object {
        private const val CAR_SPEED = "android.car.permission.CAR_SPEED"
        const val EXTRA_TARGET = "pedalcheck.target"
        const val TARGET_CONDITIONS = "conditions"
        const val TARGET_TREND = "trend"
    }
}
