package app.bitbybit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.bitbybit.ui.circuit.BoardTool
import app.bitbybit.ui.circuit.BoardToolBar
import app.bitbybit.ui.circuit.CircuitBoard
import app.bitbybit.ui.circuit.DragGhost
import app.bitbybit.ui.circuit.DragPayload
import app.bitbybit.ui.circuit.rememberDragState
import app.bitbybit.ui.circuit.Editor
import app.bitbybit.ui.circuit.EditorActionBar
import app.bitbybit.ui.circuit.EditorActions
import app.bitbybit.ui.circuit.EditorState
import app.bitbybit.ui.circuit.LogicAnalyzer
import app.bitbybit.ui.circuit.PlacedPart
import app.bitbybit.ui.circuit.SmallAction
import app.bitbybit.ui.palette.ComponentPalette
import app.bitbybit.ui.palette.buildPalette
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// Hard mode: the same level, but the player places and wires the parts themselves, on the circuit-board
// board (ui/circuit/CircuitBoard.kt). Board: 5 × 4 cells. Switch A at A1, switch B at A4, output LEDs on the right,
// and for transistor levels the power input IN (+5V) at C1, above where the transistors go. All of those are fixed.

private const val COLS = 5
private const val ROWS = 4
private const val CELL_A = 0
private const val CELL_B = 15
private const val CELL_POWER = 2

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
    /** Output LEDs, in the order the analyzer reads them (e.g. C then S, like binary "10"). */
    val outputs: List<HardOutput> = listOf(HardOutput("OUT", 14)),
    /** Transistor levels get a fixed power input IN (+5V); gate levels don't need one. */
    val hasPower: Boolean = false,
    val target: (List<Boolean>) -> List<Boolean>,
    /** Shown when a step is wrong. */
    val wrongHint: String,
) {
    val spec = HardSpec(
        inputs = listOf(CELL_A, CELL_B), leds = outputs.map { it.cell }, required = required, target = target,
        power = if (hasPower) CELL_POWER else null,
    )
}

private const val POWER_TIP = " Transistors need power: wire IN (+5V) to a C pin."

val HARD_LEVELS: Map<Int, HardLevel> = listOf(
    HardLevel(
        1, "Build an AND gate", "Place both transistors and wire them so the LED lights only when A and B are both on.$POWER_TIP",
        palette = mapOf("TRANSISTOR" to 2), required = mapOf(PartKind.TRANSISTOR to 2), hasPower = true,
        target = { listOf(it[0] && it[1]) },
        wrongHint = "Follow the current from IN to the LED for each step. Which path is open when only one switch is on?",
    ),
    HardLevel(
        2, "Build an OR gate", "Place both transistors and wire them so the LED lights when A or B (or both) is on.$POWER_TIP",
        palette = mapOf("TRANSISTOR" to 2), required = mapOf(PartKind.TRANSISTOR to 2), hasPower = true,
        target = { listOf(it[0] || it[1]) },
        wrongHint = "Follow the current from IN to the LED for each step. Which path is open when only one switch is on?",
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

/** Cell of the fixed part called [label] right now (the player may have moved it), or [fallback]. */
private fun EditorState.cellOf(label: String, fallback: Int): Int =
    placed.entries.firstOrNull { it.value.fixed && it.value.label == label }?.key ?: fallback

/** The level's spec, pointed at where A, B, IN and the LEDs are now. */
fun HardLevel.specFor(e: EditorState): HardSpec = spec.copy(
    inputs = listOf(e.cellOf("A", CELL_A), e.cellOf("B", CELL_B)),
    leds = outputs.map { e.cellOf(it.label, it.cell) },
    power = if (hasPower) e.cellOf("IN", CELL_POWER) else null,
)

data class HardUiState(
    val editor: EditorState,
    /** Step key ("0 1") → LED values, from the last Run test. Null = not tested since the last change. */
    val results: Map<String, List<Boolean>>? = null,
)

class HardWorkbenchViewModel(private val level: HardLevel) : ViewModel() {
    private val start = EditorState(
        cols = COLS, rows = ROWS,
        placed = buildMap {
            put(CELL_A, PlacedPart("SWITCH", "A", fixed = true))
            put(CELL_B, PlacedPart("SWITCH", "B", fixed = true))
            if (level.hasPower) put(CELL_POWER, PlacedPart("POWER", "IN", fixed = true))
            level.outputs.forEach { put(it.cell, PlacedPart("LED", it.label, fixed = true)) }
        },
    )
    private val _state = MutableStateFlow(HardUiState(Editor.simulate(start)))
    val state: StateFlow<HardUiState> = _state.asStateFlow()

    /** Any change to parts or wires makes old results stale; flipping a switch or changing tool doesn't. */
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
    fun setMode(mode: BoardTool) = edit { Editor.setMode(it, mode) }
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

    /** Drag and drop: a new part lands on [cell]; a moved part goes to [cell], or is removed when dropped off the board. */
    fun drop(p: DragPayload, cell: Int?) = edit { s ->
        when (p) {
            is DragPayload.New -> if (cell == null) s.copy(message = "Drop the part on the board to place it.") else Editor.placeAt(s, cell, p.id, ::canPlace)
            is DragPayload.Existing -> if (cell == null) Editor.dropOff(s, p.fromCell) else Editor.move(s, p.fromCell, cell)
        }
    }

    fun runTest() = _state.update { ui ->
        val e = ui.editor
        val parts = e.placed.mapNotNull { (cell, p) -> p.kind?.let { CircuitPart(cell, it, p.switchOn) } }
        val spec = level.specFor(e)
        val problem = HardMode.problem(spec, parts, e.wires)
        if (problem != null) ui.copy(editor = e.copy(message = problem, focused = null, pending = null), results = null)
        else ui.copy(editor = e.copy(message = null), results = HardMode.run(spec, parts, e.wires))
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

    val keys = level.spec.keys
    val got = ui.results
    val allPass = got != null && keys.all { got[it] == HardMode.want(level.spec, it) }
    val anyWrong = got != null && !allPass
    // Live outputs for the header chip: "OUT 1" or "C 1 · S 0".
    val live = level.outputs.joinToString(" · ") { o -> "${o.label} ${if (s.sim?.input(s.cellOf(o.label, o.cell), 0) == true) 1 else 0}" }
    val drag = rememberDragState()

    // The Box lets the drag ghost float above the whole screen.
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        LevelTopBar("Level ${level.n} · Hard mode", level.title, onBack) {
            Text(
                live, fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Bb.High,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Bb.HighBg).padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = "Live output: $live" },
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(level.goal, style = BbType.Body, modifier = Modifier.padding(horizontal = 16.dp))
            BoardToolBar(s.mode, vm::setMode, Modifier.padding(horizontal = 16.dp))
            CircuitBoard(s, byId, vm::tapCell, Modifier.height(320.dp).padding(horizontal = 16.dp), drag = drag, onDrop = vm::drop)
            EditorActionBar(
                s, vm.actions,
                emptyHint = "Drag a part from below onto the board (long-press, then drag), or pick it and tap a cell.",
                idleHint = "Switches flip when tapped. Long-press a part to drag it; use Wire to connect parts.",
            )
            ComponentPalette(
                items, s.tool, vm::pickTool, showFilters = false, caption = "Parts for this level",
                badge = { item -> "${(level.palette[item.id] ?: 0) - s.count(item.id)} left" },
                drag = drag, onDrop = vm::drop,
            )
            Box(Modifier.padding(horizontal = 16.dp)) {
                LogicAnalyzer(
                    inputNames = listOf("A", "B"),
                    outputNames = level.outputs.map { it.label },
                    keys = keys,
                    want = { HardMode.want(level.spec, it) },
                    got = got,
                    action = { SmallAction("Run test", accent = true, onClick = vm::runTest) },
                )
            }
            if (anyWrong) Text(level.wrongHint, style = BbType.Caption.copy(color = Bb.ErrorText), modifier = Modifier.padding(horizontal = 16.dp))
            if (s.placed.values.any { !it.fixed }) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { SmallAction("Start over", onClick = vm::reset) }
            }
        }
        Box(Modifier.padding(16.dp)) {
            when {
                allPass -> PrimaryButton("Submit circuit", ButtonTone.Success, onClick = onSolved)
                anyWrong -> PrimaryButton("A step is wrong — change the wiring", ButtonTone.Error)
                else -> PrimaryButton("Build it, then tap Run test", ButtonTone.Disabled)
            }
        }
    }
    DragGhost(drag, isFree = { cell -> cell !in s.placed || (drag.payload as? DragPayload.Existing)?.fromCell == cell })
    }
}
