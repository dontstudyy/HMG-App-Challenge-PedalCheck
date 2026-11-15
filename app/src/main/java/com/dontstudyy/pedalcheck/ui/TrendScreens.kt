package com.dontstudyy.pedalcheck.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.pedalcheck.AppViewModel
import com.dontstudyy.pedalcheck.baseline.Format
import com.dontstudyy.pedalcheck.baseline.MonthValue
import com.dontstudyy.pedalcheck.baseline.Trend
import com.dontstudyy.pedalcheck.baseline.YearMonth
import com.dontstudyy.pedalcheck.data.SessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun monthLabel(yearMonth: Int) = "${yearMonth % 100}월"

// ======================= 차트 =======================

private class ChartColors(val line: Color, val text: Color, val sub: Color, val grid: Color, val bg: Color)

private val DarkChart = ChartColors(line = PC.accent, text = PC.text, sub = PC.sub, grid = Color(0xFF2A3336), bg = PC.panel)
private val LightChart = ChartColors(
    line = Color(0xFF2E8F87), text = Color(0xFF1B2224), sub = Color(0xFF5E6A6D), grid = Color(0xFFD9D6CF), bg = Color(0xFFF4F2EE),
)

/**
 * 월별 중앙값 그래프. 세션이 3개 미만인 달은 점선·빈 점으로 그린다(값이 덜 믿을 만하다는 뜻).
 * 값 눈금은 두 달 차이가 보이도록 잡았으므로 숫자를 함께 적는다.
 */
@Composable
private fun MonthChart(months: List<MonthValue>, baselineMs: Int?, light: Boolean, modifier: Modifier = Modifier) {
    val c = if (light) LightChart else DarkChart
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (months.isEmpty()) return@Canvas
        val padX = 48.dp.toPx()
        val top = 44.dp.toPx()
        val bottom = 42.dp.toPx()
        val h = size.height - top - bottom
        val w = size.width - 2 * padX

        val vals = months.map { it.medianMs } + listOfNotNull(baselineMs)
        val lo = vals.min().toFloat()
        val hi = vals.max().toFloat()
        val span = maxOf(hi - lo, 40f)
        val mid = (lo + hi) / 2f
        val yMin = mid - span * 0.9f
        val yMax = mid + span * 0.9f
        fun y(v: Float) = top + (yMax - v) / (yMax - yMin) * h
        fun x(i: Int) = if (months.size == 1) padX + w / 2f else padX + i * (w / (months.size - 1))

        // 가로 눈금선
        for (k in 0..2) {
            val gy = top + h * k / 2f
            drawLine(c.grid, Offset(padX - 16.dp.toPx(), gy), Offset(size.width - padX + 16.dp.toPx(), gy), strokeWidth = 2.dp.toPx())
        }
        // 기준선(점선)
        if (baselineMs != null) {
            val by = y(baselineMs.toFloat())
            drawLine(
                c.sub, Offset(padX - 16.dp.toPx(), by), Offset(size.width - padX + 16.dp.toPx(), by),
                strokeWidth = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 10.dp.toPx())),
            )
            val t = measurer.measure("기준선 ${Format.seconds(baselineMs)}", TextStyle(color = c.sub, fontSize = 18.sp))
            drawText(t, topLeft = Offset(size.width - padX + 16.dp.toPx() - t.size.width, by - t.size.height - 2.dp.toPx()))
        }
        // 선: 점선인 달이 끼면 그 구간은 점선
        for (i in 0 until months.size - 1) {
            val dashed = months[i].dashed || months[i + 1].dashed
            drawLine(
                c.line, Offset(x(i), y(months[i].medianMs.toFloat())), Offset(x(i + 1), y(months[i + 1].medianMs.toFloat())),
                strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 12.dp.toPx())) else null,
            )
        }
        // 점, 값, 월
        months.forEachIndexed { i, m ->
            val cx = x(i)
            val cy = y(m.medianMs.toFloat())
            if (m.dashed) {
                drawCircle(c.bg, radius = 10.dp.toPx(), center = Offset(cx, cy))
                drawCircle(c.line, radius = 10.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 4.dp.toPx()))
            } else {
                drawCircle(c.line, radius = 10.dp.toPx(), center = Offset(cx, cy))
            }
            val vt = measurer.measure(Format.seconds(m.medianMs), TextStyle(color = c.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold))
            drawText(vt, topLeft = Offset(cx - vt.size.width / 2f, cy - vt.size.height - 12.dp.toPx()))
            val mt = measurer.measure(monthLabel(m.yearMonth), TextStyle(color = c.sub, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            drawText(mt, topLeft = Offset(cx - mt.size.width / 2f, size.height - bottom + 10.dp.toPx()))
        }
    }
}

// ======================= 7. 추이 =======================

@Composable
fun TrendScreen(vm: AppViewModel) {
    val monthly by vm.repo.monthly.collectAsState(initial = emptyList())
    val sessions by vm.repo.sessions.collectAsState(initial = emptyList())
    val baseline by vm.repo.baseline.collectAsState(initial = null)
    val shown = monthly.takeLast(6)

    Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.weight(1.25f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("월별 추이", color = PC.text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            Text("월 평균 반응시간 · 낮을수록 빨라요", color = PC.sub, fontSize = 24.sp)
            Box(
                Modifier.fillMaxWidth().weight(1f).background(PC.panel, RoundedCornerShape(24.dp)).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (shown.size < 2) {
                    Text(
                        if (shown.isEmpty()) "기록이 쌓이면 월별 추이가 보여요" else "다음 달부터 추이가 보여요",
                        color = PC.sub, fontSize = 28.sp,
                    )
                } else {
                    MonthChart(shown, baseline?.valueMs, light = false, modifier = Modifier.fillMaxSize())
                }
            }
            if (shown.any { it.dashed }) Text("점선·빈 점은 세션이 3개 미만인 달이에요", color = PC.sub, fontSize = 20.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("가족과 나누기", onClick = vm::openFamily)
                BigButton("홈으로", onClick = vm::goHome, primary = false)
            }
            Disclaimer()
        }

        Column(
            Modifier.weight(0.75f).fillMaxHeight().background(PC.panel, RoundedCornerShape(24.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("날짜별 기록 · 나만 볼 수 있어요", color = PC.sub, fontSize = 22.sp)
            if (sessions.isEmpty()) Text("아직 기록이 없어요", color = PC.sub, fontSize = 24.sp)
            sessions.take(9).forEach { DateRow(it) }
        }
    }
}

@Composable
private fun DateRow(s: SessionEntity) {
    val date = remember(s.startedAtMillis) { SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(s.startedAtMillis)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(date + if (s.demo) "  시연용" else "", color = PC.sub, fontSize = 22.sp)
        Text("${Format.seconds(s.meanMs)}초", color = PC.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

// ======================= 8. 가족 공유 미리보기 =======================

/** 공유 항목은 월별 추이 그래프 하나뿐이다. 날마다의 점수·측정 시각과 장소는 보내지 않는다. 실제 전송은 없다(미리보기). */
@Composable
fun FamilyScreen(vm: AppViewModel) {
    val consent by vm.repo.familyConsent.collectAsState()
    val name by vm.repo.userName.collectAsState()
    val monthly by vm.repo.monthly.collectAsState(initial = emptyList())
    val baseline by vm.repo.baseline.collectAsState(initial = null)
    val nowMonth = YearMonth.of(System.currentTimeMillis())

    Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("가족과 나누기", color = PC.text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            PanelRow {
                Column {
                    Text("딸에게 공유", color = PC.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (consent) "본인이 동의함" else "동의하지 않음",
                        color = if (consent) PC.accent else PC.sub, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Switch(checked = consent, onCheckedChange = vm.repo::setFamilyConsent)
            }
            ShareRow("월별 추이 그래프", send = true)
            ShareRow("날마다의 점수", send = false)
            ShareRow("측정 시각과 장소", send = false)
            Text("이 화면은 미리보기예요. 실제로는 전송되지 않아요.", color = PC.sub, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BigButton("추이로", onClick = vm::openTrend)
                BigButton("홈으로", onClick = vm::goHome, primary = false)
            }
            Disclaimer()
        }

        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("딸의 휴대폰", color = PC.sub, fontSize = 24.sp)
            Spacer(Modifier.height(12.dp))
            PhonePreview(
                title = "$name ${nowMonth % 100}월\n운전 컨디션",
                months = if (consent) monthly.takeLast(4) else emptyList(),
                baselineMs = null,
                consent = consent,
            )
        }
    }
}

@Composable
private fun ShareRow(label: String, send: Boolean) {
    PanelRow {
        Text(label, color = PC.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (send) "✓ 보내요" else "보내지 않아요",
            color = if (send) PC.accent else PC.sub, fontSize = 26.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PhonePreview(title: String, months: List<MonthValue>, baselineMs: Int?, consent: Boolean) {
    val ink = Color(0xFF1B2224)
    val soft = Color(0xFF5E6A6D)
    Column(
        Modifier.width(360.dp).height(540.dp)
            .background(Color(0xFF2A3336), RoundedCornerShape(44.dp)).padding(8.dp)
            .background(Color(0xFFF4F2EE), RoundedCornerShape(38.dp)).padding(horizontal = 26.dp, vertical = 22.dp),
    ) {
        Text("페달 체크 · 가족 공유", color = soft, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(title, color = ink, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
        Text("월 평균 반응시간 · 낮을수록 빨라요", color = soft, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (!consent) {
                Text("공유가 꺼져 있어요", color = soft, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            } else if (months.size < 2) {
                Text("기록이 쌓이면\n그래프가 보여요", color = soft, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            } else {
                MonthChart(months, baselineMs, light = true, modifier = Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxWidth().background(Color(0xFFE6E2DA), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("날마다의 점수는 공유되지 않아요", color = Color(0xFF3E484A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            DISCLAIMER, color = Color(0xFF6B777A), fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

// ======================= 9. 3개월 안내 =======================

/**
 * 본인에게만 보이는 안내(가족 미리보기에는 넣지 않는다). 같은 달에는 다시 띄우지 않는다.
 * `운전능력진단 안내 보기`의 실제 안내 문구·링크는 받은 것이 없어 지어내지 않고 "[2차 개발 범위로 현재 미구현]"만 보여 준다.
 */
@Composable
fun NoticeScreen(vm: AppViewModel) {
    val monthly by vm.repo.monthly.collectAsState(initial = emptyList())
    val last3 = monthly.takeLast(3)
    var guideRequested by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(Modifier.background(PC.accent.copy(alpha = 0.18f), RoundedCornerShape(999.dp)).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("본인에게 먼저 알려요", color = PC.accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "최근 석 달 반응이 조금씩 늦어지고 있어요.\n운전능력진단을 받아 보시겠어요?",
            color = PC.text, fontSize = 44.sp, fontWeight = FontWeight.Black, lineHeight = 62.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            last3.forEach { m ->
                Row(
                    Modifier.background(PC.panel, RoundedCornerShape(18.dp)).padding(horizontal = 28.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(monthLabel(m.yearMonth), color = PC.sub, fontSize = 28.sp)
                    Text("${Format.seconds(m.medianMs)}초", color = PC.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("운전능력진단 안내 보기", onClick = { guideRequested = true })
            BigButton("나중에", onClick = vm::goHome, primary = false)
        }
        if (guideRequested) {
            Box(Modifier.background(PC.panel, RoundedCornerShape(18.dp)).padding(horizontal = 28.dp, vertical = 18.dp)) {
                Text("[2차 개발 범위로 현재 미구현]", color = PC.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f))
        Disclaimer()
    }
}

/** 홈에 띄우는 3개월 안내 카드가 보일 조건: 조건을 만족하고, 이번 달에 아직 보여 주지 않았을 때. */
fun noticeAvailable(monthly: List<MonthValue>, shownMonth: Int, nowMillis: Long): Boolean {
    val ym = YearMonth.of(nowMillis)
    return shownMonth != ym && Trend.notice(monthly, ym)
}
