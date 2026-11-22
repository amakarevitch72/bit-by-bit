package app.bitbybit.ui.circuit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts

/**
 * Test results as a logic analyzer: one waveform lane per input, then for each output a "want" lane (amber)
 * and the measured lane (green, red where it differs). Below, a ✓ / ✗ per test step.
 *
 * [keys] are the steps, like "0 1" (input A first). [got] is null until the test has run.
 */
@Composable
fun LogicAnalyzer(
    inputNames: List<String>,
    outputNames: List<String>,
    keys: List<String>,
    want: (String) -> List<Boolean>,
    got: Map<String, List<Boolean>>?,
    action: @Composable () -> Unit,
) {
    val matches = got?.let { g -> keys.count { g[it] == want(it) } }
    val laneLabel = 64.dp
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF0A0D10))
            .border(1.dp, Bb.BorderSubtle, RoundedCornerShape(14.dp)).padding(12.dp)
            .semantics {
                contentDescription = if (got == null) "Logic analyzer: not run yet"
                else "Logic analyzer: $matches of ${keys.size} steps match"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("LOGIC ANALYZER", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp, color = Bb.TextMuted)
                Text(
                    when {
                        got == null -> "Run the test to record every step"
                        matches == keys.size -> "${keys.size}/${keys.size} steps match"
                        else -> "$matches/${keys.size} steps match"
                    },
                    fontSize = 12.sp,
                    color = when { got == null -> Bb.TextMuted; matches == keys.size -> Bb.High; else -> Bb.ErrorText },
                )
            }
            action()
        }

        @Composable
        fun lane(name: String, values: List<Boolean?>, color: Color, bad: List<Boolean> = emptyList()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.width(laneLabel), fontFamily = BbFonts.Mono, fontSize = 11.sp, color = Bb.TextSecondary, maxLines = 1)
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    val stepW = size.width / values.size
                    val hi = 2.dp.toPx(); val lo = size.height - 2.dp.toPx()
                    values.forEachIndexed { i, v ->
                        val x0 = i * stepW; val x1 = x0 + stepW
                        val c = if (bad.getOrNull(i) == true) Bb.Error else color
                        when (v) {
                            null -> drawLine(Bb.Low, Offset(x0, lo), Offset(x1, lo), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
                            true -> {
                                drawRect(c.copy(alpha = 0.10f), Offset(x0, hi), androidx.compose.ui.geometry.Size(stepW, lo - hi))
                                drawLine(c, Offset(x0, hi), Offset(x1, hi), 2.5.dp.toPx())
                            }
                            false -> drawLine(Bb.Low, Offset(x0, lo), Offset(x1, lo), 2.5.dp.toPx())
                        }
                        val prev = values.getOrNull(i - 1)
                        if (i > 0 && v != null && prev != null && prev != v) drawLine(c, Offset(x0, hi), Offset(x0, lo), 2.dp.toPx())
                    }
                }
            }
        }

        inputNames.forEachIndexed { i, n -> lane(n, keys.map { it.split(" ")[i] == "1" }, Bb.High) }
        outputNames.forEachIndexed { o, n ->
            lane("want $n", keys.map { want(it)[o] }, Bb.Accent)
            val measured = keys.map { got?.get(it)?.getOrNull(o) }
            lane(n, measured, Bb.High, bad = keys.map { k -> got?.get(k)?.getOrNull(o)?.let { it != want(k)[o] } == true })
        }

        Row {
            Spacer(Modifier.width(laneLabel))
            keys.forEach { k ->
                val ok = got?.get(k)?.let { it == want(k) }
                Text(
                    (when (ok) { true -> "✓ "; false -> "✗ "; null -> "" }) + k.replace(" ", ""),
                    Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontFamily = BbFonts.Mono, fontSize = 10.sp,
                    color = when (ok) { true -> Bb.High; false -> Bb.Error; null -> Bb.TextMuted },
                )
            }
        }
    }
}
