package app.bitbybit.ui.circuit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.bitbybit.sim.Circuit
import app.bitbybit.sim.CircuitPart
import app.bitbybit.sim.CircuitWire
import app.bitbybit.sim.PartKind
import app.bitbybit.ui.palette.ChipGlyph
import app.bitbybit.ui.palette.PaletteItem
import app.bitbybit.ui.theme.Bb
import app.bitbybit.ui.theme.BbFonts
import app.bitbybit.ui.theme.BbType

// Shared circuit editor: used by the Sandbox (free build) and by Hard mode workbenches.
// State + pure edit functions here; screens own a ViewModel that holds an EditorState and calls these.

/** A part on the board. [label] is its short name (SW1, Q2, AND1…). [fixed] parts can't be removed (Hard mode terminals). */
data class PlacedPart(val id: String, val label: String, val switchOn: Boolean = false, val fixed: Boolean = false) {
    val kind: PartKind? get() = PartKind.forId(id)
}

/** The wire being drawn: started at an output pin, waiting for a target part (and maybe a target pin). */
data class PendingWire(val fromCell: Int, val fromPin: Int, val targetCell: Int? = null)

data class EditorState(
    val cols: Int,
    val rows: Int,
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

private fun shortName(id: String) = when (id) { "SWITCH" -> "SW"; "TRANSISTOR" -> "Q"; else -> id }

object Editor {
    /** Runs the circuit (only parts that can be wired take part) and stores the settled signals. */
    fun simulate(s: EditorState): EditorState {
        val parts = s.placed.mapNotNull { (cell, p) -> p.kind?.let { CircuitPart(cell, it, p.switchOn) } }
        val result = Circuit(parts, s.wires).simulate(s.outputs)
        return s.copy(sim = result, outputs = result.outputs)
    }

    fun pickTool(s: EditorState, id: String?) = s.copy(tool = id, focused = null, pending = null, message = null)

    /** [canPlace] lets Hard mode limit parts (e.g. only two transistors). It returns an error message, or null if OK. */
    fun tapCell(s: EditorState, cell: Int, canPlace: (String) -> String? = { null }): EditorState {
        val here = s.placed[cell]
        val pending = s.pending
        return when {
            pending != null && here != null -> {
                val inputs = here.kind?.inputs.orEmpty()
                when {
                    inputs.isEmpty() -> s.copy(message = "${here.label} has no inputs. Tap another part.")
                    inputs.size == 1 -> connect(s, pending, cell, 0)
                    else -> s.copy(pending = pending.copy(targetCell = cell), message = null)
                }
            }
            pending != null -> s.copy(message = "Tap a part, not an empty cell.")
            here != null && here.id == "SWITCH" ->
                s.copy(placed = s.placed + (cell to here.copy(switchOn = !here.switchOn)), focused = cell, message = null)
            here != null -> s.copy(focused = if (s.focused == cell) null else cell, message = null)
            s.tool != null -> {
                val error = canPlace(s.tool)
                if (error != null) return s.copy(message = error, tool = null)
                val n = (s.counters[s.tool] ?: 0) + 1
                s.copy(
                    placed = s.placed + (cell to PlacedPart(s.tool, "${shortName(s.tool)}$n")),
                    counters = s.counters + (s.tool to n), focused = null, message = null,
                )
            }
            else -> s
        }
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
        return s.copy(
            placed = s.placed - c, wires = s.wires.filterNot { it.fromPart == c || it.toPart == c },
            outputs = s.outputs - c, focused = null, message = null,
        )
    }

    /** Clears everything the player added; fixed parts stay. */
    fun clear(s: EditorState): EditorState {
        val kept = s.placed.filterValues { it.fixed }
        return EditorState(cols = s.cols, rows = s.rows, tool = s.tool, placed = kept)
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

private val GAP = 6.dp
private val PAD = 8.dp

/** Grid of cells with wires and pins drawn on top. Pins: inputs on the left edge, outputs on the right. */
@Composable
fun CircuitBoard(s: EditorState, byId: Map<String, PaletteItem>, onTap: (Int) -> Unit, modifier: Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Bb.SurfaceSunken)
            .border(1.dp, Bb.BorderSubtle, RoundedCornerShape(18.dp)),
    ) {
        val cols = s.cols; val rows = s.rows
        val cellW: Dp = (maxWidth - PAD * 2 - GAP * (cols - 1)) / cols
        val cellH: Dp = (maxHeight - PAD * 2 - GAP * (rows - 1)) / rows
        fun x(c: Int) = PAD + (cellW + GAP) * c
        fun y(r: Int) = PAD + (cellH + GAP) * r
        val sim = s.sim
        val pending = s.pending

        for (i in 0 until cols * rows) {
            val part = s.placed[i]
            val item = part?.let { byId[it.id] }
            val focused = s.focused == i || pending?.fromCell == i || pending?.targetCell == i
            val canDrop = part == null && s.tool != null && pending == null
            val canTarget = pending != null && part != null && part.kind?.inputs?.isNotEmpty() == true
            Box(
                Modifier.offset(x(i % cols), y(i / cols)).size(cellW, cellH)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (focused) Bb.AccentBg else Bb.Bg)
                    .border(
                        if (focused || canDrop || canTarget) 1.5.dp else 1.dp,
                        when { focused -> Bb.Accent; canTarget -> Bb.High; canDrop -> Bb.AccentBorder; else -> Bb.BorderSubtle },
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = part != null || s.tool != null || pending != null, role = Role.Button) { onTap(i) }
                    .semantics { contentDescription = cellDescription(s, i, part, item) },
                contentAlignment = Alignment.Center,
            ) {
                if (part != null && item != null) PartFace(part, item, i, sim)
                else Box(Modifier.size(4.dp).clip(CircleShape).background(Bb.BorderSubtle))
            }
        }

        val density = LocalDensity.current
        Canvas(Modifier.fillMaxSize()) {
            fun pinPos(cell: Int, pin: Int, count: Int, output: Boolean): Offset = with(density) {
                val left = x(cell % cols).toPx()
                val top = y(cell / cols).toPx()
                Offset(if (output) left + cellW.toPx() else left, top + cellH.toPx() * (pin + 1) / (count + 1))
            }
            for (w in s.wires) {
                val from = s.placed[w.fromPart]?.kind ?: continue
                val to = s.placed[w.toPart]?.kind ?: continue
                val a = pinPos(w.fromPart, w.fromPin, from.outputs.size, output = true)
                val b = pinPos(w.toPart, w.toPin, to.inputs.size, output = false)
                val on = sim?.output(w.fromPart, w.fromPin) == true
                val bend = maxOf(40.dp.toPx(), kotlin.math.abs(b.x - a.x) / 2)
                val path = Path().apply { moveTo(a.x, a.y); cubicTo(a.x + bend, a.y, b.x - bend, b.y, b.x, b.y) }
                drawPath(path, if (on) Bb.High else Bb.Low, style = Stroke(3.dp.toPx()))
            }
            for ((cell, part) in s.placed) {
                val kind = part.kind ?: continue
                kind.inputs.indices.forEach { pin ->
                    drawCircle(if (sim?.input(cell, pin) == true) Bb.High else Bb.Low, 4.dp.toPx(), pinPos(cell, pin, kind.inputs.size, false))
                }
                kind.outputs.indices.forEach { pin ->
                    val starting = pending?.fromCell == cell && pending.fromPin == pin
                    val on = sim?.output(cell, pin) == true
                    drawCircle(if (starting) Bb.Accent else if (on) Bb.High else Bb.Low, (if (starting) 6 else 4).dp.toPx(), pinPos(cell, pin, kind.outputs.size, true))
                }
            }
        }
    }
}

@Composable
private fun PartFace(part: PlacedPart, item: PaletteItem, cell: Int, sim: Circuit.Result?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (part.id) {
            "SWITCH" -> Box(
                Modifier.size(34.dp, 26.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (part.switchOn) Bb.HighBg else Bb.Surface)
                    .border(2.dp, if (part.switchOn) Bb.High else Bb.Border, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(if (part.switchOn) "1" else "0", fontFamily = BbFonts.Mono, fontWeight = FontWeight.Bold, color = Bb.Text) }
            "LED" -> {
                val lit = sim?.input(cell, 0) == true
                Box(Modifier.size(24.dp).clip(CircleShape).background(if (lit) Bb.High else Bb.LedOff).border(2.dp, Bb.PinOutline, CircleShape))
            }
            else -> ChipGlyph(item.symbol, lit = part.kind != null, Modifier.padding(horizontal = 4.dp), compact = true)
        }
        Text(
            part.label, fontFamily = BbFonts.Mono, fontSize = 10.sp, maxLines = 1,
            color = if (part.fixed) Bb.Text else Bb.TextMuted, fontWeight = if (part.fixed) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

private fun cellDescription(s: EditorState, i: Int, part: PlacedPart?, item: PaletteItem?): String {
    if (part == null) return "Cell ${s.cellName(i)}: empty"
    val state = when (part.id) {
        "SWITCH" -> if (part.switchOn) ", on" else ", off"
        "LED" -> if (s.sim?.input(i, 0) == true) ", lit" else ", dark"
        else -> ""
    }
    return "Cell ${s.cellName(i)}: ${part.label}, ${item?.name ?: part.id}$state"
}

/** Context actions under the board. Buttons scroll sideways if they don't fit. */
@Composable
fun EditorActionBar(s: EditorState, actions: EditorActions, emptyHint: String, idleHint: String) {
    val focused = s.focused?.let { s.placed[it] }
    val pending = s.pending
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val (line, color) = when {
            pending?.targetCell != null ->
                "Connect ${Editor.pinLabel(s, pending.fromCell, pending.fromPin)} to which input of ${s.placed[pending.targetCell]?.label}?" to Bb.Accent
            pending != null -> "Wiring from ${Editor.pinLabel(s, pending.fromCell, pending.fromPin)}: tap the part to connect to" to Bb.Accent
            s.message != null -> s.message to Bb.TextSecondary
            focused != null && focused.kind == null -> "${focused.label} can't be wired yet." to Bb.TextMuted
            focused != null -> ("${focused.label} selected" + (if (focused.id == "SWITCH") " · tap it to flip" else "")) to Bb.Text
            s.tool != null -> "Placing ${s.tool.lowercase().replaceFirstChar { it.uppercase() }}: tap an empty cell" to Bb.Accent
            s.placed.values.none { !it.fixed } -> emptyHint to Bb.TextMuted
            else -> idleHint to Bb.TextMuted
        }
        Text(line, style = BbType.Body.copy(color = color), maxLines = 2)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                pending?.targetCell != null -> {
                    s.placed[pending.targetCell]?.kind?.inputs?.forEachIndexed { pin, name ->
                        SmallAction("Input $name", accent = true) { actions.chooseTargetPin(pin) }
                    }
                    SmallAction("Cancel", onClick = actions.cancelWire)
                }
                pending != null -> SmallAction("Cancel wire", onClick = actions.cancelWire)
                focused != null -> {
                    focused.kind?.outputs?.let { outs ->
                        outs.forEachIndexed { pin, name ->
                            SmallAction(if (outs.size == 1) "Start wire" else "Wire from $name", accent = true) { actions.startWire(pin) }
                        }
                    }
                    if (s.wires.any { it.fromPart == s.focused || it.toPart == s.focused }) SmallAction("Unwire", onClick = actions.unwire)
                    if (!focused.fixed) SmallAction("Remove", danger = true, onClick = actions.remove)
                }
                s.tool != null -> SmallAction("Done placing", onClick = actions.donePlacing)
            }
        }
    }
}

@Composable
fun SmallAction(text: String, danger: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    val (bg, border, fg) = when {
        danger -> Triple(Bb.ErrorBg, Bb.ErrorBorder, Bb.ErrorText)
        accent -> Triple(Bb.AccentBg, Bb.Accent, Bb.Accent)
        else -> Triple(Bb.Surface, Bb.Border, Bb.Text)
    }
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
}
