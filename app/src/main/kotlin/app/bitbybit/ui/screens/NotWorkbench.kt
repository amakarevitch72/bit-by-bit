package app.bitbybit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bitbybit.sim.InverterBoard
import app.bitbybit.sim.LedSpot
import app.bitbybit.sim.Sig
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Level 3 · NOT from one transistor. Same layout as Levels 1–2: goal → board → choice row → A / Output → want/got table.
// +5V feeds a resistor into a node; the transistor connects that node to ground. The player chooses where the LED goes.

data class NotUiState(
    val spot: LedSpot? = null,
    val a: Boolean = false,
    /** "0"/"1" input → measured output, for the current LED spot. */
    val results: Map<String, Boolean> = emptyMap(),
) {
    val out: Sig get() = InverterBoard(spot).out(a)
    val key get() = bitText(a)
}

class NotWorkbenchViewModel : ViewModel() {
    private val _state = MutableStateFlow(NotUiState())
    val state: StateFlow<NotUiState> = _state.asStateFlow()

    fun choose(spot: LedSpot) = _state.update { if (it.spot == spot) it else it.copy(spot = spot, results = emptyMap()).record() }
    fun toggle() = _state.update { it.copy(a = !it.a).record() }

    private fun NotUiState.record(): NotUiState = out?.let { copy(results = results + (key to it)) } ?: this
}

@Composable
fun NotWorkbench(onBack: () -> Unit, onSolved: () -> Unit, vm: NotWorkbenchViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val rows = listOf("0", "1").map { k -> WantGotRow(k, want = bitText(k == "0"), got = s.results[k]?.let(::bitText), now = k == s.key && s.spot != null) }
    val allPass = rows.all { it.ok == true }
    val anyWrong = rows.any { it.ok == false }

    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level 3 · Workbench", "Build a NOT gate", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Goal: the LED must be ON when A is off, and OFF when A is on.", style = BbType.Body)
            NotBoard(s)
            LedChoice(s.spot, vm::choose)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InputToggle("Input A", s.a, vm::toggle, Modifier.weight(1f))
                OutputBox(s.out == true, Modifier.weight(1f))
            }
            WantGotTable(rows, hint = if (anyWrong && s.spot == LedSpot.EMITTER) "After the emitter, the LED just copies A. Where would the transistor take current away from it?" else null)
        }
        Box(Modifier.padding(16.dp)) {
            when {
                allPass -> PrimaryButton("Submit circuit", ButtonTone.Success, onClick = onSolved)
                s.spot == null -> PrimaryButton("Choose where the LED goes", ButtonTone.Disabled)
                anyWrong -> PrimaryButton("A row is wrong — move the LED", ButtonTone.Error)
                else -> PrimaryButton("Flip A to test both rows", ButtonTone.Disabled)
            }
        }
    }
}

@Composable
private fun LedChoice(selected: LedSpot?, onChoose: (LedSpot) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(LedSpot.EMITTER, "After the emitter", "below the transistor"),
            Triple(LedSpot.COLLECTOR, "Above the transistor", "on the node it can drain"),
        ).forEach { (spot, title, sub) ->
            val on = selected == spot
            Column(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (on) Bb.AccentBg else Bb.Surface)
                    .border(2.dp, if (on) Bb.Accent else Bb.Border, RoundedCornerShape(12.dp))
                    .selectable(selected = on, role = Role.RadioButton) { onChoose(spot) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(title, style = BbType.Title.copy(fontSize = 14.sp, color = if (on) Bb.Accent else Bb.Text))
                Text(sub, style = BbType.Caption)
            }
        }
    }
}

/** +5V → resistor → node → transistor (base A) → ground, with the LED in the chosen spot. 350×290 board space. */
@Composable
private fun NotBoard(s: NotUiState) {
    val measurer = rememberTextMeasurer()
    val big = TextStyle(fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Bb.Text)
    val small = TextStyle(fontFamily = BbFonts.Mono, fontSize = 11.sp, color = Bb.TextMuted)
    val hint = TextStyle(fontSize = 13.sp, color = Bb.Accent)
    val a = s.a
    val placed = s.spot != null
    // Which wires carry current: in the collector layout the node is high only while the transistor is off.
    val node: Sig = when (s.spot) { LedSpot.COLLECTOR -> !a; LedSpot.EMITTER -> true; null -> null }
    val emitter: Sig = if (placed) a else null
    Canvas(
        Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(18.dp)).background(Bb.SurfaceSunken)
            .border(1.dp, Bb.BorderSubtle, RoundedCornerShape(18.dp))
            .semantics {
                contentDescription = when (s.spot) {
                    null -> "One transistor between a resistor and ground; the LED is not placed yet"
                    LedSpot.EMITTER -> "LED after the emitter. LED ${if (s.out == true) "on" else "off"}"
                    LedSpot.COLLECTOR -> "LED on the node above the transistor. LED ${if (s.out == true) "on" else "off"}"
                }
            },
    ) {
        val u = size.height / 290f
        fun p(x: Float, y: Float) = Offset(size.width / 2 - 175 * u + x * u, y * u)
        fun label(t: String, x: Float, y: Float, st: TextStyle = big) {
            val m = measurer.measure(t, st); drawText(m, topLeft = p(x, y) - Offset(0f, m.size.height / 2f))
        }
        fun ground(x: Float, y: Float) {
            drawLine(Bb.PinOutline, p(x - 14, y), p(x + 14, y), 3 * u)
            drawLine(Bb.PinOutline, p(x - 9, y + 6), p(x + 9, y + 6), 3 * u)
            drawLine(Bb.PinOutline, p(x - 4, y + 12), p(x + 4, y + 12), 3 * u)
        }
        fun led(x: Float, y: Float) {
            val on = s.out == true
            if (on) drawCircle(Bb.HighGlow, 17 * u, p(x, y))
            drawCircle(if (on) Bb.High else Bb.LedOff, 11 * u, p(x, y))
            drawCircle(Bb.PinOutline, 11 * u, p(x, y), style = Stroke(2 * u))
            label("OUT", x + 18, y)
        }
        // +5V and resistor (zigzag) down to the node
        wire(::p, u, 200f, 14f, 236f, 14f, true); label("+5V", 244f, 16f, small)
        wire(::p, u, 230f, 14f, 230f, 30f, true)
        val zig = Path().apply {
            moveTo(p(230f, 30f).x, p(230f, 30f).y)
            listOf(36f to 222f, 44f to 238f, 52f to 222f, 60f to 238f, 68f to 222f, 76f to 238f, 82f to 230f).forEach { (y, x) -> lineTo(p(x, y).x, p(x, y).y) }
        }
        drawPath(zig, Bb.Accent, style = Stroke(2.5f * u))
        label("R", 246f, 56f, small)
        wire(::p, u, 230f, 82f, 230f, 100f, node)
        drawCircle(if (node == true) Bb.High else Bb.Low, 4 * u, p(230f, 100f))
        // Transistor: collector from the node, base A, emitter down
        drawTransistor(::p, u, 230f, 140f, 1f, 70f, a, node, emitter)
        label("A", 34f, 140f)
        wire(::p, u, 230f, 100f, 230f, 116f, node)
        when (s.spot) {
            LedSpot.COLLECTOR -> {
                wire(::p, u, 230f, 100f, 300f, 100f, node); wire(::p, u, 300f, 100f, 300f, 124f, node)
                led(300f, 138f); wire(::p, u, 300f, 152f, 300f, 196f, false); ground(300f, 196f)
                wire(::p, u, 230f, 164f, 230f, 220f, emitter); ground(230f, 220f)
            }
            LedSpot.EMITTER -> {
                wire(::p, u, 230f, 164f, 230f, 190f, emitter); led(230f, 204f)
                wire(::p, u, 230f, 218f, 230f, 236f, false); ground(230f, 236f)
            }
            null -> {
                wire(::p, u, 230f, 100f, 300f, 100f, null); wire(::p, u, 230f, 164f, 230f, 220f, null); ground(230f, 220f)
                label("Choose where the LED goes", 70f, 264f, hint)
            }
        }
    }
}
