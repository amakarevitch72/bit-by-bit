package app.bitbybit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.bitbybit.data.LevelsFile
import app.bitbybit.sim.CircuitPart
import app.bitbybit.sim.HardMode
import app.bitbybit.sim.HardSpec
import app.bitbybit.sim.PartKind
import app.bitbybit.ui.ButtonTone
import app.bitbybit.ui.LevelTopBar
import app.bitbybit.ui.PrimaryButton
import app.bitbybit.ui.circuit.CircuitBoard
import app.bitbybit.ui.circuit.Editor
import app.bitbybit.ui.circuit.EditorActionBar
import app.bitbybit.ui.circuit.EditorActions
import app.bitbybit.ui.circuit.EditorState
import app.bitbybit.ui.circuit.PlacedPart
import app.bitbybit.ui.circuit.SmallAction
import app.bitbybit.ui.palette.ComponentPalette
import app.bitbybit.ui.palette.buildPalette
import app.bitbybit.ui.theme.BbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Hard mode: the same level, but the player places and wires the parts themselves.
// Board: 5 × 4 cells. Switch A sits at A1, switch B at A4; output LEDs on the right; all of those are fixed.

private const val COLS = 5
private const val ROWS = 4
private const val CELL_A = 0
private const val CELL_B = 15

/** A fixed output LED: its label and cell. */
data class HardOutput(val label: String, val cell: Int)

data class HardLevel(
    val n: Int,
    val title: String,
    val goal: String,
    /** Palette id → how many the player may place. */
    val palette: Map<String, Int>,
    /** Parts that must be on the board before testing (e.g. both transistors). Empty = any mix of the palette. */
    val required: Map<PartKind, Int> = emptyMap(),
    /** Output LEDs, in the order the truth table reads them (e.g. C then S, like binary "10"). */
    val outputs: List<HardOutput> = listOf(HardOutput("OUT", 14)),
    val target: (List<Boolean>) -> List<Boolean>,
    /** Hint under the table when a row is wrong. */
    val wrongHint: String,
) {
    val spec = HardSpec(inputs = listOf(CELL_A, CELL_B), leds = outputs.map { it.cell }, required = required, target = target)
    val outputsLabel: String? = if (outputs.size > 1) outputs.joinToString(" ") { it.label } else null
}

private const val C_PIN_TIP = " A transistor's C pin gets +5V unless you wire something into it."

val HARD_LEVELS: Map<Int, HardLevel> = listOf(
    HardLevel(
        1, "Build an AND gate", "Place both transistors and wire them so the LED lights only when A and B are both on.$C_PIN_TIP",
        palette = mapOf("TRANSISTOR" to 2), required = mapOf(PartKind.TRANSISTOR to 2),
        target = { listOf(it[0] && it[1]) },
        wrongHint = "Some rows are wrong. Follow the current from +5V to the LED for each input.",
    ),
    HardLevel(
        2, "Build an OR gate", "Place both transistors and wire them so the LED lights when A or B (or both) is on.$C_PIN_TIP",
        palette = mapOf("TRANSISTOR" to 2), required = mapOf(PartKind.TRANSISTOR to 2),
        target = { listOf(it[0] || it[1]) },
        wrongHint = "Some rows are wrong. Follow the current from +5V to the LED for each input.",
    ),
    // No sockets, no hints about which chips: pick from four gates, one of each, and wire both outputs yourself.
    HardLevel(
        6, "Build a half adder",
        "Add A + B. C must show the carry and S the sum bit: 1 + 1 = 10 in binary. Pick your chips, place them and wire both LEDs.",
        palette = mapOf("XOR" to 1, "AND" to 1, "OR" to 1, "NAND" to 1),
        outputs = listOf(HardOutput("C", 9), HardOutput("S", 14)),
        target = { listOf(it[0] && it[1], it[0] != it[1]) },
        wrongHint = "Check each LED on its own: which chip is 1 only for 1 + 1? Which is 1 when exactly one input is on?",
    ),
).associateBy { it.n }

data class HardUiState(
    val editor: EditorState,
    /** Row key ("0 1") → LED values, from the last Run test. Null = not tested since the last change. */
    val results: Map<String, List<Boolean>>? = null,
)

class HardWorkbenchViewModel(private val level: HardLevel) : ViewModel() {
    private val start = EditorState(
        cols = COLS, rows = ROWS,
        placed = mapOf(
            CELL_A to PlacedPart("SWITCH", "A", fixed = true),
            CELL_B to PlacedPart("SWITCH", "B", fixed = true),
        ) + level.outputs.associate { it.cell to PlacedPart("LED", it.label, fixed = true) },
    )
    private val _state = MutableStateFlow(HardUiState(Editor.simulate(start)))
    val state: StateFlow<HardUiState> = _state.asStateFlow()

    /** Any change to parts or wires makes old results stale; flipping a switch doesn't. */
    private fun edit(block: (EditorState) -> EditorState) = _state.update { ui ->
        val next = Editor.simulate(block(ui.editor))
        val changed = next.wires != ui.editor.wires || next.placed.mapValues { it.value.id } != ui.editor.placed.mapValues { it.value.id }
        ui.copy(editor = next, results = if (changed) null else ui.results)
    }

    private fun canPlace(id: String): String? {
        val allowed = level.palette[id] ?: return "That part isn't used in this level."
        val used = _state.value.editor.count(id)
        val name = if (id == "TRANSISTOR") "transistors" else "$id chip" + if (allowed == 1) "" else "s"
        return if (used >= allowed) "You only have $allowed $name in this level." else null
    }

    fun pickTool(id: String?) = edit { Editor.pickTool(it, id) }
    fun tapCell(cell: Int) = edit { Editor.tapCell(it, cell, ::canPlace) }
    val actions = EditorActions(
        startWire = { pin -> edit { Editor.startWire(it, pin) } },
        chooseTargetPin = { pin -> edit { Editor.chooseTargetPin(it, pin) } },
        cancelWire = { edit(Editor::cancelWire) },
        unwire = { edit(Editor::unwireFocused) },
        remove = { edit(Editor::removeFocused) },
        donePlacing = { edit { Editor.pickTool(it, null) } },
    )
    fun reset() = edit(Editor::clear)

    fun runTest() = _state.update { ui ->
        val e = ui.editor
        val parts = e.placed.mapNotNull { (cell, p) -> p.kind?.let { CircuitPart(cell, it, p.switchOn) } }
        val problem = HardMode.problem(level.spec, parts, e.wires)
        if (problem != null) ui.copy(editor = e.copy(message = problem, focused = null, pending = null), results = null)
        else ui.copy(editor = e.copy(message = null), results = HardMode.run(level.spec, parts, e.wires))
    }
}

@Composable
fun HardWorkbench(level: HardLevel, data: LevelsFile, onBack: () -> Unit, onSolved: () -> Unit) {
    val vm: HardWorkbenchViewModel = viewModel(key = "hard-${level.n}") { HardWorkbenchViewModel(level) }
    val ui by vm.state.collectAsStateWithLifecycle()
    val s = ui.editor
    val all = remember(data) { buildPalette(data, emptySet()) }
    val byId = remember(all) { all.associateBy { it.id } }
    // The level hands you its parts, unlocked or not; a tile goes dashed when you've used them all.
    val items = all.filter { it.id in level.palette }.map { it.copy(available = s.count(it.id) < (level.palette[it.id] ?: 0)) }

    val rows = level.spec.keys.map { k ->
        WantGotRow(k, want = bitsText(HardMode.want(level.spec, k)), got = ui.results?.get(k)?.let(::bitsText))
    }
    val allPass = rows.all { it.ok == true }
    val anyWrong = rows.any { it.ok == false }

    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level ${level.n} · Hard mode", level.title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(level.goal, style = BbType.Body, modifier = Modifier.padding(horizontal = 16.dp))
            CircuitBoard(s, byId, vm::tapCell, Modifier.height(300.dp).padding(horizontal = 16.dp))
            EditorActionBar(
                s, vm.actions,
                emptyHint = "Pick a part below, then tap an empty cell.",
                idleHint = "Tap a part to wire it. Switches A and B flip when tapped.",
            )
            ComponentPalette(
                items, s.tool, vm::pickTool, showFilters = false, caption = "Parts for this level",
                badge = { item -> "${(level.palette[item.id] ?: 0) - s.count(item.id)} left" },
            )
            Box(Modifier.padding(horizontal = 16.dp)) {
                WantGotTable(
                    rows,
                    hint = if (anyWrong) level.wrongHint else null,
                    outputs = level.outputsLabel,
                    action = { SmallAction("Run test", accent = true, onClick = vm::runTest) },
                )
            }
            if (s.placed.values.any { !it.fixed }) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SmallAction("Start over", onClick = vm::reset)
                }
            }
        }
        Box(Modifier.padding(16.dp)) {
            when {
                allPass -> PrimaryButton("Submit circuit", ButtonTone.Success, onClick = onSolved)
                anyWrong -> PrimaryButton("A row is wrong — change the wiring", ButtonTone.Error)
                ui.results == null -> PrimaryButton("Build it, then tap Run test", ButtonTone.Disabled)
                else -> PrimaryButton("Run test to check every row", ButtonTone.Disabled)
            }
        }
    }
}
