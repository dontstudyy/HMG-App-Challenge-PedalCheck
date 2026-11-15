package com.dontstudyy.pedalcheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.pedalcheck.AppViewModel
import com.dontstudyy.pedalcheck.baseline.BaselineBuilder
import com.dontstudyy.pedalcheck.baseline.BaselineStatus
import com.dontstudyy.pedalcheck.baseline.Format
import com.dontstudyy.pedalcheck.baseline.SessionPoint
import com.dontstudyy.pedalcheck.data.SessionEntity
import com.dontstudyy.pedalcheck.engine.AbortReason
import com.dontstudyy.pedalcheck.gate.Check
import com.dontstudyy.pedalcheck.gate.CheckStatus
import com.dontstudyy.pedalcheck.gate.Gate
import com.dontstudyy.pedalcheck.gate.GateResult
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ======================= 1. 홈 =======================

@Composable
fun HomeScreen(vm: AppViewModel) {
    val sessions by vm.repo.sessions.collectAsState(initial = emptyList())
    val baseline by vm.repo.baseline.collectAsState(initial = null)
    val monthly by vm.repo.monthly.collectAsState(initial = emptyList())
    val noticeShown by vm.repo.noticeShownMonth.collectAsState()
    val now = System.currentTimeMillis()
    val today = sessions.firstOrNull { sameDay(it.startedAtMillis, now) }

    Row(Modifier.fillMaxSize().padding(48.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("페달 체크", color = PC.text, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            Text("화면 신호에 맞춰 브레이크를 밟아 30초 만에 반응속도를 재요", color = PC.sub, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))

            PanelRow {
                Text("오늘", color = PC.sub, fontSize = 24.sp)
                Text(
                    today?.let { "${Format.seconds(it.meanMs)}초" } ?: "아직 안 쟀어요",
                    color = PC.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                )
            }
            PanelRow {
                Text("기준선", color = PC.sub, fontSize = 24.sp)
                val b = baseline
                if (b != null) {
                    Text(
                        "${Format.seconds(b.valueMs)}초" + if (b.method == "DEMO") " (시연용)" else "",
                        color = PC.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    )
                } else {
                    val st = BaselineBuilder.status(sessions.map { SessionPoint(it.startedAtMillis, it.meanMs) }, now)
                    val txt = when (st) {
                        is BaselineStatus.Building ->
                            "만드는 중 ${minOf(st.count, BaselineBuilder.NEEDED)}/${BaselineBuilder.NEEDED}" +
                                if (st.daysLeft > 0) " · ${st.daysLeft}일 남음" else ""
                        is BaselineStatus.Ready -> "${Format.seconds(st.baselineMs)}초"
                    }
                    Text(txt, color = PC.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            // 출발 전 제안: 장거리 길안내를 시작했고 오늘 측정이 없을 때
            vm.suggestion?.let { sg ->
                Column(
                    Modifier.fillMaxWidth().background(PC.accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("${sg.destination.ifBlank { "목적지" }}까지 ${sg.distanceKm}km 길안내를 시작했어요", color = PC.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("출발 전에 30초 체크할까요?", color = PC.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BigButton("체크하기", onClick = vm::openConditions)
                        BigButton("나중에", onClick = vm::dismissSuggestion, primary = false)
                    }
                }
            }
            // 3개월 안내: 조건을 만족하고 이번 달에 아직 안 보여 준 경우에만 (본인 화면에만 뜬다)
            if (noticeAvailable(monthly, noticeShown, now)) {
                Row(
                    Modifier.fillMaxWidth().background(PC.accent.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                        .clickable(onClick = vm::openNotice).padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("최근 석 달 반응 추이를 알려 드려요", color = PC.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("보기 ›", color = PC.accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                BigButton("30초 체크", onClick = vm::openConditions)
                BigButton("추이", onClick = vm::openTrend, primary = false)
                BigButton("설정", onClick = vm::openSettings, primary = false)
            }
            Disclaimer()
        }

        Column(
            Modifier.weight(1f).fillMaxHeight().background(PC.panel, RoundedCornerShape(24.dp)).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("최근 결과", color = PC.sub, fontSize = 24.sp)
            if (sessions.isEmpty()) {
                Text("아직 기록이 없어요", color = PC.sub, fontSize = 26.sp, modifier = Modifier.padding(top = 12.dp))
            }
            sessions.take(7).forEach { RecentRow(it) }
        }
    }
}

@Composable
private fun RecentRow(s: SessionEntity) {
    val date = remember(s.startedAtMillis) {
        SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(s.startedAtMillis))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(date + if (s.demo) "  시연용" else "", color = PC.sub, fontSize = 24.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("${Format.seconds(s.meanMs)}초", color = PC.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            s.percentVsBaseline?.let { p ->
                val t = when {
                    p > 0 -> "+$p%"
                    p < 0 -> "$p%"
                    else -> "0%"
                }
                Text(t, color = PC.sub, fontSize = 24.sp, modifier = Modifier.width(80.dp))
            }
        }
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

// ======================= 2. 실행 조건 =======================

@Composable
fun ConditionsScreen(vm: AppViewModel) {
    val state by vm.vehicle.collectAsState()
    val gate = Gate.evaluateStart(state, vm.gateConfig)
    val ready = gate is GateResult.Ready

    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Box(
            Modifier.background(PC.panel, RoundedCornerShape(28.dp)).padding(horizontal = 36.dp, vertical = 24.dp),
        ) { Text("오늘 30초 체크할까요?", color = PC.text, fontSize = 44.sp, fontWeight = FontWeight.Bold) }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            gate.items.forEach { item ->
                val (label, mark, color) = when (item.check) {
                    Check.GEAR_PARK -> Triple("기어가 P예요", markOf(item.status), markColorOf(item.status))
                    Check.PARKING_BRAKE -> Triple(
                        if (item.status == CheckStatus.UNKNOWN) "주차 브레이크 (이 환경에서는 확인할 수 없어요)" else "주차 브레이크가 걸려 있어요",
                        markOf(item.status), markColorOf(item.status),
                    )
                    Check.STOPPED -> Triple("차가 멈춰 있어요", markOf(item.status), markColorOf(item.status))
                }
                PanelRow {
                    Text(label, color = if (item.status == CheckStatus.UNKNOWN) PC.sub else PC.text, fontSize = 28.sp)
                    Text(mark, color = color, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!state.connected) {
            Text("차량 신호를 받을 수 없어요" + (state.error?.let { " ($it)" } ?: ""), color = PC.red, fontSize = 22.sp)
        } else if (!ready) {
            Text("차를 세우고 기어를 P로 놓아 주세요", color = PC.sub, fontSize = 26.sp)
        }

        Text("빨개지면 밟고, 초록이면 참으세요", color = PC.accent, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("시작하기 전에 브레이크에서 발을 떼 주세요", color = PC.sub, fontSize = 22.sp)

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("시작", onClick = vm::startMeasure, enabled = ready)
            BigButton("뒤로", onClick = vm::goHome, primary = false)
        }
    }
}

private fun markOf(s: CheckStatus) = when (s) {
    CheckStatus.OK -> "✓"
    CheckStatus.FAIL -> "✕"
    CheckStatus.UNKNOWN -> "–"
}

private fun markColorOf(s: CheckStatus) = when (s) {
    CheckStatus.OK -> PC.accent
    CheckStatus.FAIL -> PC.red
    CheckStatus.UNKNOWN -> PC.sub
}

// ======================= 6. 측정 중단 / 주행 중 =======================

@Composable
fun AbortedScreen(reason: AbortReason, vm: AppViewModel) {
    val (title, body) = when (reason) {
        AbortReason.GEAR_CHANGED -> "측정을 멈췄어요" to "기어가 P에서 바뀌었어요"
        AbortReason.PARKING_BRAKE_RELEASED -> "측정을 멈췄어요" to "주차 브레이크가 풀렸어요"
        AbortReason.DRIVING_RESTRICTION -> "측정을 멈췄어요" to "주행 규제가 켜졌어요"
        AbortReason.TOO_MANY_FAULTS -> "천천히 다시 해 볼까요?" to "너무 일찍 밟거나 놓친 횟수가 많았어요"
        AbortReason.CANCELED -> "측정을 멈췄어요" to "직접 멈췄어요"
    }
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = PC.text, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text(body, color = PC.sub, fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text("기록하지 않았어요", color = PC.sub, fontSize = 28.sp)
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (reason == AbortReason.TOO_MANY_FAULTS) BigButton("다시 하기", onClick = vm::openConditions)
            BigButton("홈으로", onClick = vm::goHome, primary = reason != AbortReason.TOO_MANY_FAULTS)
        }
    }
}

@Composable
fun DrivingScreen() {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("주행 중에는 쓸 수 없어요", color = PC.text, fontSize = 64.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text("차를 세우고 기어를 P로 놓으면 다시 쓸 수 있어요", color = PC.sub, fontSize = 32.sp)
    }
}

// ======================= 10. 설정 =======================

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val demo by vm.repo.demoMode.collectAsState()
    val consent by vm.repo.familyConsent.collectAsState()
    val name by vm.repo.userName.collectAsState()
    val baseline by vm.repo.baseline.collectAsState(initial = null)
    var message by remember { mutableStateOf("") }
    var ms by remember { mutableStateOf("600") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("설정", color = PC.text, fontSize = 48.sp, fontWeight = FontWeight.Bold)
        PanelRow {
            Text("가족 공유 동의 (미리보기)", color = PC.text, fontSize = 28.sp)
            Switch(checked = consent, onCheckedChange = vm.repo::setFamilyConsent)
        }
        PanelRow {
            Text("이름 (가족 화면에 쓰여요)", color = PC.text, fontSize = 28.sp)
            OutlinedTextField(
                value = name, onValueChange = { vm.repo.setUserName(it.take(10)) },
                singleLine = true, modifier = Modifier.width(260.dp),
            )
        }
        val suggestKm by vm.repo.suggestKm.collectAsState()
        PanelRow {
            Text("출발 전 제안 거리 (km 이상 길안내일 때)", color = PC.text, fontSize = 28.sp)
            OutlinedTextField(
                value = suggestKm.toString(), onValueChange = { t -> t.filter(Char::isDigit).toIntOrNull()?.let(vm.repo::setSuggestKm) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.width(160.dp),
            )
        }
        PanelRow {
            Text("시연 모드", color = PC.text, fontSize = 28.sp)
            Switch(checked = demo, onCheckedChange = vm::setDemoMode)
        }
        if (demo) {
            Text("시연 도구 — 기준선은 원래 처음 14일 기록으로 만들어요", color = PC.sub, fontSize = 22.sp)
            Text(
                baseline?.let { "지금 기준선: ${Format.seconds(it.valueMs)}초 (${if (it.method == "DEMO") "시연용" else "처음 14일"})" }
                    ?: "지금 기준선: 아직 없음",
                color = PC.text, fontSize = 26.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BigButton("시연용 기록 넣기", primary = false, onClick = {
                    vm.insertDemoHistory { n -> message = "시연용 기록 ${n}건을 넣고 기준선을 맞췄어요" }
                })
                BigButton("시연용 기록 지우기", primary = false, onClick = { vm.clearDemoHistory(); message = "시연용 기록을 지웠어요" })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ms, onValueChange = { ms = it.filter(Char::isDigit).take(4) },
                    label = { Text("기준선(ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true, modifier = Modifier.width(220.dp),
                )
                BigButton("기준선으로 지정", primary = false, onClick = {
                    val v = ms.toIntOrNull()
                    if (v != null && v in 200..2000) { vm.setDemoBaseline(v); message = "기준선을 ${v}ms로 정했어요" }
                    else message = "200~2000ms 사이로 넣어 주세요"
                })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("기준선 다시 만들기", primary = false, onClick = {
                    vm.rebuildBaselineNow { ok -> message = if (ok) "지금 기록으로 기준선을 다시 만들었어요" else "기록이 5개 이상 있어야 해요" }
                })
                BigButton("기준선 지우기", primary = false, onClick = { vm.clearBaseline(); message = "기준선을 지웠어요" })
                BigButton("기록 모두 지우기", primary = false, onClick = { vm.clearAll(); message = "기록과 기준선을 모두 지웠어요" })
            }
        }
        if (message.isNotEmpty()) Text(message, color = PC.accent, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        BigButton("뒤로", onClick = vm::goHome)
    }
}
