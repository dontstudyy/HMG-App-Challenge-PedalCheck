package com.dontstudyy.pedalcheck.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.withFrameNanos
import com.dontstudyy.pedalcheck.AppViewModel
import com.dontstudyy.pedalcheck.baseline.Format
import com.dontstudyy.pedalcheck.engine.EngineState
import com.dontstudyy.pedalcheck.engine.SignalKind
import com.dontstudyy.pedalcheck.engine.TrialRecord
import com.dontstudyy.pedalcheck.engine.Verdict

/**
 * 3번 측정 화면. 깜빡임 없음: 원은 한 번에 색이 바뀌어 유지되고, 판정 테두리는 한 번 켜졌다 서서히 꺼진다.
 *
 * 프레임 루프가 핵심이다. 엔진은 프레임 시각으로 신호를 켜고, 상태 변경이 같은 프레임 안에서 그려진다
 * (Compose는 withFrameNanos 콜백에서 바뀐 상태를 그 프레임의 리컴포지션에 반영한다).
 */
@Composable
fun MeasureScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frame -> vm.onFrame(frame) }
        }
    }

    val st = vm.engineState
    val attempts = vm.attempts
    val normalDone = attempts.count { it.verdict == Verdict.NORMAL }

    // 회차 표시. 초록 회차는 신호가 켜진 뒤에만 "참기 회차"로 바꾼다(기다리는 동안 초록이 올지 알려 주지 않는다).
    val revealedKind = when (st) {
        is EngineState.Signal -> st.kind
        is EngineState.Feedback -> st.kind
        else -> SignalKind.RED
    }
    val header = when {
        revealedKind == SignalKind.GREEN -> "참기 회차"
        st is EngineState.Finished -> "5 / 5"
        else -> "${minOf(normalDone + 1, 5)} / 5"
    }

    val circleColor = when {
        st is EngineState.Signal && st.kind == SignalKind.RED -> PC.red
        st is EngineState.Signal && st.kind == SignalKind.GREEN -> PC.green
        else -> PC.wait
    }
    val guidance = when (st) {
        is EngineState.WaitRelease -> "발을 떼 주세요"
        is EngineState.Waiting -> "기다리세요"
        is EngineState.Signal -> if (st.kind == SignalKind.RED) "밟으세요!" else "밟지 마세요"
        is EngineState.Feedback -> feedbackText(st.verdict)
        is EngineState.Finished -> "결과를 계산하고 있어요"
        else -> ""
    }
    val circleText = (st as? EngineState.Feedback)?.takeIf { it.verdict == Verdict.NORMAL }?.reactionMs?.let { Format.seconds(it) }

    // 판정 연출(목업 영상과 같은 모양): 제대로 밟음·참기 성공은 초록, 틀린 입력은 빨강 + 약한 흔들림.
    // 시간(초)을 하나의 애니메이션으로 돌리고 그리기 단계에서만 읽어, 프레임마다 리컴포지션하지 않는다.
    val fx = remember { Animatable(FeedbackFx.DURATION_SEC) }
    var fxKind by remember { mutableStateOf(FxKind.NONE) }
    var fxRun by remember { mutableIntStateOf(0) }
    val feedbackKey = (st as? EngineState.Feedback)?.let { it.slot to attempts.size }
    val feedbackVerdict = (st as? EngineState.Feedback)?.verdict
    // 새 판정이 나오면 종류를 정하고 연출을 한 번 시작한다.
    LaunchedEffect(feedbackKey) {
        val kind = FeedbackFx.kindOf(feedbackVerdict)
        if (feedbackKey != null && kind != FxKind.NONE) {
            fxKind = kind
            fxRun++
        }
    }
    // 애니메이션은 판정 상태와 분리해 돌린다(상태가 바뀌어도 취소되지 않고 끝까지 간다).
    LaunchedEffect(fxRun) {
        if (fxRun > 0) {
            fx.snapTo(0f)
            fx.animateTo(FeedbackFx.DURATION_SEC, tween(durationMillis = (FeedbackFx.DURATION_SEC * 1000).toInt(), easing = LinearEasing))
        }
    }
    // 다음 스텝이 시작되면(판정 화면이 끝나면) 연출은 항상 기본 상태로 돌아간다.
    val fxNow = if (st is EngineState.Feedback) fxKind else FxKind.NONE

    Box(
        Modifier.fillMaxSize().graphicsLayer {
            translationX = FeedbackFx.shake(fxNow, fx.value, 12.dp.toPx()) // 약한 화면 흔들림
        },
    ) {
    val demoMode by vm.repo.demoMode.collectAsState()
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
    Column(Modifier.weight(1f).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(header, color = PC.text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            if (vm.keysDirect) {
                Text("직접 입력 중 · 차량 신호가 아니에요", color = PC.sub, fontSize = 22.sp)
            }
            BigButton("그만하기", onClick = vm::cancelMeasure, primary = false)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 원 둘레 링(제대로=초록, 틀림=빨강) + 틀렸을 때 원이 살짝 떨리고 빨간 진동선이 퍼진다
                Box(
                    Modifier.size(368.dp)
                        .graphicsLayer { translationX = FeedbackFx.shake(fxNow, fx.value, 6.dp.toPx(), freq = 17f, decay = 5f) }
                        .drawBehind {
                            val a = FeedbackFx.glow(fxNow, fx.value)
                            if (a > 0.001f) {
                                val w = 9.dp.toPx()
                                drawCircle(
                                    color = fxColor(fxNow).copy(alpha = a),
                                    radius = size.minDimension / 2f - w / 2f,
                                    style = Stroke(width = w),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    VibrationMarks(fx = { FeedbackFx.vibration(fxNow, fx.value) })
                    Box(Modifier.size(340.dp).clip(CircleShape).background(circleColor), contentAlignment = Alignment.Center) {
                        if (circleText != null) {
                            Text(circleText, color = PC.text, fontSize = 96.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(guidance, color = PC.text, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            }
        }

        TrialStrip(attempts)
        Spacer(Modifier.height(12.dp))
        Disclaimer()
    }
    if (demoMode) {
        Spacer(Modifier.width(28.dp))
        DemoPanel(vm, Modifier.width(DEMO_PANEL_WIDTH).fillMaxHeight())
    }
    }

    // 화면 가장자리 테두리와 부드러운 글로우. 한 번 켜졌다 서서히 꺼진다(깜빡임 없음).
    Box(
        Modifier.fillMaxSize().drawBehind {
            val a = FeedbackFx.glow(fxNow, fx.value)
            if (a > 0.001f) {
                val c = fxColor(fxNow)
                val glowStrength = if (fxNow == FxKind.BAD) 0.28f else 0.22f
                drawRect(
                    brush = Brush.radialGradient(
                        0.55f to Color.Transparent,
                        1f to c.copy(alpha = glowStrength * a),
                        center = center,
                        radius = kotlin.math.hypot(size.width, size.height) / 2f,
                    ),
                )
                val w = 9.dp.toPx()
                inset(w / 2f) { drawRect(color = c.copy(alpha = a), style = Stroke(width = w)) }
            }
        },
    )
    }
}

private fun fxColor(kind: FxKind): Color = if (kind == FxKind.BAD) PC.red else PC.green

/** 틀린 입력일 때 원 양옆으로 퍼지는 빨간 진동선(목업의 `marks`). 크기는 원(340dp) 기준. */
@Composable
private fun VibrationMarks(fx: () -> Float) {
    Canvas(Modifier.requiredSize(560.dp, 360.dp)) {
        val a = fx()
        if (a <= 0.001f) return@Canvas
        val u = 1.dp.toPx()
        val stroke = Stroke(width = 8f * u, cap = StrokeCap.Round)
        fun arc(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float) = Path().apply {
            moveTo(x0 * u, y0 * u)
            quadraticTo(cx * u, cy * u, x1 * u, y1 * u)
        }
        val color = PC.red.copy(alpha = a)
        drawPath(arc(60f, 110f, 30f, 180f, 60f, 250f), color, style = stroke)
        drawPath(arc(28f, 80f, -8f, 180f, 28f, 280f), color, style = stroke)
        drawPath(arc(500f, 110f, 530f, 180f, 500f, 250f), color, style = stroke)
        drawPath(arc(532f, 80f, 568f, 180f, 532f, 280f), color, style = stroke)
    }
}

private fun feedbackText(v: Verdict) = when (v) {
    Verdict.NORMAL -> "기록했어요"
    Verdict.FALSE_START -> "조금 일렀어요, 다시 할게요"
    Verdict.TOO_FAST -> "조금 일렀어요, 다시 할게요"
    Verdict.MISSED -> "괜찮아요, 다시 할게요"
    Verdict.HELD -> "잘 참으셨어요"
    Verdict.WRONG_PRESS -> "초록에서는 밟지 않아요"
}

/** 아래 띠: 6개 회차의 결과(빨강은 반응시간 막대, 초록은 참기 결과). */
@Composable
private fun TrialStrip(attempts: List<TrialRecord>) {
    val done = attempts.filter {
        it.verdict == Verdict.NORMAL || it.verdict == Verdict.HELD || it.verdict == Verdict.WRONG_PRESS
    }
    Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        // 회차 순서가 아니라 끝난 순서대로 채운다(초록 위치를 미리 알려 주지 않기 위해)
        repeat(6) { i ->
            val t = done.getOrNull(i)
            Box(
                Modifier.weight(1f).height(88.dp).background(PC.panel, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (t != null) {
                    if (t.kind == SignalKind.RED) {
                        val h = ((t.reactionMs ?: 0) / 1000f).coerceIn(0.1f, 1f)
                        Box(Modifier.fillMaxWidth().height((88 * h).dp).padding(horizontal = 4.dp).background(PC.accent, RoundedCornerShape(10.dp)))
                        Text(Format.seconds(t.reactionMs ?: 0), color = PC.text, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp))
                    } else {
                        Text(if (t.verdict == Verdict.HELD) "참음" else "밟음", color = PC.text, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
}
