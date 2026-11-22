package app.bitbybit.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
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
import app.bitbybit.data.LevelsFile
import app.bitbybit.sim.ChipId
import app.bitbybit.sim.HalfAdderBoard
import app.bitbybit.sim.Sig
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.palette.ComponentPalette
import app.bitbybit.ui.palette.buildPalette
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Level 6 · Normal (design/HalfAdder.dc.html): two sockets — one drives S (sum), one drives C (carry).
// Tap a chip in the tray, then a socket. Tap a filled socket to empty it. Same layout as the other workbenches.

private val HA_TRAY = listOf(ChipId.XOR, ChipId.AND, ChipId.OR, ChipId.NAND)
private val HA_KEYS = listOf("00", "01", "10", "11")

data class HalfAdderUiState(
    val sum: ChipId? = ChipId.XOR, // starts with XOR placed and Carry empty, as in the design
    val carry: ChipId? = null,
    val selected: ChipId? = null,
    val a: Boolean = false,
    val b: Boolean = false,
    /** Input key → "C S" bits measured with the current chips. */
    val results: Map<String, String> = emptyMap(),
) {
    val board get() = HalfAdderBoard(sum, carry)
    val outs: Pair<Sig, Sig> get() = board.eval(a, b) // (S, C)
    val key get() = "${bitText(a)}${bitText(b)}"
    fun want(k: String): String {
        val x = k[0] == '1'; val y = k[1] == '1'
        return bitText(x && y) + bitText(x != y)
    }
}

class HalfAdderViewModel : ViewModel() {
    private val _state = MutableStateFlow(HalfAdderUiState().record())
    val state: StateFlow<HalfAdderUiState> = _state.asStateFlow()

    fun pick(id: String?) = _state.update { s -> s.copy(selected = id?.let { ChipId.valueOf(it) }?.takeIf { it != s.selected }) }

    /** With a chip picked: put it in the socket. Without: empty the socket. Either way it's a new circuit, so results reset. */
    fun tapSocket(isSum: Boolean) = _state.update { s ->
        val chip = s.selected
        val next = when {
            chip != null -> if (isSum) s.copy(sum = chip) else s.copy(carry = chip)
            isSum && s.sum != null -> s.copy(sum = null)
            !isSum && s.carry != null -> s.copy(carry = null)
            else -> return@update s
        }
        next.copy(selected = null, results = emptyMap()).record()
    }

    fun toggleA() = _state.update { it.copy(a = !it.a).record() }
    fun toggleB() = _state.update { it.copy(b = !it.b).record() }
}

private fun HalfAdderUiState.record(): HalfAdderUiState {
    val (s, c) = outs
    if (s == null || c == null) return this
    return copy(results = results + (key to bitText(c) + bitText(s)))
}

@Composable
fun HalfAdderWorkbench(data: LevelsFile, onBack: () -> Unit, onSolved: () -> Unit, vm: HalfAdderViewModel = viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    // The level hands you these four chips, so they're always available here.
    val palette = remember(data) {
        buildPalette(data, emptySet()).filter { p -> HA_TRAY.any { it.name == p.id } }.map { it.copy(available = true) }
    }
    val rows = HA_KEYS.map { k ->
        WantGotRow("${k[0]} ${k[1]}", want = s.want(k), got = s.results[k], now = k == s.key && s.sum != null && s.carry != null)
    }
    val ready = s.sum != null && s.carry != null
    val allPass = rows.all { it.ok == true }
    val anyWrong = rows.any { it.ok == false }

    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level 6 · Workbench", "Build a half adder", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Goal: add two bits. S is the sum bit, C the carry: 1 + 1 = 10 in binary.",
                style = BbType.Body, modifier = Modifier.padding(horizontal = 16.dp),
            )
            HalfAdderBoardView(s, vm::tapSocket, Modifier.padding(horizontal = 16.dp))
            ComponentPalette(
                palette, s.selected?.name, vm::pick, showFilters = false,
                caption = if (s.selected != null) "Now tap a socket" else "Tap a chip, then a socket",
            )
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InputToggle("Input A", s.a, vm::toggleA, Modifier.weight(1f))
                InputToggle("Input B", s.b, vm::toggleB, Modifier.weight(1f))
                ResultBox(s, Modifier.weight(1f))
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                WantGotTable(
                    rows, outputs = "C S",
                    hint = if (anyWrong) "Which chip is 1 only for 1 + 1? Which is 1 when exactly one input is on?" else null,
                )
            }
        }
        Box(Modifier.padding(16.dp)) {
            when {
                allPass -> PrimaryButton("Submit circuit", ButtonTone.Success, onClick = onSolved)
                !ready -> PrimaryButton("Fill both sockets to power the board", ButtonTone.Disabled)
                anyWrong -> PrimaryButton("A row is wrong — swap a chip", ButtonTone.Error)
                else -> PrimaryButton("Flip A and B to test every row", ButtonTone.Disabled)
            }
        }
    }
}

@Composable
private fun ResultBox(s: HalfAdderUiState, modifier: Modifier) {
    val (sum, carry) = s.outs
    val text = if (sum == null || carry == null) "? ?" else "${bitText(carry)} ${bitText(sum)}"
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(14.dp)).background(Bb.SurfaceSunken)
            .border(2.dp, Bb.Border, RoundedCornerShape(14.dp))
            .semantics { contentDescription = "Result C S: $text" },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("C S", style = BbType.Caption)
        Text(text, fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Bb.Text)
    }
}

/**
 * The board in the design's 358×240 space, scaled to the width. Wires and LEDs are drawn; the two sockets
 * are real buttons placed on top. Unpowered wires (an empty socket) are drawn dashed.
 */
@Composable
private fun HalfAdderBoardView(s: HalfAdderUiState, onSocket: (Boolean) -> Unit, modifier: Modifier) {
    val (sum, carry) = s.outs
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Bb.Text)
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(358f / 240f).clip(RoundedCornerShape(18.dp)).background(Bb.SurfaceSunken)
            .border(1.dp, Bb.BorderSubtle, RoundedCornerShape(18.dp)),
    ) {
        val unit = maxWidth / 358f
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 358f
            fun p(x: Float, y: Float) = Offset(x * u, y * u)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float, v: Sig) = wire(::p, u, x1, y1, x2, y2, v)
            fun text(t: String, x: Float, y: Float) {
                val m = measurer.measure(t, label); drawText(m, topLeft = p(x, y) - Offset(0f, m.size.height / 2f))
            }
            fun led(y: Float, v: Sig, name: String) {
                if (v == true) drawCircle(Bb.HighGlow, 16 * u, p(312f, y))
                drawCircle(if (v == true) Bb.High else Bb.LedOff, 10 * u, p(312f, y))
                drawCircle(Bb.PinOutline, 10 * u, p(312f, y), style = Stroke(2 * u))
                text(name, 306f, y + 34)
            }
            // A feeds both sockets' first input; B feeds both second inputs.
            text("A", 10f, 70f); drawCircle(if (s.a) Bb.High else Bb.Low, 8 * u, p(34f, 70f))
            line(42f, 70f, 90f, 70f, s.a); line(90f, 48f, 90f, 168f, s.a); line(90f, 48f, 150f, 48f, s.a); line(90f, 168f, 150f, 168f, s.a)
            text("B", 10f, 192f); drawCircle(if (s.b) Bb.High else Bb.Low, 8 * u, p(34f, 192f))
            line(42f, 192f, 150f, 192f, s.b); line(114f, 72f, 114f, 192f, s.b); line(114f, 72f, 150f, 72f, s.b)
            // Outputs
            line(230f, 60f, 296f, 60f, sum); led(60f, sum, "S")
            line(230f, 180f, 296f, 180f, carry); led(180f, carry, "C")
        }
        Socket("Sum socket", s.sum, s.selected != null, unit * 150f, unit * 28f, unit * 80f, unit * 64f) { onSocket(true) }
        Socket("Carry socket", s.carry, s.selected != null, unit * 150f, unit * 148f, unit * 80f, unit * 64f) { onSocket(false) }
    }
}

@Composable
private fun Socket(
    name: String, chip: ChipId?, armed: Boolean,
    x: androidx.compose.ui.unit.Dp, y: androidx.compose.ui.unit.Dp,
    w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.offset(x, y).size(w, h).clip(shape)
            .background(if (chip != null) Bb.Surface else if (armed) Bb.AccentBg else Bb.SurfaceSunken)
            .then(
                if (chip != null) Modifier.border(2.dp, Bb.High, shape)
                else Modifier.drawBehind { // empty socket = dashed outline
                    drawRoundRect(
                        if (armed) Bb.Accent else Bb.PinOutline, cornerRadius = CornerRadius(10.dp.toPx()),
                        style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                    )
                },
            )
            .clickable(role = Role.Button, onClickLabel = if (chip != null && !armed) "Remove chip" else "Place chip") { onTap() }
            .semantics { contentDescription = if (chip != null) "$name: ${chip.name}" else "Empty $name" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            chip?.symbol ?: "+", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 20.sp,
            color = if (chip != null) Bb.Text else if (armed) Bb.Accent else Bb.TextLocked,
        )
    }
}
