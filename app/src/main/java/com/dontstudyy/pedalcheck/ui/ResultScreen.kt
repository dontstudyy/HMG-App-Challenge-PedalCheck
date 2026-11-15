package com.dontstudyy.pedalcheck.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.pedalcheck.AppViewModel
import com.dontstudyy.pedalcheck.baseline.BaselineBuilder
import com.dontstudyy.pedalcheck.baseline.Direction
import com.dontstudyy.pedalcheck.baseline.Format
import com.dontstudyy.pedalcheck.data.BaselineView
import com.dontstudyy.pedalcheck.data.ResultUi
import com.dontstudyy.pedalcheck.engine.SignalKind
import com.dontstudyy.pedalcheck.engine.Verdict

/**
 * 4번(오늘 결과)·5번(느린 날 결과). 합격/불합격 표현 없이 나 자신과의 비교 문구만 쓴다.
 * 기준선이 아직 없으면 오늘 평균만 보여 주고 비교 문구는 없다.
 */
@Composable
fun ResultScreen(ui: ResultUi, vm: AppViewModel) {
    Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("오늘의 반응속도", color = PC.sub, fontSize = 28.sp)
            Text("${Format.seconds(ui.meanMs)}초", color = PC.text, fontSize = 120.sp, fontWeight = FontWeight.Bold)

            when (val b = ui.baseline) {
                is BaselineView.Building -> {
                    Text("기준선 만드는 중 ${minOf(b.count, BaselineBuilder.NEEDED)}/${BaselineBuilder.NEEDED}", color = PC.accent, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "기준선이 만들어지면 평소의 나와 비교해 드려요" + if (b.daysLeft > 0) " (${b.daysLeft}일 뒤)" else "",
                        color = PC.sub, fontSize = 24.sp,
                    )
                }
                is BaselineView.Ready -> {
                    val c = b.comparison
                    val line = when (c.direction) {
                        Direction.SLOWER -> "평소보다 ${c.percent}% 느려요"
                        Direction.FASTER -> "평소보다 ${c.percent}% 빨라요"
                        Direction.SAME -> "평소와 같아요"
                    } + if (c.withinUsual && c.direction != Direction.SAME) " · 평소 범위예요" else ""
                    Text(line, color = PC.accent, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text("평소 ${Format.seconds(b.baselineMs)}초" + if (b.method == "DEMO") " (시연용 기준)" else "", color = PC.sub, fontSize = 26.sp)

                    if (c.suggestBreak) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "시속 60km라면 ${"%.1f".format(c.extraMeters)}m 더 가서 멈추는 셈이에요",
                            color = PC.text, fontSize = 30.sp,
                        )
                        Text("가까운 곳만 다녀오시거나 쉬었다 가시는 건 어떨까요?", color = PC.text, fontSize = 28.sp)
                        Spacer(Modifier.height(8.dp))
                        CompareBars(baselineMs = b.baselineMs, todayMs = ui.meanMs)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("홈으로", onClick = vm::goHome)
                BigButton("다시 재기", onClick = vm::openConditions, primary = false)
            }
            Disclaimer()
        }

        Column(
            Modifier.weight(0.9f).fillMaxHeight().background(PC.panel, RoundedCornerShape(24.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("이번 회차", color = PC.sub, fontSize = 24.sp)
            ui.trials.filter { it.kind == SignalKind.RED && it.verdict == Verdict.NORMAL }.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${i + 1}회", color = PC.sub, fontSize = 26.sp)
                    Text(
                        "${Format.seconds(t.reactionMs ?: 0)}초" + if (t.excludedFromMean) "  (평균 제외)" else "",
                        color = if (t.excludedFromMean) PC.sub else PC.text, fontSize = 28.sp,
                    )
                }
            }
            val green = ui.trials.lastOrNull { it.kind == SignalKind.GREEN && (it.verdict == Verdict.HELD || it.verdict == Verdict.WRONG_PRESS) }
            if (green != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("초록", color = PC.sub, fontSize = 26.sp)
                    Text(if (green.verdict == Verdict.HELD) "참았어요" else "밟았어요", color = PC.text, fontSize = 28.sp)
                }
            }
            val retries = ui.trials.count { it.verdict == Verdict.FALSE_START || it.verdict == Verdict.TOO_FAST || it.verdict == Verdict.MISSED }
            if (retries > 0) Text("다시 한 회차 ${retries}번", color = PC.sub, fontSize = 22.sp)
        }
    }
}

/** 평소/오늘 비교 막대(5번 화면). */
@Composable
private fun CompareBars(baselineMs: Int, todayMs: Int) {
    val max = maxOf(baselineMs, todayMs).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("평소" to baselineMs, "오늘" to todayMs).forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = PC.sub, fontSize = 24.sp, modifier = Modifier.width(72.dp))
                Box(Modifier.weight(1f)) {
                    Box(
                        Modifier.fillMaxWidth(v / max).height(28.dp)
                            .background(if (label == "평소") PC.sub else PC.accent, RoundedCornerShape(8.dp)),
                    )
                }
                Text("${Format.seconds(v)}초", color = PC.text, fontSize = 24.sp, modifier = Modifier.padding(start = 12.dp).width(96.dp))
            }
        }
    }
}
