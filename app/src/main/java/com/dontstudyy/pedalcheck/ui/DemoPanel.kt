package com.dontstudyy.pedalcheck.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.pedalcheck.AppViewModel
import com.dontstudyy.pedalcheck.gate.Check
import com.dontstudyy.pedalcheck.gate.CheckStatus
import com.dontstudyy.pedalcheck.gate.Gate

private val Skin = Color(0xFFC9B6A2)
private val Shoe = Color(0xFF3A4649)

/**
 * 시연 모드에서 측정 화면 오른쪽에 붙는 패널(설계서 A2): '운전석 발밑' 그림과 차량 신호 체크.
 * 그림은 **Brake 리스너 값**으로, 체크는 **실제 차량 값**으로 그린다. 목업처럼 발이 내려가고 페달이 눌린다.
 * 한 번 내려갔다 올라오는 움직임뿐이고 깜빡임은 없다.
 */
@Composable
fun DemoPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.vehicle.collectAsState()
    val pressed = state.brakePressed == true
    val items = Gate.items(state, vm.gateConfig)

    Column(
        modifier.background(PC.panel, RoundedCornerShape(24.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("운전석 발밑 · 시연 모드", color = PC.sub, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        FootAndPedal(pressed, Modifier.fillMaxWidth().height(250.dp))
        Text(if (pressed) "브레이크를 밟는 중" else "브레이크에서 발을 뗀 상태", color = PC.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("차량 신호", color = PC.sub, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
        items.forEach { item ->
            val label = when (item.check) {
                Check.GEAR_PARK -> "기어 P"
                Check.PARKING_BRAKE -> "주차 브레이크"
                Check.STOPPED -> "정차"
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = if (item.status == CheckStatus.UNKNOWN) PC.sub else PC.text, fontSize = 22.sp)
                Text(
                    when (item.status) {
                        CheckStatus.OK -> "✓"
                        CheckStatus.FAIL -> "✕"
                        CheckStatus.UNKNOWN -> "확인 불가"
                    },
                    color = when (item.status) {
                        CheckStatus.OK -> PC.accent
                        CheckStatus.FAIL -> PC.red
                        CheckStatus.UNKNOWN -> PC.sub
                    },
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 옆에서 본 발과 페달. 밟으면 발이 내려가고 페달이 기울며 페달 색이 청록으로 바뀐다. */
@Composable
private fun FootAndPedal(pressed: Boolean, modifier: Modifier) {
    val p by animateFloatAsState(if (pressed) 1f else 0f, tween(durationMillis = 90), label = "pedal")
    Canvas(modifier) {
        val u = size.minDimension / 250f // 250 기준 좌표
        val cx = size.width / 2f
        val floorY = size.height - 14f * u

        // 바닥
        drawRoundRect(Color(0xFF222C2F), Offset(cx - 150f * u, floorY), Size(300f * u, 14f * u), CornerRadius(7f * u))

        // 페달: 아래쪽 축을 중심으로 앞으로 기운다
        val pedalPivot = Offset(cx + 20f * u, floorY)
        rotate(degrees = 14f * p, pivot = pedalPivot) {
            drawRoundRect(
                color = if (pressed) PC.accent else Color(0xFF5A6A6E),
                topLeft = Offset(cx - 5f * u, floorY - 130f * u),
                size = Size(50f * u, 130f * u),
                cornerRadius = CornerRadius(12f * u),
            )
        }

        // 다리와 발: 발끝이 페달 위에 놓이고, 밟으면 아래로 내려간다
        val drop = 26f * u * p
        val footBottom = floorY - 142f * u + drop
        val ankle = Offset(cx - 70f * u, footBottom - 52f * u)
        val kneeTop = Offset(cx - 96f * u, footBottom - 118f * u) // 패널 제목 아래에 머물도록 짧게
        drawLine(Skin, kneeTop, ankle, strokeWidth = 40f * u, cap = StrokeCap.Round)
        drawRoundRect(
            Shoe, Offset(cx - 98f * u, footBottom - 40f * u), Size(150f * u, 40f * u),
            CornerRadius(20f * u),
        )
        drawRoundRect(Color(0xFF4A585C), Offset(cx - 98f * u, footBottom - 8f * u), Size(150f * u, 8f * u), CornerRadius(4f * u))

        // 누르는 순간 페달 둘레에 번지는 선(한 번 켜져 있다가 뗄 때 사라진다. 깜빡임 없음)
        if (p > 0.01f) {
            drawCircle(
                PC.accent.copy(alpha = 0.5f * p), radius = 58f * u, center = Offset(cx + 20f * u, floorY - 70f * u),
                style = Stroke(width = 4f * u),
            )
        }
    }
}

/** 측정 화면 오른쪽 폭(dp) */
val DEMO_PANEL_WIDTH = 330.dp
