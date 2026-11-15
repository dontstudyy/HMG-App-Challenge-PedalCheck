package com.dontstudyy.pedalcheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 설계서 4장의 색. 강조색은 청록 하나만, 빨강·초록은 신호와 테두리에만 쓴다. */
object PC {
    val bg = Color(0xFF0E1315)
    val panel = Color(0xFF182023)
    val text = Color(0xFFF3F5F4)
    val sub = Color(0xFFA3AEB0)
    val accent = Color(0xFF43B8AE)
    val red = Color(0xFFE5484D)
    val green = Color(0xFF36B864)
    val wait = Color(0xFF3A4548)
}

const val DISCLAIMER = "자가 점검이며 의료·면허 판단이 아닙니다"

@Composable
fun PedalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PC.bg,
            surface = PC.bg,
            onBackground = PC.text,
            onSurface = PC.text,
            primary = PC.accent,
            onPrimary = Color(0xFF05201D),
            secondary = PC.accent,
        ),
        content = content,
    )
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = true,
) {
    val shape = RoundedCornerShape(20.dp)
    val pad = PaddingValues(horizontal = 36.dp, vertical = 20.dp)
    if (primary) {
        Button(
            onClick = onClick, enabled = enabled, shape = shape, contentPadding = pad,
            modifier = modifier.heightIn(min = 72.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PC.accent, contentColor = Color(0xFF05201D),
                disabledContainerColor = PC.panel, disabledContentColor = PC.sub,
            ),
        ) { Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick, enabled = enabled, shape = shape, contentPadding = pad,
            modifier = modifier.heightIn(min = 72.dp),
        ) { Text(text, fontSize = 26.sp, color = if (enabled) PC.text else PC.sub) }
    }
}

@Composable
fun Disclaimer(modifier: Modifier = Modifier) {
    Text(DISCLAIMER, color = PC.sub, fontSize = 18.sp, modifier = modifier)
}

@Composable
fun PanelRow(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().background(PC.panel, RoundedCornerShape(16.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}
