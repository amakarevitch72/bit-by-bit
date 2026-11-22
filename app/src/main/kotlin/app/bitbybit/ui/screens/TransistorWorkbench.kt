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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bitbybit.sim.TransistorPairBoard
import app.bitbybit.sim.Wiring
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.Card
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One workbench for every "two transistors" level. Each level only differs in this config:
 * the target truth table, and whether the wiring is fixed (Level 1) or the player's choice (Level 2).
 */
data class TransistorLevel(
    val n: Int,
    val title: String,
    val goal: String,
    val target: (Boolean, Boolean) -> Boolean,
    /** Set = the wiring row shows this option chosen and locked (an intro level). Null = the player chooses. */
    val fixedWiring: Wiring?,
    /** Shown under the truth table when a row is wrong with this wiring. */
    val wrongHints: Map<Wiring, String> = emptyMap(),
)

val TRANSISTOR_LEVELS: Map<Int, TransistorLevel> = listOf(
    TransistorLevel(
        n = 1, title = "Build an AND gate",
        goal = "Goal: light the LED only when A and B are both on.",
        target = TransistorPairBoard.AND_TARGET,
        fixedWiring = Wiring.SERIES,
    ),
    TransistorLevel(
        n = 2, title = "Build an OR gate",
        goal = "Goal: light the LED when A or B (or both) is on.",
        target = TransistorPairBoard.OR_TARGET,
        fixedWiring = null,
        wrongHints = mapOf(Wiring.SERIES to "Stacked: current needs A and then B. That's AND from Level 1!"),
    ),
).associateBy { it.n }

private val COMBOS = listOf("00", "01", "10", "11")

data class TransistorUiState(
    val level: TransistorLevel,
    val wiring: Wiring? = level.fixedWiring,
    val a: Boolean = false,
    val b: Boolean = false,
    /** Output recorded for each input combination tried with the current wiring. */
    val results: Map<String, Boolean> = emptyMap(),
) {
    val wires get() = TransistorPairBoard(wiring).eval(a, b)
    val key get() = "${if (a) 1 else 0}${if (b) 1 else 0}"

    fun want(k: String) = level.target(k[0] == '1', k[1] == '1')
    fun ok(k: String): Boolean? = results[k]?.let { it == want(k) }

    val passCount get() = COMBOS.count { ok(it) == true }
    val anyWrong get() = COMBOS.any { ok(it) == false }
    val allPass get() = passCount == 4
}

class TransistorWorkbenchViewModel(level: TransistorLevel) : ViewModel() {
    private val _state = MutableStateFlow(TransistorUiState(level).record())
    val state: StateFlow<TransistorUiState> = _state.asStateFlow()

    /** Changing the wiring clears the results: it is a different circuit now. Ignored when the level fixes the wiring. */
    fun choose(w: Wiring) = _state.update {
        if (it.level.fixedWiring != null || it.wiring == w) it else it.copy(wiring = w, results = emptyMap()).record()
    }
    fun toggleA() = _state.update { it.copy(a = !it.a).record() }
    fun toggleB() = _state.update { it.copy(b = !it.b).record() }
}

private fun TransistorUiState.record(): TransistorUiState {
    val out = wires.out ?: return this
    return copy(results = results + (key to out))
}

@Composable
fun TransistorWorkbench(level: TransistorLevel, onBack: () -> Unit, onSolved: () -> Unit) {
    val vm: TransistorWorkbenchViewModel = viewModel(key = "transistor-${level.n}") { TransistorWorkbenchViewModel(level) }
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level ${level.n} · Workbench", level.title, onBack)
        // Scrolls on short phones (the design assumes 844 dp of height; many phones have ~730).
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(level.goal, style = BbType.Body)
            TransistorBoard(s)
            WiringChoice(s.wiring, level.fixedWiring, vm::choose)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InputToggle("Input A", s.a, vm::toggleA, Modifier.weight(1f))
                InputToggle("Input B", s.b, vm::toggleB, Modifier.weight(1f))
                OutputBox(s.wires.out == true, Modifier.weight(1f))
            }
            TruthTable(s)
        }
        Box(Modifier.padding(16.dp)) {
            when {
                s.allPass -> PrimaryButton("Submit circuit", ButtonTone.Success, onClick = onSolved)
                s.wiring == null -> PrimaryButton("Choose how to wire the transistors", ButtonTone.Disabled)
                s.anyWrong -> PrimaryButton("A row is wrong — try the other wiring", ButtonTone.Error)
                else -> PrimaryButton("Flip A and B to test every row", ButtonTone.Disabled)
            }
        }
    }
}

/**
 * Stacked vs Side by side. In a fixed-wiring level the chosen option is shown locked and the other one
 * is dashed with the level that opens it, so the player sees the choice that's coming.
 */
@Composable
private fun WiringChoice(selected: Wiring?, fixed: Wiring?, onChoose: (Wiring) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(Wiring.SERIES, "Stacked", "one after the other"),
            Triple(Wiring.PARALLEL, "Side by side", "two paths"),
        ).forEach { (w, title, sub) ->
            val on = selected == w
            val locked = fixed != null && fixed != w
            val shape = RoundedCornerShape(12.dp)
            Column(
                Modifier.weight(1f).height(52.dp).clip(shape)
                    .background(if (on) Bb.AccentBg else if (locked) Bb.Bg else Bb.Surface)
                    .then(
                        if (locked) Modifier.drawBehind {
                            drawRoundRect(
                                Bb.Border, cornerRadius = CornerRadius(12.dp.toPx()),
                                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))),
                            )
                        } else Modifier.border(2.dp, if (on) Bb.Accent else Bb.Border, shape),
                    )
                    .selectable(selected = on, enabled = fixed == null, role = Role.RadioButton, onClick = { onChoose(w) })
                    .semantics {
                        stateDescription = when {
                            on && fixed != null -> "selected, fixed in this level"
                            locked -> "not available yet"
                            on -> "selected"
                            else -> "not selected"
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(title, style = BbType.Title.copy(fontSize = 15.sp, color = if (on) Bb.Accent else if (locked) Bb.TextLocked else Bb.Text))
                Text(
                    when {
                        on && fixed != null -> "fixed in this level"
                        locked -> "you'll choose in Level 2"
                        else -> sub
                    },
                    style = BbType.Caption,
                )
            }
        }
    }
}

@Composable
private fun TruthTable(s: TransistorUiState) {
    val rows = COMBOS.map { k ->
        WantGotRow("${k[0]} ${k[1]}", want = bitText(s.want(k)), got = s.results[k]?.let(::bitText), now = k == s.key && s.wiring != null)
    }
    val hint = s.wiring?.let { s.level.wrongHints[it] }?.takeIf { s.anyWrong }
    WantGotTable(rows, hint)
}

/** Draws the board for the chosen wiring (or the unwired parts), in a 350×290 board space. */
@Composable
private fun TransistorBoard(s: TransistorUiState) {
    val w = s.wires
    val measurer = rememberTextMeasurer()
    val big = TextStyle(fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Bb.Text)
    val small = TextStyle(fontFamily = BbFonts.Mono, fontSize = 12.sp, color = Bb.TextMuted)
    val hint = TextStyle(fontSize = 13.sp, color = Bb.Accent)
    Canvas(
        Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)).background(Bb.SurfaceSunken)
            .border(1.dp, Bb.BorderSubtle, RoundedCornerShape(18.dp))
            .semantics {
                contentDescription = when (s.wiring) {
                    null -> "Two transistors, not wired yet"
                    Wiring.SERIES -> "Transistors stacked in series. LED ${if (w.out == true) "on" else "off"}"
                    Wiring.PARALLEL -> "Transistors side by side in parallel. LED ${if (w.out == true) "on" else "off"}"
                }
            },
    ) {
        val u = size.height / 290f
        fun p(x: Float, y: Float) = Offset(size.width / 2 - 175 * u + x * u, (y - 4) * u)
        fun label(text: String, x: Float, y: Float, style: TextStyle = big) {
            val m = measurer.measure(text, style)
            drawText(m, topLeft = p(x, y) - Offset(0f, m.size.height / 2f))
        }
        fun led(x: Float, y: Float) {
            val on = w.out == true
            if (on) drawCircle(Bb.HighGlow, 18 * u, p(x, y))
            drawCircle(if (on) Bb.High else Bb.LedOff, 12 * u, p(x, y))
            drawCircle(Bb.PinOutline, 12 * u, p(x, y), style = Stroke(2 * u))
            wire(::p, u, x, y + 14, x, y + 28, false)
            drawLine(Bb.PinOutline, p(x - 16, y + 28), p(x + 16, y + 28), 3 * u)
            drawLine(Bb.PinOutline, p(x - 10, y + 34), p(x + 10, y + 34), 3 * u)
            label("OUT", x + 22, y)
        }

        when (s.wiring) {
            Wiring.SERIES -> {
                wire(::p, u, 200f, 14f, 236f, 14f, true)
                wire(::p, u, 230f, 14f, 230f, 66f, true)
                drawTransistor(::p, u, 230f, 90f, 1f, 56f, s.a, true, w.q1Out)
                wire(::p, u, 230f, 114f, 230f, 166f, w.q1Out)
                drawTransistor(::p, u, 230f, 190f, 1f, 56f, s.b, w.q1Out, w.out)
                wire(::p, u, 230f, 214f, 230f, 238f, w.out)
                led(230f, 252f)
                label("+5V", 244f, 16f, small)
                label("A", 20f, 90f); label("B", 20f, 190f)
                label("Q1", 262f, 90f, small); label("Q2", 262f, 190f, small)
            }
            Wiring.PARALLEL, null -> {
                val wired = s.wiring != null
                // +5V rail feeding both collectors
                wire(::p, u, 150f, 24f, 250f, 24f, if (wired) true else null)
                wire(::p, u, 150f, 24f, 150f, 86f, if (wired) true else null)
                wire(::p, u, 250f, 24f, 250f, 86f, if (wired) true else null)
                drawTransistor(::p, u, 150f, 110f, 1f, 56f, s.a, if (wired) true else null, w.q1Out)
                drawTransistor(::p, u, 250f, 110f, -1f, 326f, s.b, if (wired) true else null, w.q2Out)
                // both emitters join, then go to the LED
                wire(::p, u, 150f, 134f, 150f, 180f, w.q1Out)
                wire(::p, u, 250f, 134f, 250f, 180f, w.q2Out)
                wire(::p, u, 150f, 180f, 250f, 180f, w.out)
                wire(::p, u, 200f, 180f, 200f, 214f, w.out)
                if (wired) led(200f, 228f) else label("Choose a wiring below", 112f, 228f, hint)
                label("+5V", 258f, 24f, small)
                label("A", 20f, 110f); label("B", 318f, 80f)
                label("Q1", 158f, 156f, small); label("Q2", 258f, 156f, small)
            }
        }
    }
}
