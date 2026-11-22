package app.bitbybit.ui.circuit

import app.bitbybit.sim.Circuit
import app.bitbybit.sim.CircuitPart
import app.bitbybit.sim.CircuitWire
import app.bitbybit.sim.PartKind

// Shared circuit editor: used by the Sandbox (free build) and by Hard mode workbenches.
// State + pure edit functions live here (unit-tested in app/src/test); the board UI is in CircuitBoard.kt.

/** A part on the board. [label] is its short name (SW1, Q2, AND1…). [fixed] parts can't be removed (Hard mode terminals). */
data class PlacedPart(val id: String, val label: String, val switchOn: Boolean = false, val fixed: Boolean = false) {
    val kind: PartKind? get() = PartKind.forId(id)
}

/** The wire being drawn: started at an output pin, waiting for a target part (and maybe a target pin). */
data class PendingWire(val fromCell: Int, val fromPin: Int, val targetCell: Int? = null)

/** What a tap on a part does. Hard mode shows these as a tool bar; the Sandbox stays in PLACE. */
enum class BoardTool(val label: String) {
    /** Tap an empty cell to drop the picked part; tap a part to select it; tap a switch to flip it. */
    PLACE("Place"),
    /** Tap a part to start a wire from it, then tap the part to connect to. */
    WIRE("Wire"),
    /** Tap a part to read its pins (switches still flip). */
    PROBE("Probe"),
    /** Tap a part to remove it and its wires. Fixed parts stay. */
    ERASE("Erase"),
}

data class EditorState(
    val cols: Int,
    val rows: Int,
    val mode: BoardTool = BoardTool.PLACE,
    /** Palette selection: the part the next tap on an empty cell places. */
    val tool: String? = null,
    val placed: Map<Int, PlacedPart> = emptyMap(),
    val wires: List<CircuitWire> = emptyList(),
    val focused: Int? = null,
    val pending: PendingWire? = null,
    val counters: Map<String, Int> = emptyMap(),
    /** Last simulated outputs, fed back in so latches keep their state between edits. */
    val outputs: Map<Int, BooleanArray> = emptyMap(),
    val sim: Circuit.Result? = null,
    val message: String? = null,
) {
    fun cellName(i: Int) = "${'A' + i % cols}${i / cols + 1}"
    fun count(id: String) = placed.values.count { it.id == id }
}

private fun shortName(id: String) = when (id) { "SWITCH" -> "SW"; "TRANSISTOR" -> "Q"; "POWER" -> "IN"; else -> id }

object Editor {
    /** Runs the circuit (only parts that can be wired take part) and stores the settled signals. */
    fun simulate(s: EditorState): EditorState {
        val parts = s.placed.mapNotNull { (cell, p) -> p.kind?.let { CircuitPart(cell, it, p.switchOn) } }
        val result = Circuit(parts, s.wires).simulate(s.outputs)
        return s.copy(sim = result, outputs = result.outputs)
    }

    /** Picking a part in the palette also switches to PLACE, since that's the only tool that uses it. */
    fun pickTool(s: EditorState, id: String?) =
        s.copy(tool = id, mode = if (id != null) BoardTool.PLACE else s.mode, focused = null, pending = null, message = null)

    fun setMode(s: EditorState, mode: BoardTool) = s.copy(
        mode = mode, focused = null, pending = null,
        tool = if (mode == BoardTool.PLACE) s.tool else null,
        message = null,
    )

    /** [canPlace] lets Hard mode limit parts (e.g. only two transistors). It returns an error message, or null if OK. */
    fun tapCell(s: EditorState, cell: Int, canPlace: (String) -> String? = { null }): EditorState {
        val here = s.placed[cell]
        val pending = s.pending
        // Finishing a wire works the same whatever the tool.
        if (pending != null) {
            if (here == null) return s.copy(message = "Tap a part, not an empty cell.")
            val inputs = here.kind?.inputs.orEmpty()
            return when {
                inputs.isEmpty() -> s.copy(message = "${here.label} has no inputs. Tap another part.")
                inputs.size == 1 -> connect(s, pending, cell, 0)
                else -> s.copy(pending = pending.copy(targetCell = cell), message = null)
            }
        }
        if (here == null) return place(s, cell, canPlace)
        return when (s.mode) {
            BoardTool.PLACE -> if (here.id == "SWITCH") flip(s, cell, here).copy(focused = cell)
                               else s.copy(focused = if (s.focused == cell) null else cell, message = null)
            BoardTool.WIRE -> {
                val outs = here.kind?.outputs.orEmpty()
                when {
                    outs.isEmpty() -> s.copy(message = "${here.label} has no outputs. Start the wire from a part that drives something.")
                    outs.size == 1 -> s.copy(pending = PendingWire(cell, 0), focused = null, message = null)
                    else -> s.copy(focused = cell, message = "Which output of ${here.label}?")
                }
            }
            BoardTool.PROBE -> {
                // Re-simulate after a flip so the reading shows the new value, not the old one.
                val after = if (here.id == "SWITCH") simulate(flip(s, cell, here)) else s
                after.copy(focused = cell, message = probe(after, cell))
            }
            BoardTool.ERASE ->
                if (here.fixed) s.copy(message = "${here.label} is part of this level and can't be removed.")
                else removeAt(s, cell)
        }
    }

    private fun place(s: EditorState, cell: Int, canPlace: (String) -> String?): EditorState {
        val tool = s.tool ?: return s.copy(focused = null)
        if (s.mode != BoardTool.PLACE) return s
        val error = canPlace(tool)
        if (error != null) return s.copy(message = error, tool = null)
        val n = (s.counters[tool] ?: 0) + 1
        return s.copy(
            placed = s.placed + (cell to PlacedPart(tool, "${shortName(tool)}$n")),
            counters = s.counters + (tool to n), focused = null, message = null,
        )
    }

    /** Drag and drop from the palette: put [id] on [cell], whatever the current tool. */
    fun placeAt(s: EditorState, cell: Int, id: String, canPlace: (String) -> String? = { null }): EditorState {
        if (cell in s.placed) return s.copy(message = "That spot is taken. Drop it on an empty cell.")
        val error = canPlace(id)
        if (error != null) return s.copy(message = error)
        val n = (s.counters[id] ?: 0) + 1
        val label = "${shortName(id)}$n"
        return s.copy(
            placed = s.placed + (cell to PlacedPart(id, label)), counters = s.counters + (id to n),
            focused = null, pending = null, message = "Placed $label.",
        )
    }

    /**
     * Drag and drop on the board: move the part at [from] to [to]. Its wires follow, because they refer to the cell.
     * Fixed parts don't move; occupied cells are refused.
     */
    fun move(s: EditorState, from: Int, to: Int): EditorState {
        if (from == to) return s
        val p = s.placed[from] ?: return s
        if (p.fixed) return s.copy(message = "${p.label} is part of this level and stays where it is.")
        if (to in s.placed) return s.copy(message = "That spot is taken. Drop ${p.label} on an empty cell.")
        fun remap(c: Int) = if (c == from) to else c
        return s.copy(
            placed = s.placed - from + (to to p),
            wires = s.wires.map { it.copy(fromPart = remap(it.fromPart), toPart = remap(it.toPart)) },
            outputs = s.outputs.mapKeys { remap(it.key) },
            focused = null, pending = null, message = "Moved ${p.label} to ${s.cellName(to)}.",
        )
    }

    /** Drag a part off the board to remove it (fixed parts stay). */
    fun dropOff(s: EditorState, from: Int): EditorState {
        val p = s.placed[from] ?: return s
        if (p.fixed) return s.copy(message = "${p.label} is part of this level and can't be removed.")
        return removeAt(s, from)
    }

    private fun flip(s: EditorState, cell: Int, p: PlacedPart) =
        s.copy(placed = s.placed + (cell to p.copy(switchOn = !p.switchOn)), message = null)

    /** "Q1 · B 1 · C 1 · E 1": every pin of the part with its current value (after the last simulation). */
    fun probe(s: EditorState, cell: Int): String {
        val p = s.placed[cell] ?: return ""
        val kind = p.kind ?: return "${p.label} can't be probed yet."
        val sim = s.sim
        fun v(b: Boolean?) = if (b == true) "1" else "0"
        val ins = kind.inputs.mapIndexed { i, n -> "$n ${v(sim?.input(cell, i))}" }
        val outs = kind.outputs.mapIndexed { i, n -> "$n ${v(sim?.output(cell, i))}" }
        return (listOf(p.label) + ins + outs).joinToString(" · ")
    }

    fun startWire(s: EditorState, fromPin: Int): EditorState {
        val cell = s.focused ?: return s
        return s.copy(pending = PendingWire(cell, fromPin), tool = null, message = null)
    }

    fun chooseTargetPin(s: EditorState, pin: Int): EditorState {
        val p = s.pending ?: return s
        val target = p.targetCell ?: return s
        return connect(s, p, target, pin)
    }

    private fun connect(s: EditorState, p: PendingWire, toCell: Int, toPin: Int): EditorState {
        val wire = CircuitWire(p.fromCell, p.fromPin, toCell, toPin)
        if (wire in s.wires) return s.copy(pending = null, message = "Those pins are already connected.")
        val joined = s.wires.any { it.toPart == toCell && it.toPin == toPin }
        val done = "Wired ${pinLabel(s, p.fromCell, p.fromPin)} → ${inputLabel(s, toCell, toPin)}."
        return s.copy(
            wires = s.wires + wire, pending = null, focused = null,
            message = if (joined) "$done Two wires into one input are joined as OR." else done,
        )
    }

    fun cancelWire(s: EditorState) = s.copy(pending = null, message = null)

    fun unwireFocused(s: EditorState): EditorState {
        val c = s.focused ?: return s
        return s.copy(wires = s.wires.filterNot { it.fromPart == c || it.toPart == c }, message = null)
    }

    fun removeFocused(s: EditorState): EditorState {
        val c = s.focused ?: return s
        if (s.placed[c]?.fixed == true) return s.copy(message = "${s.placed[c]?.label} is part of this level and can't be removed.")
        return removeAt(s, c)
    }

    private fun removeAt(s: EditorState, c: Int): EditorState {
        val label = s.placed[c]?.label
        return s.copy(
            placed = s.placed - c, wires = s.wires.filterNot { it.fromPart == c || it.toPart == c },
            outputs = s.outputs - c, focused = null, message = label?.let { "Removed $it and its wires." },
        )
    }

    /** Clears everything the player added; fixed parts stay. */
    fun clear(s: EditorState): EditorState {
        val kept = s.placed.filterValues { it.fixed }
        return EditorState(cols = s.cols, rows = s.rows, mode = s.mode, tool = s.tool, placed = kept)
    }

    fun pinLabel(s: EditorState, cell: Int, pin: Int): String {
        val p = s.placed[cell] ?: return "?"
        val outs = p.kind?.outputs.orEmpty()
        return if (outs.size <= 1) p.label else "${p.label}.${outs[pin]}"
    }

    fun inputLabel(s: EditorState, cell: Int, pin: Int): String {
        val p = s.placed[cell] ?: return "?"
        val ins = p.kind?.inputs.orEmpty()
        return if (ins.size <= 1) p.label else "${p.label}.${ins[pin]}"
    }
}

/** Callbacks the action bar needs; screens wire them to their ViewModel. */
class EditorActions(
    val startWire: (Int) -> Unit,
    val chooseTargetPin: (Int) -> Unit,
    val cancelWire: () -> Unit,
    val unwire: () -> Unit,
    val remove: () -> Unit,
    val donePlacing: () -> Unit,
)
